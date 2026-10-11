package dev.technics.pods

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.technics.pods.state.Az100ConnectionRuntime
import dev.technics.pods.state.Az100MonitorService
import dev.technics.pods.ui.TechnicsPodsAppUi
import dev.technics.pods.ui.TechnicsPodsViewModel
import dev.technics.pods.ui.UiAction
import kotlinx.coroutines.launch

/** Android permission and activity boundaries; pages share one state owner. */
class MainActivity : ComponentActivity() {
    private lateinit var model: TechnicsPodsViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Az100ConnectionRuntime.get(this).reconcileConnection()
        }
        when (model.consumePermissionAction()) {
            UiAction.EnableAutomation -> {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) enableAutomation() else toast(R.string.permission_automation_missing)
            }
            UiAction.ChooseDevice -> {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) model.openDevicePicker()
                else toast(R.string.permission_bluetooth_missing)
            }
            else -> Unit
        }
        model.refresh()
    }

    private val imageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.importImage(it)
    }

    private val manifestLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.importArtworkManifest(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[TechnicsPodsViewModel::class.java]
        lifecycle.addObserver(model)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }
        setContent { TechnicsPodsAppUi(model, ::performAction) }
    }

    private fun performAction(action: UiAction) {
        when (action) {
            UiAction.EnableAutomation -> requestOrRun(action) { enableAutomation() }
            UiAction.ChooseDevice -> requestOrRun(action) { model.openDevicePicker() }
            UiAction.RequestPermissions -> requestOrRun(action) { toast(R.string.permissions_ready) }
            UiAction.ImportImage -> imageLauncher.launch(arrayOf("image/*"))
            UiAction.ImportArtworkManifest -> manifestLauncher.launch(arrayOf("application/xml", "text/xml", "text/plain", "application/octet-stream"))
            UiAction.ImportOfficialImage -> model.importOfficialArtwork()
            UiAction.ResetImage -> model.resetArtwork()
            UiAction.OpenDiagnostics -> startActivity(Intent(this, DiagnosticActivity::class.java))
            UiAction.OpenAppSettings -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
            UiAction.OpenBatterySettings -> openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            UiAction.OpenBluetoothSettings -> openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            UiAction.OpenHuaweiReference -> openSettings(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nshpiter/HuaweiPods")))
            UiAction.OpenSonyReference -> openSettings(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mercury000/SonyPods")))
            UiAction.OpenLicense -> openSettings(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
        }
    }

    private fun requestOrRun(action: UiAction, ready: () -> Unit) {
        val required = if (action == UiAction.ChooseDevice) listOf(Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        val missing = required.filterNot(::hasPermission)
        if (missing.isEmpty()) ready() else {
            model.rememberPermissionAction(action)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun enableAutomation() {
        try {
            ModulePreferences.get(this).edit().putBoolean(ModulePreferences.AUTO_MANAGE, true)
                .remove(ModulePreferences.START_ERROR).apply()
            ModulePreferences.mirrorToFramework(this)
            startForegroundService(Intent(this, Az100MonitorService::class.java)
                .setAction(Az100MonitorService.ACTION_START))
        } catch (error: RuntimeException) {
            ModulePreferences.get(this).edit().putBoolean(ModulePreferences.AUTO_MANAGE, false)
                .putString(ModulePreferences.START_ERROR, error.message).apply()
            ModulePreferences.mirrorToFramework(this)
            toast(R.string.automation_start_failed)
        }
        model.refresh()
    }

    private fun hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun openSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: RuntimeException) { toast(R.string.settings_unavailable) }
    }

    private fun toast(resource: Int) = Toast.makeText(this, resource, Toast.LENGTH_SHORT).show()
}
