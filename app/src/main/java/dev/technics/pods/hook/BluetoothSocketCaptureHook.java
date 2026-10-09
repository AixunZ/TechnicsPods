package dev.technics.pods.hook;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class BluetoothSocketCaptureHook {
    interface EventSink {
        void emit(String direction, String operation, byte[] payload, String note);
    }

    private static final String TAG = "TechnicsPods-Socket";

    private static final Set<String> SOCKET_FACTORY_METHODS = Set.of(
            "createRfcommSocketToServiceRecord",
            "createInsecureRfcommSocketToServiceRecord",
            "createRfcommSocket",
            "createInsecureRfcommSocket",
            "createL2capChannel",
            "createInsecureL2capChannel"
    );

    private final AtomicBoolean socketHooksInstalled = new AtomicBoolean();
    private final XposedModule module;
    private final EventSink sink;
    private final Set<Class<?>> hookedInputClasses = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );
    private final Set<Class<?>> hookedOutputClasses = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );

    BluetoothSocketCaptureHook(XposedModule module, EventSink sink) {
        this.module = module;
        this.sink = sink;
    }

    void install() {
        installDeviceFactoryHooks();
        installSocketHooks();
        installKnownStreamClass("android.bluetooth.BluetoothInputStream", "RX");
        installKnownStreamClass("android.bluetooth.BluetoothOutputStream", "TX");
    }

    private void installDeviceFactoryHooks() {
        for (Method method : BluetoothDevice.class.getDeclaredMethods()) {
            if (!SOCKET_FACTORY_METHODS.contains(method.getName())) continue;
            installHook(method, "socket-factory:" + signature(method), chain -> {
                Object result = chain.proceed();
                sink.emit(
                        "META",
                        method.getName(),
                        null,
                        signature(method) + safeScalarArguments(chain.getArgs())
                );
                if (result instanceof BluetoothSocket) {
                    installSocketHooks();
                }
                return result;
            });
        }
    }

    private void installSocketHooks() {
        if (!socketHooksInstalled.compareAndSet(false, true)) return;
        for (Method method : BluetoothSocket.class.getDeclaredMethods()) {
            switch (method.getName()) {
                case "connect", "close" -> installHook(
                        method,
                        "socket-lifecycle:" + signature(method),
                        chain -> {
                            Object result = chain.proceed();
                            sink.emit("META", "socket-" + method.getName(), null, signature(method));
                            return result;
                        }
                );
                case "getInputStream" -> installHook(
                        method,
                        "socket-input:" + signature(method),
                        chain -> {
                            Object result = chain.proceed();
                            if (result != null) installStreamClass(result.getClass(), "RX");
                            return result;
                        }
                );
                case "getOutputStream" -> installHook(
                        method,
                        "socket-output:" + signature(method),
                        chain -> {
                            Object result = chain.proceed();
                            if (result != null) installStreamClass(result.getClass(), "TX");
                            return result;
                        }
                );
                default -> {
                }
            }
        }
    }

    private void installKnownStreamClass(String className, String direction) {
        try {
            installStreamClass(Class.forName(className, false, BluetoothSocket.class.getClassLoader()), direction);
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Stream class unavailable: " + className, error);
        }
    }

    private void installStreamClass(Class<?> streamClass, String direction) {
        Set<Class<?>> installed = "RX".equals(direction) ? hookedInputClasses : hookedOutputClasses;
        if (!installed.add(streamClass)) return;

        int installedCount = 0;
        for (Class<?> current = streamClass;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || !isTargetStreamMethod(method, direction)) continue;
                installStreamMethod(method, direction);
                installedCount++;
            }
        }
        module.log(
                Log.INFO,
                TAG,
                "Installed " + installedCount + " " + direction + " stream hooks for " + streamClass.getName()
        );
    }

    private boolean isTargetStreamMethod(Method method, String direction) {
        Class<?>[] parameters = method.getParameterTypes();
        if ("RX".equals(direction) && "read".equals(method.getName())) {
            return parameters.length == 0
                    || (parameters.length == 3
                    && parameters[0] == byte[].class
                    && parameters[1] == int.class
                    && parameters[2] == int.class);
        }
        if ("TX".equals(direction) && "write".equals(method.getName())) {
            return (parameters.length == 1 && parameters[0] == int.class)
                    || (parameters.length == 3
                    && parameters[0] == byte[].class
                    && parameters[1] == int.class
                    && parameters[2] == int.class);
        }
        return false;
    }

    private void installStreamMethod(Method method, String direction) {
        installHook(method, "socket-stream:" + direction + ":" + signature(method), chain -> {
            if ("RX".equals(direction)) {
                Object result = chain.proceed();
                byte[] payload = readPayload(method, chain.getArgs(), result);
                if (payload != null && payload.length > 0) {
                    sink.emit("RX", "socket-read", payload, signature(method));
                }
                return result;
            }

            byte[] payload = writePayload(method, chain.getArgs());
            Object result = chain.proceed();
            if (payload != null && payload.length > 0) {
                sink.emit("TX", "socket-write", payload, signature(method));
            }
            return result;
        });
    }

    private byte[] readPayload(Method method, List<Object> arguments, Object result) {
        if (!(result instanceof Integer count) || count < 0) return null;
        if (method.getParameterCount() == 0) {
            return new byte[]{(byte) (count & 0xFF)};
        }
        return sliceBuffer(arguments, count);
    }

    private byte[] writePayload(Method method, List<Object> arguments) {
        if (method.getParameterCount() == 1 && !arguments.isEmpty() && arguments.get(0) instanceof Integer value) {
            return new byte[]{(byte) (value & 0xFF)};
        }
        int requested = arguments.size() >= 3 && arguments.get(2) instanceof Integer value ? value : -1;
        return sliceBuffer(arguments, requested);
    }

    private byte[] sliceBuffer(List<Object> arguments, int actualLength) {
        if (arguments.isEmpty() || !(arguments.get(0) instanceof byte[] buffer)) return null;
        int offset = arguments.size() >= 2 && arguments.get(1) instanceof Integer value ? value : 0;
        int requested = arguments.size() >= 3 && arguments.get(2) instanceof Integer value
                ? value
                : buffer.length - offset;
        if (offset < 0 || requested < 0 || offset > buffer.length) return null;
        int available = Math.min(requested, buffer.length - offset);
        int length = actualLength < 0 ? available : Math.min(actualLength, available);
        if (length <= 0) return null;
        return java.util.Arrays.copyOfRange(buffer, offset, offset + length);
    }

    private String safeScalarArguments(List<Object> arguments) {
        List<String> values = new ArrayList<>();
        for (Object argument : arguments) {
            if (argument instanceof UUID value) values.add("uuid=" + value);
            else if (argument instanceof Integer value) values.add("int=" + value);
            else if (argument instanceof Boolean value) values.add("bool=" + value);
        }
        return ";args=" + values;
    }

    private void installHook(Method method, String idSuffix, XposedInterface.Hooker hooker) {
        try {
            method.setAccessible(true);
            module.hook(method)
                    .setId("technicspods.capture.v2:" + idSuffix)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Skipped hook " + method, error);
        }
    }

    private static String signature(Method method) {
        StringBuilder output = new StringBuilder(method.getDeclaringClass().getName())
                .append('#')
                .append(method.getName())
                .append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) output.append(',');
            output.append(parameters[index].getName());
        }
        return output.append(')').toString();
    }
}
