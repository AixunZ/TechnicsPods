package dev.technics.pods.hook;

import android.app.Application;
import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.technics.pods.capture.CaptureContract;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Read-only inventory of the device-specific Bluetooth service surface. */
final class BluetoothEngineProbeHook {
    private static final String TAG = "TechnicsPods-BluetoothProbe";
    private static final String[] CANDIDATE_CLASSES = {
            "com.android.bluetooth.btservice.AdapterService",
            "com.android.bluetooth.btservice.RemoteDevices",
            "com.android.bluetooth.btservice.DeviceProperties",
            "com.android.bluetooth.btservice.RemoteDevices$DeviceProperties",
            "com.android.bluetooth.btservice.RemoteDevices$DevicePropertiesMap",
            "com.android.bluetooth.gatt.GattService",
            "com.android.bluetooth.battery.BatteryService",
            "com.android.bluetooth.a2dp.A2dpService",
            "com.android.bluetooth.le_audio.LeAudioService",
            "com.android.bluetooth.bass.BassClientService"
    };

    private final XposedModule module;
    private final CaptureConfig config;
    private final ClassLoader classLoader;
    private final AtomicBoolean emitted = new AtomicBoolean();
    private final AtomicBoolean propertyProbeInstalled = new AtomicBoolean();

    BluetoothEngineProbeHook(XposedModule module, CaptureConfig config, ClassLoader classLoader) {
        this.module = module;
        this.config = config;
        this.classLoader = classLoader;
    }

    void install() throws ReflectiveOperationException {
        module.log(Log.INFO, TAG, "Installing system Bluetooth probe");
        emitFromCurrentApplication();
        Method method = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate",
                Application.class
        );
        method.setAccessible(true);
        module.hook(method)
                .setId("technicspods.system-battery-probe:application-on-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Application application = (Application) chain.getArg(0);
                    emitFromApplication(application);
                    return result;
                });
    }

    private void emitFromCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread",
                    false,
                    classLoader
            );
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Application value) emitFromApplication(value);
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Application is not ready during package hook");
        }
    }

    private void emitFromApplication(Application application) {
        if (!emitted.compareAndSet(false, true)) return;
        Context context = application.getApplicationContext();
        emit(context, "system-engine-ready");
        emitInventory(context);
        installRemoteDevicePropertyProbe();
    }

    private void installRemoteDevicePropertyProbe() {
        if (!propertyProbeInstalled.compareAndSet(false, true)) return;
        try {
            Class<?> remoteDevices = Class.forName(
                    "com.android.bluetooth.btservice.RemoteDevices",
                    false,
                    classLoader
            );
            Method callback = remoteDevices.getDeclaredMethod(
                    "devicePropertyChangedCallback",
                    byte[].class,
                    int.class,
                    int[].class,
                    byte[][].class
            );
            callback.setAccessible(true);
            module.hook(callback)
                    .setId("technicspods.system-battery-probe:remote-device-properties")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        List<Object> arguments = chain.getArgs();
                        int[] properties = arguments.size() > 2 && arguments.get(2) instanceof int[] value
                                ? value
                                : null;
                        byte[][] values = arguments.size() > 3 && arguments.get(3) instanceof byte[][] value
                                ? value
                                : null;
                        StringBuilder note = new StringBuilder("property-callback;ids=");
                        if (properties == null) {
                            note.append("none");
                        } else {
                            for (int index = 0; index < properties.length; index++) {
                                if (index > 0) note.append(',');
                                note.append(properties[index]);
                            }
                        }
                        note.append(";valueLengths=");
                        if (values == null) {
                            note.append("none");
                        } else {
                            for (int index = 0; index < values.length; index++) {
                                if (index > 0) note.append(',');
                                note.append(values[index] == null ? -1 : values[index].length);
                            }
                        }
                        if (properties != null && values != null) {
                            note.append(";smallValues=");
                            for (int index = 0; index < Math.min(properties.length, values.length); index++) {
                                byte[] value = values[index];
                                if (!isSmallNumericProperty(properties[index], value)) continue;
                                note.append(properties[index]).append("=0x");
                                appendHex(note, value);
                                note.append(',');
                            }
                        }
                        module.log(Log.INFO, TAG, note.toString());
                        return result;
                    });
            module.log(Log.INFO, TAG, "Installed read-only RemoteDevices property probe");
            installBatteryParserProbes(remoteDevices);
            installDevicePropertiesBatteryProbes();
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install RemoteDevices property probe", error);
        }
    }

    private void installDevicePropertiesBatteryProbes() {
        try {
            Class<?> properties = Class.forName(
                    "com.android.bluetooth.btservice.RemoteDevices$DeviceProperties",
                    false,
                    classLoader
            );
            for (Method method : properties.getDeclaredMethods()) {
                if (!method.getName().toLowerCase(java.util.Locale.ROOT).contains("battery")) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId("technicspods.system-battery-probe:device-properties-" + method.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            StringBuilder note = new StringBuilder("device-properties;")
                                    .append(method.getName())
                                    .append(";result=")
                                    .append(result instanceof Number || result instanceof Boolean ? result : "none")
                                    .append(";args=");
                            for (Object argument : chain.getArgs()) {
                                if (argument instanceof Number || argument instanceof Boolean) {
                                    note.append(argument);
                                } else if (argument == null) {
                                    note.append("null");
                                } else {
                                    note.append(argument.getClass().getSimpleName());
                                }
                                note.append(',');
                            }
                            module.log(Log.INFO, TAG, note.toString());
                            return result;
                        });
                module.log(Log.INFO, TAG, "Installed read-only DeviceProperties battery probe: "
                        + signature(method));
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install DeviceProperties battery probe", error);
        }
    }

    private void installBatteryParserProbes(Class<?> remoteDevices) {
        for (Method method : remoteDevices.getDeclaredMethods()) {
            if (!method.getName().toLowerCase(java.util.Locale.ROOT).contains("getbatterylevel")) {
                continue;
            }
            try {
                method.setAccessible(true);
                module.hook(method)
                        .setId("technicspods.system-battery-probe:parser-" + method.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            StringBuilder note = new StringBuilder("battery-parser;")
                                    .append(method.getName())
                                    .append(";result=")
                                    .append(result instanceof Number ? result : "non-numeric")
                                    .append(";argShapes=");
                            for (Object argument : chain.getArgs()) {
                                if (argument instanceof android.bluetooth.BluetoothDevice) {
                                    note.append("device,");
                                } else if (argument instanceof Object[] values) {
                                    note.append("array[").append(values.length).append("]{");
                                    for (Object value : values) {
                                        appendShape(note, value);
                                        note.append(',');
                                    }
                                    note.append("},");
                                } else if (argument == null) {
                                    note.append("null,");
                                } else {
                                    appendShape(note, argument);
                                    note.append(',');
                                }
                            }
                            module.log(Log.INFO, TAG, note.toString());
                            return result;
                        });
                module.log(Log.INFO, TAG, "Installed read-only battery parser probe: " + signature(method));
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "Failed to install battery parser probe: " + method, error);
            }
        }
    }

    private static void appendShape(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof byte[] bytes) {
            output.append("byte[").append(bytes.length).append(']');
        } else if (value instanceof Number number) {
            output.append(value.getClass().getSimpleName()).append('(').append(number).append(')');
        } else if (value instanceof String string) {
            output.append("String[").append(string.length()).append(']');
        } else if (value instanceof android.bluetooth.BluetoothDevice) {
            output.append("device");
        } else {
            output.append(value.getClass().getSimpleName());
        }
    }

    private static boolean isSmallNumericProperty(int property, byte[] value) {
        return value != null
                && value.length > 0
                && value.length <= 4
                && (property == 11 || property == 17 || property == 21 || property == 22
                || property == 24 || property == 29 || property == 31 || property == 33
                || property == 240 || property == 249);
    }

    private static void appendHex(StringBuilder output, byte[] value) {
        for (byte item : value) {
            output.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        }
    }

    private void emitInventory(Context context) {
        for (String className : CANDIDATE_CLASSES) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                List<String> methods = new ArrayList<>();
                for (Class<?> current = type;
                     current != null && current != Object.class;
                     current = current.getSuperclass()) {
                    for (Method method : current.getDeclaredMethods()) {
                        if (isBatterySurface(method)) methods.add(signature(method));
                    }
                }
                String note = "class=" + className + ";methods=" + methods;
                String boundedNote = note.length() > 512 ? note.substring(0, 512) : note;
                // Keep the inventory visible when a system-process broadcast is not
                // delivered to the module receiver on a particular ROM.
                module.log(Log.INFO, TAG, "Inventory: " + boundedNote);
                emit(context, boundedNote);
            } catch (Throwable error) {
                module.log(Log.INFO, TAG, "Class unavailable: " + className);
            }
        }
    }

    private static boolean isBatterySurface(Method method) {
        if (Modifier.isAbstract(method.getModifiers())) return false;
        String name = method.getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("battery")
                || name.contains("metadata")
                || name.contains("property")
                || name.contains("device");
    }

    private void emit(Context context, String note) {
        Intent intent = new Intent(CaptureContract.ACTION_RECORD)
                .setComponent(new ComponentName(
                        CaptureContract.MODULE_PACKAGE,
                        CaptureContract.RECEIVER_CLASS
                ))
                .putExtra(CaptureContract.EXTRA_WALL_TIME, System.currentTimeMillis())
                .putExtra(CaptureContract.EXTRA_ELAPSED_NANOS, SystemClock.elapsedRealtimeNanos())
                .putExtra(CaptureContract.EXTRA_SESSION, config.session())
                .putExtra(CaptureContract.EXTRA_PROCESS, Application.getProcessName())
                .putExtra(CaptureContract.EXTRA_THREAD, Thread.currentThread().getName())
                .putExtra(CaptureContract.EXTRA_DIRECTION, "META")
                .putExtra(CaptureContract.EXTRA_OPERATION, "system-engine-probe")
                .putExtra(CaptureContract.EXTRA_SERVICE_UUID, "")
                .putExtra(CaptureContract.EXTRA_CHARACTERISTIC_UUID, "")
                .putExtra(CaptureContract.EXTRA_DESCRIPTOR_UUID, "")
                .putExtra(CaptureContract.EXTRA_STATUS, -1)
                .putExtra(CaptureContract.EXTRA_WRITE_TYPE, -1)
                .putExtra(CaptureContract.EXTRA_PAYLOAD, (byte[]) null)
                .putExtra(CaptureContract.EXTRA_NOTE, note);
        context.sendBroadcast(
                intent,
                null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        );
    }

    private static String signature(Method method) {
        StringBuilder output = new StringBuilder(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) output.append(',');
            output.append(parameters[index].getName());
        }
        return output.append(')').toString();
    }
}
