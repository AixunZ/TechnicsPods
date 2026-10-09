package dev.technics.pods.hook;

import android.app.Application;
import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.technics.pods.capture.CaptureContract;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class GattCaptureHook {
    private static final String TAG = "TechnicsPods-Gatt";

    private static final Set<String> OUTBOUND_METHODS = Set.of(
            "connect",
            "disconnect",
            "close",
            "discoverServices",
            "readCharacteristic",
            "writeCharacteristic",
            "readDescriptor",
            "writeDescriptor",
            "setCharacteristicNotification",
            "requestMtu",
            "requestConnectionPriority",
            "setPreferredPhy",
            "readPhy",
            "beginReliableWrite",
            "executeReliableWrite",
            "abortReliableWrite"
    );

    private static final Set<String> CALLBACK_METHODS = Set.of(
            "onConnectionStateChange",
            "onServicesDiscovered",
            "onCharacteristicRead",
            "onCharacteristicWrite",
            "onCharacteristicChanged",
            "onDescriptorRead",
            "onDescriptorWrite",
            "onReliableWriteCompleted",
            "onReadRemoteRssi",
            "onMtuChanged",
            "onPhyRead",
            "onPhyUpdate",
            "onServiceChanged",
            "onSubrateChange"
    );

    private final XposedModule module;
    private final CaptureConfig config;
    private final ClassLoader appClassLoader;
    private final Set<Class<?>> hookedCallbackClasses = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private volatile Context appContext;

    GattCaptureHook(XposedModule module, CaptureConfig config, ClassLoader appClassLoader) {
        this.module = module;
        this.config = config;
        this.appClassLoader = appClassLoader;
    }

    void install() throws ReflectiveOperationException {
        installApplicationHook();
        installConnectGattHooks();
        installBluetoothGattHooks();
        new BluetoothSocketCaptureHook(module, this::emitTransport).install();
    }

    private void installApplicationHook() throws NoSuchMethodException {
        Method method = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
        installHook(method, "application-on-create", chain -> {
            Application application = (Application) chain.getArg(0);
            appContext = application.getApplicationContext();
            Object result = chain.proceed();
            emitAlways("META", "application-on-create", null, null, -1, -1, null, "capture-hook-ready");
            return result;
        });
    }

    private void installConnectGattHooks() {
        for (Method method : BluetoothDevice.class.getDeclaredMethods()) {
            if (!"connectGatt".equals(method.getName())) continue;
            installHook(method, "connectGatt:" + signature(method), chain -> {
                for (Object argument : chain.getArgs()) {
                    if (argument != null && isGattCallback(argument.getClass())) {
                        installCallbackHooks(argument.getClass());
                    }
                }
                emit("META", "connectGatt", null, null, -1, -1, null, signature(method));
                return chain.proceed();
            });
        }
    }

    private void installBluetoothGattHooks() throws ClassNotFoundException {
        Class<?> bluetoothGatt = Class.forName("android.bluetooth.BluetoothGatt", false, appClassLoader);
        for (Method method : bluetoothGatt.getDeclaredMethods()) {
            if (!OUTBOUND_METHODS.contains(method.getName())) continue;
            installHook(method, "gatt-out:" + signature(method), chain -> {
                try {
                    EventParts parts = inspect(method.getName(), chain.getArgs());
                    emit(
                            "TX",
                            method.getName(),
                            parts.characteristic,
                            parts.descriptor,
                            parts.status,
                            parts.writeType,
                            parts.payload,
                            signature(method) + parts.scalarNote
                    );
                } catch (Throwable error) {
                    module.log(Log.WARN, TAG, "Could not encode outbound GATT event", error);
                }
                return chain.proceed();
            });
        }
    }

    private void installCallbackHooks(Class<?> callbackClass) {
        if (!hookedCallbackClasses.add(callbackClass)) return;
        int installedCount = 0;
        for (Class<?> current = callbackClass;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!CALLBACK_METHODS.contains(method.getName()) || Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                installHook(method, "gatt-callback:" + signature(method), chain -> {
                    try {
                        EventParts parts = inspect(method.getName(), chain.getArgs());
                        emit(
                                "RX",
                                method.getName(),
                                parts.characteristic,
                                parts.descriptor,
                                parts.status,
                                parts.writeType,
                                parts.payload,
                                signature(method) + parts.scalarNote
                        );
                    } catch (Throwable error) {
                        module.log(Log.WARN, TAG, "Could not encode GATT callback", error);
                    }
                    return chain.proceed();
                });
                installedCount++;
            }
        }
        module.log(Log.INFO, TAG, "Installed " + installedCount + " callback hooks for " + callbackClass.getName());
    }

    private void installHook(Method method, String idSuffix, XposedInterface.Hooker hooker) {
        try {
            method.setAccessible(true);
            module.hook(method)
                    .setId("technicspods.capture.v1:" + idSuffix)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Skipped hook " + method, error);
        }
    }

    private boolean isGattCallback(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if ("android.bluetooth.BluetoothGattCallback".equals(current.getName())) return true;
        }
        return false;
    }

    private EventParts inspect(String operation, List<Object> arguments) {
        BluetoothGattCharacteristic characteristic = null;
        BluetoothGattDescriptor descriptor = null;
        byte[] payload = null;
        List<Integer> integers = new ArrayList<>();
        List<Boolean> booleans = new ArrayList<>();

        for (Object argument : arguments) {
            if (argument instanceof BluetoothGattCharacteristic value) characteristic = value;
            else if (argument instanceof BluetoothGattDescriptor value) descriptor = value;
            else if (argument instanceof byte[] value) payload = value.clone();
            else if (argument instanceof Integer value) integers.add(value);
            else if (argument instanceof Boolean value) booleans.add(value);
        }
        if (characteristic == null && descriptor != null) characteristic = descriptor.getCharacteristic();
        if (payload == null && characteristic != null && operation.contains("Characteristic")) {
            byte[] value = characteristic.getValue();
            if (value != null) payload = value.clone();
        }
        if (payload == null && descriptor != null && operation.contains("Descriptor")) {
            byte[] value = descriptor.getValue();
            if (value != null) payload = value.clone();
        }

        int status = -1;
        if (operation.startsWith("on") && !integers.isEmpty()) {
            status = "onConnectionStateChange".equals(operation)
                    ? integers.get(0)
                    : integers.get(integers.size() - 1);
        }
        int writeType = "writeCharacteristic".equals(operation) && characteristic != null
                ? characteristic.getWriteType()
                : -1;
        if ("writeCharacteristic".equals(operation) && integers.size() == 1) {
            writeType = integers.get(0);
        }
        String scalarNote = ";ints=" + integers + ";bools=" + booleans;
        return new EventParts(characteristic, descriptor, payload, status, writeType, scalarNote);
    }

    private void emitTransport(String direction, String operation, byte[] payload, String note) {
        emit(direction, operation, null, null, -1, -1, payload, note);
    }

    private void emit(
            String direction,
            String operation,
            BluetoothGattCharacteristic characteristic,
            BluetoothGattDescriptor descriptor,
            int status,
            int writeType,
            byte[] payload,
            String note
    ) {
        emit(direction, operation, characteristic, descriptor, status, writeType, payload, note, false);
    }

    private void emitAlways(
            String direction,
            String operation,
            BluetoothGattCharacteristic characteristic,
            BluetoothGattDescriptor descriptor,
            int status,
            int writeType,
            byte[] payload,
            String note
    ) {
        emit(direction, operation, characteristic, descriptor, status, writeType, payload, note, true);
    }

    private void emit(
            String direction,
            String operation,
            BluetoothGattCharacteristic characteristic,
            BluetoothGattDescriptor descriptor,
            int status,
            int writeType,
            byte[] payload,
            String note,
            boolean force
    ) {
        Context context = appContext;
        if (context == null || (!force && !config.isEnabled())) return;
        UUID characteristicUuid = characteristic == null ? null : characteristic.getUuid();
        UUID serviceUuid = characteristic == null || characteristic.getService() == null
                ? null
                : characteristic.getService().getUuid();
        UUID descriptorUuid = descriptor == null ? null : descriptor.getUuid();

        byte[] boundedPayload = payload;
        String payloadNote = note;
        if (payload != null && payload.length > CaptureContract.MAX_PAYLOAD_BYTES) {
            boundedPayload = java.util.Arrays.copyOf(payload, CaptureContract.MAX_PAYLOAD_BYTES);
            payloadNote = note + ";payload-truncated-from=" + payload.length;
        }

        Intent intent = new Intent(CaptureContract.ACTION_RECORD)
                .setComponent(new ComponentName(CaptureContract.MODULE_PACKAGE, CaptureContract.RECEIVER_CLASS))
                .putExtra(CaptureContract.EXTRA_WALL_TIME, System.currentTimeMillis())
                .putExtra(CaptureContract.EXTRA_ELAPSED_NANOS, SystemClock.elapsedRealtimeNanos())
                .putExtra(CaptureContract.EXTRA_SESSION, config.session())
                .putExtra(CaptureContract.EXTRA_PROCESS, Application.getProcessName())
                .putExtra(CaptureContract.EXTRA_THREAD, Thread.currentThread().getName())
                .putExtra(CaptureContract.EXTRA_DIRECTION, direction)
                .putExtra(CaptureContract.EXTRA_OPERATION, operation)
                .putExtra(CaptureContract.EXTRA_SERVICE_UUID, text(serviceUuid))
                .putExtra(CaptureContract.EXTRA_CHARACTERISTIC_UUID, text(characteristicUuid))
                .putExtra(CaptureContract.EXTRA_DESCRIPTOR_UUID, text(descriptorUuid))
                .putExtra(CaptureContract.EXTRA_STATUS, status)
                .putExtra(CaptureContract.EXTRA_WRITE_TYPE, writeType)
                .putExtra(CaptureContract.EXTRA_PAYLOAD, boundedPayload)
                .putExtra(CaptureContract.EXTRA_NOTE, payloadNote);
        context.sendBroadcast(
                intent,
                null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        );
    }

    private static String text(UUID uuid) {
        return uuid == null ? "" : uuid.toString().toLowerCase(java.util.Locale.ROOT);
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

    private record EventParts(
            BluetoothGattCharacteristic characteristic,
            BluetoothGattDescriptor descriptor,
            byte[] payload,
            int status,
            int writeType,
            String scalarNote
    ) {}
}
