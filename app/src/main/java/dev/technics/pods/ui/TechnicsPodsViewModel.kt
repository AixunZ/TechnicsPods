package dev.technics.pods.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import dev.technics.pods.ModulePreferences
import dev.technics.pods.R
import dev.technics.pods.TechnicsPodsApp
import dev.technics.pods.art.Az100ArtProvider
import dev.technics.pods.art.Az100ArtImportResult
import dev.technics.pods.art.Az100ArtStore
import dev.technics.pods.diagnostic.Az100DeviceMatcher
import dev.technics.pods.state.Az100ConnectionRuntime
import dev.technics.pods.state.Az100MonitorService
import dev.technics.pods.state.Az100StateProvider
import dev.technics.pods.state.Az100StateRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.Executors

/** A single foreground observer and worker shared by all pages, including recreation. */
class TechnicsPodsViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application), DefaultLifecycleObserver {
    private val app = application
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val modulePreferences = ModulePreferences.get(app)
    private val uiPreferences = app.getSharedPreferences("technics_ui", Context.MODE_PRIVATE)
    private val capabilityPreferences = app.getSharedPreferences("hook_capabilities", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(TechnicsPodsUiState(
        selectedTab = savedState.get<Int>("tab")?.coerceIn(0, 2) ?: 0,
        appearance = appearanceFromPreference(),
        devicePickerOpen = savedState["device_picker"] ?: false,
    ))
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var started = false
    private var generation = 0L
    private var refreshRunning = false
    private var refreshPending = false
    private var cleared = false
    private var artworkVersion = Long.MIN_VALUE
    private var artworkAddress = ""
    private var artworkCache = ArtworkState()

    private val refreshAtExpiry = Runnable { refresh() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { refresh() }
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (started) return
        started = true
        generation++
        app.contentResolver.registerContentObserver(Az100StateProvider.STATE_URI, true, observer)
        app.contentResolver.registerContentObserver(Az100ArtProvider.ART_URI, true, observer)
        modulePreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        uiPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        capabilityPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        app.registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }, Context.RECEIVER_EXPORTED)
        ModulePreferences.mirrorToFramework(app)
        Az100ConnectionRuntime.get(app).reconcileConnection()
        refresh()
    }

    override fun onResume(owner: LifecycleOwner) = refresh()

    override fun onStop(owner: LifecycleOwner) {
        if (!started) return
        started = false
        generation++
        handler.removeCallbacks(refreshAtExpiry)
        app.contentResolver.unregisterContentObserver(observer)
        modulePreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        uiPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        capabilityPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        app.unregisterReceiver(bluetoothReceiver)
    }

    fun selectTab(tab: Int) {
        val selected = tab.coerceIn(0, 2)
        savedState["tab"] = selected
        mutableState.value = mutableState.value.copy(selectedTab = selected)
    }

    fun setAppearance(appearance: AppAppearance) {
        uiPreferences.edit().putString("appearance", appearance.name).apply()
        mutableState.value = mutableState.value.copy(appearance = appearance)
    }

    fun setArtworkColor(color: String?) {
        val value = color.orEmpty().lowercase().takeIf { it.matches(Regex("black|silver|blue|gold|violet")) }.orEmpty()
        modulePreferences.edit().putString(ModulePreferences.ART_COLOR_OVERRIDE, value).apply()
        artworkVersion = Long.MIN_VALUE
        refresh()
    }

    fun setBooleanPreference(key: String, enabled: Boolean) {
        if (key !in setOf(ModulePreferences.AUTO_MANAGE, ModulePreferences.STATUS_ICON, ModulePreferences.DIAGNOSTICS)) return
        modulePreferences.edit().putBoolean(key, enabled).apply()
        ModulePreferences.mirrorToFramework(app)
        if (key == ModulePreferences.AUTO_MANAGE && !enabled) {
            Az100ConnectionRuntime.get(app).cancel()
            app.stopService(Intent(app, Az100MonitorService::class.java))
        }
        refresh()
    }

    fun rememberPermissionAction(action: UiAction) { savedState["permission_action"] = action.name }
    fun consumePermissionAction(): UiAction? = savedState.remove<String>("permission_action")?.let {
        runCatching { UiAction.valueOf(it) }.getOrNull()
    }

    fun openDevicePicker() {
        savedState["device_picker"] = true
        mutableState.value = mutableState.value.copy(devicePickerOpen = true)
        refresh()
    }

    fun dismissDevicePicker() {
        savedState["device_picker"] = false
        mutableState.value = mutableState.value.copy(devicePickerOpen = false)
    }

    fun selectDevice(address: String) {
        if (address.isNotEmpty() && mutableState.value.deviceChoices.none { it.address == address }) return
        modulePreferences.edit().putString(ModulePreferences.SELECTED_ADDRESS, address).apply()
        ModulePreferences.mirrorToFramework(app)
        Az100ConnectionRuntime.get(app).reconcileConnection()
        mutableState.value = mutableState.value.copy(
            selectedAddress = address, reachable = false, leftBattery = ObservedValue(),
            rightBattery = ObservedValue(), caseBattery = ObservedValue(), outsideMode = ObservedValue(),
            noiseLevel = ObservedValue(), leftFirmware = ObservedValue(), rightFirmware = ObservedValue(),
            artwork = ArtworkState(),
        )
        dismissDevicePicker()
        refresh()
    }

    fun refresh() {
        if (!started || cleared) return
        if (refreshRunning) { refreshPending = true; return }
        refreshRunning = true
        val session = generation
        worker.execute {
            val result = runCatching { readSnapshot() }
            val snapshot = result.getOrNull()
            handler.post {
                refreshRunning = false
                val runtime = Az100ConnectionRuntime.get(app)
                val identityCurrent = snapshot != null && snapshot.selectedAddress ==
                    modulePreferences.getString(ModulePreferences.SELECTED_ADDRESS, "").orEmpty() &&
                    snapshot.sessionGeneration == runtime.generation() &&
                    snapshot.deviceAddress.equals(runtime.deviceAddress(), true)
                if (!cleared && started && session == generation && identityCurrent) {
                    val current = mutableState.value
                    mutableState.value = snapshot.copy(
                        selectedTab = current.selectedTab,
                        devicePickerOpen = current.devicePickerOpen,
                        appearance = current.appearance,
                        artwork = snapshot.artwork.copy(busy = current.artwork.busy, lastResult = current.artwork.lastResult),
                    )
                    scheduleExpiry(mutableState.value)
                } else if (!cleared && started && session == generation && result.isSuccess) {
                    refreshPending = true
                } else if (!cleared && started && session == generation && result.isFailure) {
                    mutableState.value = mutableState.value.copy(loading = false, reachable = false,
                        stateError = result.exceptionOrNull()?.message.orEmpty().ifBlank { app.getString(R.string.ui_state_unavailable) },
                        now = System.currentTimeMillis())
                }
                if (refreshPending && !cleared) { refreshPending = false; refresh() }
            }
        }
    }

    private fun readSnapshot(): TechnicsPodsUiState? {
        val gateway = app.contentResolver.call(Az100StateProvider.STATE_URI,
            Az100StateProvider.METHOD_STATE, null, null) ?: error(app.getString(R.string.ui_state_unavailable))
        val cached = Az100StateRepository(app).state()
        val bluetoothGranted = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        val selectedAddress = gateway.getString("selected_address").orEmpty()
        val address = gateway.getString("device_address").orEmpty()
        val runtimeGeneration = gateway.getLong("generation")
        val identityMatches = selectedAddress.isEmpty() || selectedAddress.equals(address, true)
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
        val devices = readDeviceChoices(adapter, selectedAddress, address)
        val service = TechnicsPodsApp.getXposedService()
        val active = service != null && runCatching {
            val scope = service.scope
            scope.contains("com.android.settings") && scope.contains("com.milink.service") &&
                (scope.contains("com.android.bluetooth") || scope.contains("com.xiaomi.bluetooth"))
        }.getOrDefault(false)
        val framework = if (service == null) "" else runCatching {
            "${service.frameworkName} ${service.frameworkVersion}"
        }.getOrDefault("LSPosed")
        val capabilityRows = capabilityPreferences.all.mapNotNull { (key, value) ->
            if (value !is String || ':' !in key) null else {
                val separator = value.indexOf(':')
                Capability(key.substringAfter(':'), key.substringBefore(':'),
                    if (separator < 0) value else value.substring(0, separator),
                    if (separator < 0) "" else value.substring(separator + 1).trim())
            }
        }.sortedWith(compareBy(Capability::feature, Capability::host))
        val targetAddress = if (identityMatches) address.ifEmpty { selectedAddress } else selectedAddress
        val detectedColor = gateway.getString("device_color").orEmpty().lowercase()
        val manualColor = modulePreferences.getString(ModulePreferences.ART_COLOR_OVERRIDE, "").orEmpty().lowercase()
        val requestedColor = manualColor.ifBlank { detectedColor }
        if (requestedColor.matches(Regex("black|silver|blue|gold|violet"))) {
            modulePreferences.edit().putString(ModulePreferences.ART_COLOR_AUTO, detectedColor).apply()
            val current = Az100ArtStore.metadata(app)
            if (!current.color().equals(requestedColor, true) || !"bundled".equals(current.source())) {
                runCatching { Az100ArtStore.selectBundledColor(app, requestedColor) }
            }
        }
        val version = Az100ArtStore.version(app)
        if (version != artworkVersion || artworkAddress != targetAddress) {
            val manifest = Az100ArtStore.metadata(app)
            val matchesDevice = manifest.deviceAddress().isNullOrBlank() || manifest.deviceAddress().equals(targetAddress, true)
            artworkCache = ArtworkState(
                main = if (matchesDevice) loadArtwork("main", 960) else null,
                left = if (matchesDevice) loadArtwork("left", 256) else null,
                right = if (matchesDevice) loadArtwork("right", 256) else null,
                case = if (matchesDevice) loadArtwork("case", 256) else null,
                slots = if (matchesDevice) manifest.slots().keys.toSet() else emptySet(),
                source = manifest.source(), color = manifest.color(), sourceVersion = manifest.sourceVersion(),
                detectedColor = detectedColor, manualColor = manualColor,
            )
            artworkVersion = version
            artworkAddress = targetAddress
        }
        // A device switch can clear persisted fields while artwork is being decoded.
        val recheck = app.contentResolver.call(Az100StateProvider.STATE_URI,
            Az100StateProvider.METHOD_STATE, null, null) ?: error(app.getString(R.string.ui_state_unavailable))
        if (runtimeGeneration != recheck.getLong("generation") ||
            address != recheck.getString("device_address").orEmpty() ||
            selectedAddress != recheck.getString("selected_address").orEmpty()) return null
        return TechnicsPodsUiState(
            loading = false, frameworkConnected = service != null, moduleActive = active, frameworkName = framework,
            bluetoothGranted = bluetoothGranted,
            bluetoothEnabled = readBluetoothEnabled(adapter),
            notificationsGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
            batteryUnrestricted = app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) == true,
            connectionStatus = if (identityMatches) gateway.getString("connection_status") ?: "disconnected" else "disconnected",
            connectionOwner = gateway.getString("connection_owner") ?: "none",
            sessionGeneration = runtimeGeneration,
            reachable = identityMatches && gateway.getBoolean("reachable"),
            deviceAddress = address, selectedAddress = selectedAddress,
            deviceName = devices.firstOrNull { it.address.equals(targetAddress, true) }?.name ?: "EAH-AZ100",
            deviceChoices = devices,
            leftBattery = if (identityMatches) ObservedValue(cached.left().batteryPercent(), cached.left().batteryUpdatedAtMillis()) else ObservedValue(),
            rightBattery = if (identityMatches) ObservedValue(cached.right().batteryPercent(), cached.right().batteryUpdatedAtMillis()) else ObservedValue(),
            caseBattery = if (identityMatches) ObservedValue(cached.cradleBatteryPercent(), cached.cradleUpdatedAtMillis()) else ObservedValue(),
            outsideMode = if (identityMatches) ObservedValue(cached.controls().outsideMode(), cached.controls().outsideModeUpdatedAtMillis()) else ObservedValue(),
            noiseLevel = if (identityMatches) ObservedValue(cached.controls().noiseCancellingLevel(), cached.controls().noiseCancellingLevelUpdatedAtMillis()) else ObservedValue(),
            leftFirmware = if (identityMatches) ObservedValue(cached.left().firmware(), cached.left().firmwareUpdatedAtMillis()) else ObservedValue(),
            rightFirmware = if (identityMatches) ObservedValue(cached.right().firmware(), cached.right().firmwareUpdatedAtMillis()) else ObservedValue(),
            now = System.currentTimeMillis(), lastError = if (identityMatches) cached.lastError().orEmpty() else "",
            controlStatus = if (identityMatches) gateway.getString("control_status").orEmpty() else "",
            controlError = if (identityMatches) gateway.getString("control_error").orEmpty() else "",
            startError = modulePreferences.getString(ModulePreferences.START_ERROR, "").orEmpty(),
            autoManage = modulePreferences.getBoolean(ModulePreferences.AUTO_MANAGE, false),
            statusIcon = modulePreferences.getBoolean(ModulePreferences.STATUS_ICON, true),
            diagnosticProbes = modulePreferences.getBoolean(ModulePreferences.DIAGNOSTICS, false),
            appearance = appearanceFromPreference(), capabilities = capabilityRows, artwork = artworkCache,
        )
    }

    private fun scheduleExpiry(state: TechnicsPodsUiState) {
        handler.removeCallbacks(refreshAtExpiry)
        if (!state.reachable) return
        val next = listOf(state.leftBattery.updatedAt, state.rightBattery.updatedAt, state.caseBattery.updatedAt,
            state.outsideMode.updatedAt, state.noiseLevel.updatedAt, state.leftFirmware.updatedAt,
            state.rightFirmware.updatedAt).filter { it > 0L }.map { it + 120_001L }
            .filter { it > state.now }.minOrNull() ?: return
        handler.postDelayed(refreshAtExpiry, (next - System.currentTimeMillis()).coerceAtLeast(1))
    }

    private fun readDeviceChoices(adapter: BluetoothAdapter?, selected: String, connected: String): List<DeviceChoice> {
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            val choices = ArrayList<DeviceChoice>()
            for (device in adapter?.bondedDevices.orEmpty()) {
                val name = device.name.orEmpty()
                val address = device.address
                if (Az100DeviceMatcher.matchesName(name) || address.equals(selected, true) || address.equals(connected, true)) {
                    choices.add(DeviceChoice(name.ifBlank { "EAH-AZ100" }, address))
                }
            }
            choices.sortedBy { it.address }
        } catch (_: SecurityException) { emptyList() }
    }

    private fun readBluetoothEnabled(adapter: BluetoothAdapter?): Boolean {
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        return try { adapter?.isEnabled == true } catch (_: SecurityException) { false }
    }

    private fun loadArtwork(slot: String, size: Int): Bitmap? {
        val bitmap = Az100ArtStore.load(app, slot) ?: return null
        val dimension = maxOf(bitmap.width, bitmap.height)
        if (dimension <= size) return bitmap
        val scale = size.toFloat() / dimension
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun importImage(uri: Uri) = artworkAction {
        Az100ArtStore.importImage(app, uri)
        app.getString(R.string.art_import_success)
    }

    fun importOfficialArtwork() = artworkAction {
        val result = Az100ArtStore.importOfficialResult(app, "")
        if (result.success()) app.getString(R.string.art_official_success)
        else app.getString(R.string.ui_art_import_detail, importStatus(result.status()), result.message())
    }

    fun importArtworkManifest(uri: Uri) = artworkAction {
        val result = Az100ArtStore.importOfficialManifest(app, uri)
        if (result.success()) app.getString(R.string.ui_art_manifest_success)
        else app.getString(R.string.ui_art_import_detail, importStatus(result.status()), result.message())
    }

    fun resetArtwork() = artworkAction {
        Az100ArtStore.clear(app)
        app.getString(R.string.ui_art_reset_done)
    }

    private fun artworkAction(action: () -> String) {
        if (cleared || mutableState.value.artwork.busy) return
        mutableState.value = mutableState.value.copy(artwork = mutableState.value.artwork.copy(busy = true, lastResult = ""))
        worker.execute {
            val message = runCatching(action).getOrElse {
                app.getString(R.string.ui_art_import_detail, app.getString(R.string.art_import_failed),
                    it.message.orEmpty().ifBlank { it.javaClass.simpleName })
            }
            artworkVersion = Long.MIN_VALUE
            handler.post {
                if (!cleared) {
                    mutableState.value = mutableState.value.copy(artwork = mutableState.value.artwork.copy(busy = false, lastResult = message))
                    messageChannel.trySend(message)
                    refresh()
                }
            }
        }
    }

    private fun hasPermission(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun importStatus(status: Az100ArtImportResult.Status) = app.getString(when (status) {
        Az100ArtImportResult.Status.SUCCESS -> R.string.art_official_success
        Az100ArtImportResult.Status.MAPPING_REQUIRED -> R.string.ui_art_mapping_required
        Az100ArtImportResult.Status.APP_MISSING -> R.string.official_app_missing
        Az100ArtImportResult.Status.SOURCE_MISMATCH -> R.string.ui_art_source_mismatch
        Az100ArtImportResult.Status.RESOURCE_MISSING -> R.string.ui_art_resource_missing
        else -> R.string.art_import_failed
    })
    private fun appearanceFromPreference() = runCatching {
        AppAppearance.valueOf(uiPreferences.getString("appearance", AppAppearance.System.name).orEmpty())
    }.getOrDefault(AppAppearance.System)

    override fun onCleared() {
        cleared = true
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        messageChannel.close()
        super.onCleared()
    }
}
