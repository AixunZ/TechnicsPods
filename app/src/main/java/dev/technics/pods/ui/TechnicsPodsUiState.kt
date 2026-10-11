package dev.technics.pods.ui

import android.graphics.Bitmap

enum class AppAppearance { System, Light, Dark }

enum class UiAction {
    EnableAutomation, ChooseDevice, RequestPermissions, ImportImage, ImportOfficialImage,
    ImportArtworkManifest, ResetImage, OpenDiagnostics, OpenAppSettings, OpenBatterySettings,
    OpenBluetoothSettings, OpenHuaweiReference, OpenSonyReference, OpenLicense,
}

data class ObservedValue<T>(val value: T? = null, val updatedAt: Long = 0L)
data class DeviceChoice(val name: String, val address: String)
data class Capability(val feature: String, val host: String, val status: String, val detail: String)

data class ArtworkState(
    val main: Bitmap? = null,
    val left: Bitmap? = null,
    val right: Bitmap? = null,
    val case: Bitmap? = null,
    val slots: Set<String> = emptySet(),
    val source: String = "unverified",
    val color: String = "",
    val detectedColor: String = "",
    val manualColor: String = "",
    val sourceVersion: String = "",
    val busy: Boolean = false,
    val lastResult: String = "",
)

/** Read-only presentation snapshot. Timestamps stay separate for every observed field. */
data class TechnicsPodsUiState(
    val loading: Boolean = true,
    val frameworkConnected: Boolean = false,
    val moduleActive: Boolean = false,
    val frameworkName: String = "",
    val bluetoothGranted: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val connectionStatus: String = "disconnected",
    val connectionOwner: String = "none",
    val sessionGeneration: Long = 0L,
    val stateError: String = "",
    val reachable: Boolean = false,
    val deviceAddress: String = "",
    val selectedAddress: String = "",
    val deviceName: String = "EAH-AZ100",
    val deviceChoices: List<DeviceChoice> = emptyList(),
    val devicePickerOpen: Boolean = false,
    val leftBattery: ObservedValue<Int> = ObservedValue(),
    val rightBattery: ObservedValue<Int> = ObservedValue(),
    val caseBattery: ObservedValue<Int> = ObservedValue(),
    val outsideMode: ObservedValue<Int> = ObservedValue(),
    val noiseLevel: ObservedValue<Int> = ObservedValue(),
    val leftFirmware: ObservedValue<String> = ObservedValue(),
    val rightFirmware: ObservedValue<String> = ObservedValue(),
    val now: Long = 0L,
    val lastError: String = "",
    val controlStatus: String = "",
    val controlError: String = "",
    val startError: String = "",
    val autoManage: Boolean = false,
    val statusIcon: Boolean = true,
    val diagnosticProbes: Boolean = false,
    val appearance: AppAppearance = AppAppearance.System,
    val selectedTab: Int = 0,
    val capabilities: List<Capability> = emptyList(),
    val artwork: ArtworkState = ArtworkState(),
)
