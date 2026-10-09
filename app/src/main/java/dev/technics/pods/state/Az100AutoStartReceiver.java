package dev.technics.pods.state;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.diagnostic.Az100DeviceIdentity;

/** Reconciles the selected device after system connection, boot and package update events. */
public final class Az100AutoStartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Az100ConnectionRuntime runtime = Az100ConnectionRuntime.get(context);
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            if (Az100DeviceIdentity.matches(context, device)) {
                runtime.connectionChanged(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action));
            }
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                runtime.bluetoothOff();
            } else if (state == BluetoothAdapter.STATE_ON) runtime.reconcileConnection();
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            runtime.reconcileConnection();
        }
    }

    public static void startIfEnabled(Context context) {
        if (!ModulePreferences.get(context).getBoolean(ModulePreferences.AUTO_MANAGE, false)) return;
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Az100ConnectionRuntime.get(context).startError("请授予蓝牙连接权限，再恢复自动管理");
            return;
        }
        try {
            context.startForegroundService(new Intent(context, Az100MonitorService.class)
                    .setAction(Az100MonitorService.ACTION_START));
            ModulePreferences.get(context).edit().remove(ModulePreferences.START_ERROR).apply();
        } catch (RuntimeException denied) {
            Az100ConnectionRuntime.get(context).startError("后台启动受系统限制，请打开 TechnicsPods 恢复自动管理");
        }
    }
}
