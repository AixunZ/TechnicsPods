package dev.technics.pods.diagnostic;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import dev.technics.pods.ModulePreferences;

/** Binds transport selection to a paired device; a saved address survives renaming. */
public final class Az100DeviceIdentity {
    private Az100DeviceIdentity() {}

    public static BluetoothDevice selectedDevice(Context context) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) throw new SecurityException("缺少蓝牙连接权限");
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        String selected = ModulePreferences.get(context)
                .getString(ModulePreferences.SELECTED_ADDRESS, "");
        BluetoothDevice matched = null;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (selected != null && !selected.isEmpty()) {
                if (selected.equalsIgnoreCase(device.getAddress())) return device;
            } else if (Az100DeviceMatcher.matchesName(device.getName())) {
                if (matched != null) throw new IllegalStateException("多个 AZ100 已配对，请先选择设备");
                matched = device;
            }
        }
        return matched;
    }

    public static boolean isConnected(Context context) {
        try {
            BluetoothDevice device = selectedDevice(context);
            if (device == null) return false;
            Method method = BluetoothDevice.class.getDeclaredMethod("isConnected");
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(device));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    public static boolean matches(Context context, BluetoothDevice device) {
        if (device == null) return false;
        try {
            BluetoothDevice selected = selectedDevice(context);
            return selected != null && selected.getAddress().equalsIgnoreCase(device.getAddress());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
