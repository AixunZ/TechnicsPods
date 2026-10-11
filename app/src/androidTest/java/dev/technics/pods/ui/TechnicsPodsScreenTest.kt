package dev.technics.pods.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TechnicsPodsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeTabsKeepDailyControlInSystemPages() {
        val snapshot = mutableStateOf(readyState())
        compose.setContent {
            TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {},
                { snapshot.value = snapshot.value.copy(appearance = it) }, { _, _ -> }, {}, {})
        }
        compose.onNodeWithTag("page_0").assertIsDisplayed()
        compose.onNodeWithText("模块已启用").assertIsDisplayed()
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithTag("page_1").assertIsDisplayed()
        compose.onNodeWithContentDescription("左耳，电量 82%，最近读取").assertIsDisplayed()
        compose.onNodeWithText("应用降噪强度").assertDoesNotExist()
        compose.onNodeWithText("切换外音模式？").assertDoesNotExist()
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("page_2").assertIsDisplayed()
        assertEquals(2, snapshot.value.selectedTab)
    }

    @Test fun appearanceSwitchRetainsSelectedPageAndSurvivesStateRestore() {
        val snapshot = mutableStateOf(readyState())
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {},
                { snapshot.value = snapshot.value.copy(appearance = it) }, { _, _ -> }, {}, {})
        }
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("appearance_setting").performClick()
        compose.onNodeWithTag("theme_Dark").performClick()
        compose.onNodeWithTag("appearance_Dark").assertIsDisplayed()
        compose.onNodeWithTag("page_2").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("appearance_Dark").assertIsDisplayed()
        compose.onNodeWithTag("page_2").assertIsDisplayed()
        assertEquals(2, snapshot.value.selectedTab)
    }

    @Test fun largeFontKeepsBatteryInformationAccessibleAndCachedSeparately() {
        verifyLargeFont(AppAppearance.Light)
    }

    @Test fun darkLargeFontAt320DpRendersBatteryState() {
        verifyLargeFont(AppAppearance.Dark)
    }

    @Test fun homeGridAlignsCardsAndLimitsLongDeviceNameInBothThemes() {
        val snapshot = mutableStateOf(readyState().copy(
            deviceName = "Technics EAH-AZ100 — long renamed device from the Bluetooth settings",
            frameworkName = "LSPosed 2.2.1", appearance = AppAppearance.Light,
        ))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 1f }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Box(Modifier.requiredWidth(400.dp)) {
                    TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        for (appearance in listOf(AppAppearance.Light, AppAppearance.Dark)) {
            compose.runOnIdle { snapshot.value = snapshot.value.copy(appearance = appearance) }
            compose.waitForIdle()
            val module = bounds("home_module_card")
            val bluetooth = bounds("home_bluetooth_card")
            val device = bounds("home_device_card")
            assertEquals("Top edge must align", module.top.value, bluetooth.top.value, 0.5f)
            assertEquals("Bottom edge must align", module.bottom.value, device.bottom.value, 0.5f)
            assertEquals("Right cards must have equal heights", (bluetooth.bottom - bluetooth.top).value,
                (device.bottom - device.top).value, 0.5f)
            assertTrue("Module icon must have its own space below the summary",
                bounds("home_module_icon").top >= bounds("home_module_summary").bottom)

            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("home_device_name", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            assertTrue("Long names must be bounded to two lines", layouts.single().lineCount <= 2)
            assertTrue("Long names must end with an ellipsis", layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
            captureScreenshot("home_${appearance.name.lowercase()}_400_long_name", appearance)
        }
    }

    @Test fun homeAt320DpAndLargeFontStacksCardsWithoutOverlappingText() {
        val snapshot = mutableStateOf(readyState().copy(
            appearance = AppAppearance.Dark, deviceName = "Technics EAH-AZ100", frameworkName = "LSPosed 2.2.1",
        ))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 2f }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        compose.onNodeWithTag("home_module_card").performScrollTo()
        val module = bounds("home_module_card")
        val bluetooth = bounds("home_bluetooth_card")
        val device = bounds("home_device_card")
        assertEquals(module.left.value, bluetooth.left.value, 0.5f)
        assertEquals(module.left.value, device.left.value, 0.5f)
        assertEquals((module.right - module.left).value, (device.right - device.left).value, 0.5f)
        assertTrue("Narrow screen must place Bluetooth below the module", module.bottom < bluetooth.top)
        assertTrue("Large font must place the device below Bluetooth", bluetooth.bottom < device.top)
        assertTrue("Module icon must stay below text at large font size",
            bounds("home_module_icon").top >= bounds("home_module_summary").bottom)
        compose.onNodeWithTag("home_device_name", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun batteryCardsAlignEvenWhenArtworkAspectRatiosDifferAndInformationUsesCards() {
        val snapshot = mutableStateOf(readyState().copy(selectedTab = 1, artwork = ArtworkState(
            left = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888),
            right = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888),
            case = Bitmap.createBitmap(240, 50, Bitmap.Config.ARGB_8888),
        )))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 1f }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Box(Modifier.requiredWidth(400.dp)) {
                    TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        compose.onNodeWithTag("battery_card_0").performScrollTo()
        val cards = (0..2).map { bounds("battery_card_$it") }
        val slots = (0..2).map { bounds("battery_image_$it") }
        for (index in 1..2) {
            assertEquals("Battery top edges", cards[0].top.value, cards[index].top.value, 0.5f)
            assertEquals("Battery bottom edges", cards[0].bottom.value, cards[index].bottom.value, 0.5f)
            assertEquals("Image slot top edges", slots[0].top.value, slots[index].top.value, 0.5f)
            assertEquals("Image slot heights", (slots[0].bottom - slots[0].top).value,
                (slots[index].bottom - slots[index].top).value, 0.5f)
        }
        assertEquals("Artwork gets a fixed slot independent of its aspect ratio", 64f,
            (slots[0].bottom - slots[0].top).value, 0.5f)
        compose.onNodeWithTag("device_status_card").performScrollTo()
        assertTrue("Device state must have a full-width background card",
            (bounds("device_status_card").right - bounds("device_status_card").left).value >= 350f)
        compose.onNodeWithTag("device_settings_card").performScrollTo()
        assertTrue("Device and artwork settings must have a full-width background card",
            (bounds("device_settings_card").right - bounds("device_settings_card").left).value >= 350f)
    }

    @Test fun deviceSettingsRowsOpenAndDispatchTheirActions() {
        val snapshot = mutableStateOf(readyState().copy(selectedTab = 1))
        val actions = mutableListOf<UiAction>()
        val colors = mutableListOf<String?>()
        compose.setContent {
            TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) },
                { actions += it; if (it == UiAction.ChooseDevice) snapshot.value = snapshot.value.copy(devicePickerOpen = true) },
                { snapshot.value = snapshot.value.copy(appearance = it) }, { _, _ -> },
                { snapshot.value = snapshot.value.copy(devicePickerOpen = false) }, {}, { colors += it })
        }
        compose.onNodeWithText("选择 AZ100").performScrollTo().performClick()
        compose.onNodeWithText("选择已配对的 AZ100").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()

        compose.onNodeWithText("耳机颜色").performScrollTo().performClick()
        compose.onNodeWithTag("color_blue").performClick()
        assertEquals(listOf("blue"), colors)

        compose.onNodeWithText("设备图片").performScrollTo().performClick()
        compose.onNodeWithText("导入图片").assertIsDisplayed()
        compose.onNodeWithText("导入图片").performClick()
        assertEquals(listOf(UiAction.ChooseDevice, UiAction.ImportImage), actions)
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun verifyLargeFont(appearance: AppAppearance) {
        val snapshot = mutableStateOf(readyState().copy(
            selectedTab = 1, rightBattery = ObservedValue(69, 1L), appearance = appearance,
        ))
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { fontScale = 2f }
            val density = Density(LocalDensity.current.density, 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                Box(Modifier.width(320.dp)) {
                    TechnicsPodsScreen(snapshot.value, { snapshot.value = snapshot.value.copy(selectedTab = it) }, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        captureScreenshot("device_${appearance.name.lowercase()}_320_font2", appearance)
        compose.onNodeWithContentDescription("左耳，电量 82%，最近读取").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("右耳，电量 69%，缓存").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("充电盒，电量 --，未知").performScrollTo().assertIsDisplayed()
        captureScreenshot("batteries_${appearance.name.lowercase()}_320_font2", appearance)
    }

    private fun captureScreenshot(name: String, appearance: AppAppearance) {
        val bitmap = compose.onNodeWithTag("appearance_${appearance.name}")
            .captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ui-qa")
        assertTrue(directory.isDirectory || directory.mkdirs())
        FileOutputStream(File(directory, "$name.png")).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val colors = HashSet<Int>()
        val step = (bitmap.width / 80).coerceAtLeast(1)
        for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) colors.add(bitmap.getPixel(x, y))
        assertTrue("Rendered screenshot must contain visible content", colors.size > 8)
    }

    private fun readyState(): TechnicsPodsUiState {
        val now = System.currentTimeMillis()
        return TechnicsPodsUiState(loading = false, moduleActive = true, frameworkConnected = true,
            bluetoothGranted = true, bluetoothEnabled = true, connectionStatus = "idle", reachable = true,
            leftBattery = ObservedValue(82, now), rightBattery = ObservedValue(69, now), now = now)
    }
}
