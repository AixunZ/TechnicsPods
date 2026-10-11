package dev.technics.pods.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.technics.pods.BuildConfig
import dev.technics.pods.ModulePreferences
import dev.technics.pods.R
import dev.technics.pods.state.Az100StateFreshness
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun TechnicsPodsAppUi(model: TechnicsPodsViewModel, onAction: (UiAction) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    TechnicsPodsScreen(
        state = state, onTabSelected = model::selectTab, onAction = onAction,
        onAppearanceSelected = model::setAppearance, onSettingChanged = model::setBooleanPreference,
        onDeviceSelected = model::selectDevice, onDevicePickerDismissed = model::dismissDevicePicker,
        onColorSelected = model::setArtworkColor,
    )
}

@Composable
fun TechnicsPodsScreen(
    state: TechnicsPodsUiState,
    onTabSelected: (Int) -> Unit,
    onAction: (UiAction) -> Unit,
    onAppearanceSelected: (AppAppearance) -> Unit,
    onSettingChanged: (String, Boolean) -> Unit,
    onDeviceSelected: (String) -> Unit,
    onDevicePickerDismissed: () -> Unit,
    onColorSelected: (String?) -> Unit = {},
) {
    val mode = when (state.appearance) {
        AppAppearance.System -> ColorSchemeMode.System
        AppAppearance.Light -> ColorSchemeMode.Light
        AppAppearance.Dark -> ColorSchemeMode.Dark
    }
    val themeController = remember(mode) { ThemeController(mode) }
    val dark = when (state.appearance) {
        AppAppearance.System -> isSystemInDarkTheme()
        AppAppearance.Light -> false
        AppAppearance.Dark -> true
    }
    val view = LocalView.current
    val activity = LocalActivity.current
    SideEffect {
        if (activity != null) WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MiuixTheme(controller = themeController) {
        val pager = rememberPagerState(initialPage = state.selectedTab, pageCount = { 3 })
        val scope = rememberCoroutineScope()
        val titles = listOf(stringResource(R.string.ui_home), stringResource(R.string.ui_device), stringResource(R.string.ui_about))
        val icons = listOf(MiuixIcons.GridView, MiuixIcons.Music, MiuixIcons.Info)
        var themeDialog by rememberSaveable { mutableStateOf(false) }
        var artworkDialog by rememberSaveable { mutableStateOf(false) }
        var colorDialog by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(pager.settledPage) { onTabSelected(pager.settledPage) }
        Scaffold(
            modifier = Modifier.testTag("appearance_${state.appearance.name}"),
            bottomBar = {
                NavigationBar(color = MiuixTheme.colorScheme.surface, showDivider = false) {
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            icon = icons[index], label = title,
                            modifier = Modifier.testTag("tab_$index"),
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pager, beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            ) { page ->
                PageShell(title = if (page == 0) stringResource(R.string.app_name) else titles[page], tag = "page_$page") { pagePadding, scrollModifier ->
                    when (page) {
                        0 -> HomePage(state, pagePadding, scrollModifier)
                        1 -> DevicePage(state, pagePadding, scrollModifier, onAction, { artworkDialog = true }, { colorDialog = true })
                        2 -> AboutPage(state, pagePadding, scrollModifier, onAction, onSettingChanged) { themeDialog = true }
                    }
                }
            }
        }
        AppearanceDialog(themeDialog, state.appearance, { themeDialog = false }) {
            onAppearanceSelected(it)
            themeDialog = false
        }
        ArtworkDialog(artworkDialog, state, { artworkDialog = false }) {
            artworkDialog = false
            onAction(it)
        }
        ColorDialog(colorDialog, state.artwork.manualColor.ifBlank { state.artwork.detectedColor },
            { colorDialog = false }) { selected ->
                onColorSelected(selected)
                colorDialog = false
            }
        DevicePickerDialog(state, onDevicePickerDismissed, onDeviceSelected)
    }
}

@Composable
private fun PageShell(title: String, tag: String, content: @Composable (PaddingValues, Modifier) -> Unit) {
    val scroll = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.testTag(tag),
        topBar = { TopAppBar(title = title, largeTitle = title, scrollBehavior = scroll) },
    ) { padding -> content(padding, Modifier.nestedScroll(scroll.nestedScrollConnection)) }
}

private fun pagePadding(padding: PaddingValues) = PaddingValues(
    start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp,
    bottom = padding.calculateBottomPadding() + 24.dp,
)

@Composable
private fun HomePage(state: TechnicsPodsUiState, padding: PaddingValues, modifier: Modifier) {
    val dark = MiuixTheme.colorScheme.background.red < 0.35f
    val content = pagePadding(padding)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = content,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 340.dp || LocalConfiguration.current.fontScale > 1.2f
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeModuleCard(state, dark, Modifier.fillMaxWidth())
                        HomeBluetoothCard(state, dark, Modifier.fillMaxWidth())
                        HomeDeviceCard(state, dark, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("home_status_grid")) {
                        HomeModuleCard(state, dark, Modifier.weight(1.08f).fillMaxHeight())
                        Column(Modifier.weight(0.92f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeBluetoothCard(state, dark, Modifier.fillMaxWidth().weight(1f))
                            HomeDeviceCard(state, dark, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
        }
        item { HomeSystemCard(state, dark) }
        if (state.stateError.isNotBlank() || state.startError.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = if (dark) Color(0xFF3A2526) else Color(0xFFFFECE9),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.ui_state_unavailable), fontWeight = FontWeight.SemiBold,
                            color = if (dark) Color(0xFFFFB4AA) else Color(0xFF9D2D27))
                        state.stateError.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        state.startError.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeModuleCard(state: TechnicsPodsUiState, dark: Boolean, modifier: Modifier) {
    val accent = if (state.moduleActive) {
        if (dark) Color(0xFFB9F2CF) else Color(0xFF176B45)
    } else {
        if (dark) Color(0xFFFFB1A8) else Color(0xFFB33A32)
    }
    val background = if (state.moduleActive) {
        if (dark) Color(0xFF243A31) else Color(0xFFE0F4E8)
    } else {
        if (dark) Color(0xFF472B2C) else Color(0xFFFFE2DE)
    }
    val title = stringResource(if (state.moduleActive) R.string.ui_module_active else R.string.ui_module_inactive)
    val summary = stringResource(when {
        state.moduleActive -> R.string.ui_module_scopes_ready
        state.frameworkConnected -> R.string.ui_module_scopes_missing
        else -> R.string.ui_module_service_missing
    })
    Card(
        colors = CardDefaults.defaultColors(color = background),
        modifier = modifier.heightIn(min = 248.dp).testTag("home_module_card").semantics {
            contentDescription = "$title, $summary"
        },
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_framework), fontSize = 14.sp, color = accent)
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
            Text(summary, fontSize = 14.sp, color = accent, modifier = Modifier.testTag("home_module_summary"))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.frameworkName, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(
                    imageVector = if (state.moduleActive) MiuixIcons.Ok else MiuixIcons.Info,
                    contentDescription = null,
                    tint = accent.copy(alpha = if (dark) 0.78f else 0.55f),
                    modifier = Modifier.size(40.dp).testTag("home_module_icon"),
                )
            }
        }
    }
}

@Composable
private fun HomeBluetoothCard(state: TechnicsPodsUiState, dark: Boolean, modifier: Modifier) {
    val enabled = state.bluetoothGranted && state.bluetoothEnabled
    val background = when {
        enabled && dark -> Color(0xFF20384C)
        enabled -> Color(0xFFE0F0FF)
        dark -> Color(0xFF353033)
        else -> Color(0xFFF0ECEE)
    }
    val accent = when {
        enabled && dark -> Color(0xFFA9D8FF)
        enabled -> Color(0xFF236DA5)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    HomeMetricCard(
        title = stringResource(R.string.ui_bluetooth),
        value = stringResource(when {
            !state.bluetoothGranted -> R.string.permission_bluetooth_missing
            enabled -> R.string.ui_bluetooth_on
            else -> R.string.ui_bluetooth_off
        }),
        icon = MiuixIcons.GridView,
        background = background,
        valueColor = accent,
        modifier = modifier.testTag("home_bluetooth_card"),
    )
}

@Composable
private fun HomeDeviceCard(state: TechnicsPodsUiState, dark: Boolean, modifier: Modifier) {
    val background = if (dark) Color(0xFF302A43) else Color(0xFFEDE6FF)
    val accent = if (dark) Color(0xFFD0C1FF) else Color(0xFF61459A)
    val device = state.deviceName.ifBlank { stringResource(R.string.value_unknown) }
    val detail = when {
        state.deviceChoices.isNotEmpty() -> state.deviceChoices.size.toString()
        state.selectedAddress.isNotBlank() || state.deviceAddress.isNotBlank() -> "1"
        else -> "0"
    }
    HomeMetricCard(
        title = stringResource(R.string.ui_current_device),
        value = device,
        supporting = detail,
        icon = MiuixIcons.Music,
        background = background,
        valueColor = accent,
        modifier = modifier.testTag("home_device_card"),
        valueTag = "home_device_name",
    )
}

@Composable
private fun HomeMetricCard(
    title: String,
    value: String,
    background: Color,
    valueColor: Color,
    modifier: Modifier,
    icon: ImageVector,
    supporting: String = "",
    valueTag: String = "",
) {
    Card(colors = CardDefaults.defaultColors(color = background), modifier = modifier.heightIn(min = 124.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, fontSize = if (supporting.isBlank()) 21.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag(valueTag))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(supporting, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Icon(imageVector = icon, contentDescription = null, tint = valueColor.copy(alpha = 0.68f),
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun HomeSystemCard(state: TechnicsPodsUiState, dark: Boolean) {
    val background = if (dark) Color(0xFF252525) else Color(0xFFF1F1F3)
    Card(colors = CardDefaults.defaultColors(color = background), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 19.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text(stringResource(R.string.ui_module_info), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            HomeInfoLine(stringResource(R.string.ui_system), "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}")
            HomeInfoLine(stringResource(R.string.ui_version), BuildConfig.VERSION_NAME)
            HomeInfoLine(stringResource(R.string.ui_framework), state.frameworkName.ifBlank { stringResource(R.string.framework_disconnected) })
            HomeInfoLine(stringResource(R.string.ui_connection_status), connectionText(state))
            HomeInfoLine(stringResource(R.string.ui_current_device), state.deviceName)
        }
    }
}

@Composable
private fun HomeInfoLine(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(value.ifBlank { stringResource(R.string.value_unknown) }, style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun DevicePage(state: TechnicsPodsUiState, padding: PaddingValues, modifier: Modifier, onAction: (UiAction) -> Unit,
                      onArtwork: () -> Unit, onColor: () -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = pagePadding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 240.dp), contentAlignment = Alignment.Center) {
                    val bitmap = state.artwork.main
                    if (bitmap == null) Image(painterResource(R.drawable.ic_az100_placeholder),
                        stringResource(R.string.ui_product_placeholder), Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 220.dp), contentScale = ContentScale.Fit)
                    else Image(bitmap.asImageBitmap(), stringResource(R.string.product_image_description),
                        Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 240.dp), contentScale = ContentScale.Fit)
                }
                Text(state.deviceName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(connectionText(state), color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp))
            }
        }
        item { BatteryRow(state) }
        item {
            DeviceInfoCard(stringResource(R.string.ui_device_status), "device_status_card") {
                InfoRow(stringResource(R.string.ui_outside_mode), stringResource(when (state.outsideMode.value) {
                    0 -> R.string.mode_off; 1 -> R.string.mode_anc; 2 -> R.string.mode_ambient; else -> R.string.state_unknown
                }), freshness(state.outsideMode, state))
                InfoRow(stringResource(R.string.ui_noise_level), state.noiseLevel.value?.toString() ?: stringResource(R.string.value_unknown),
                    freshness(state.noiseLevel, state))
                InfoRow(stringResource(R.string.ui_left_firmware), state.leftFirmware.value ?: stringResource(R.string.value_unknown),
                    freshness(state.leftFirmware, state))
                InfoRow(stringResource(R.string.ui_right_firmware), state.rightFirmware.value ?: stringResource(R.string.value_unknown),
                    freshness(state.rightFirmware, state))
                if (state.controlStatus.isNotBlank()) InfoRow(stringResource(R.string.ui_control_result), controlStatus(state))
                if (state.lastError.isNotBlank()) InfoRow(stringResource(R.string.ui_last_error), state.lastError)
            }
        }
        item {
            DeviceInfoCard(stringResource(R.string.section_devices), "device_settings_card") {
                ActionRow(stringResource(R.string.choose_device), state.selectedAddress.ifEmpty { stringResource(R.string.device_automatic) }) {
                    onAction(UiAction.ChooseDevice)
                }
                val address = state.selectedAddress.ifEmpty { state.deviceAddress }
                if (address.isNotEmpty()) InfoRow(stringResource(R.string.ui_device_address), address)
                ActionRow(stringResource(R.string.ui_art_color), artColorLabel(state), onClick = onColor)
                ActionRow(stringResource(R.string.section_artwork), artworkSource(state), enabled = !state.artwork.busy, onClick = onArtwork)
                if (state.artwork.lastResult.isNotBlank()) InfoRow(stringResource(R.string.ui_art_result), state.artwork.lastResult)
            }
        }
    }
}

@Composable
private fun artColorLabel(state: TechnicsPodsUiState): String {
    val key = state.artwork.manualColor.ifBlank { state.artwork.detectedColor.ifBlank { state.artwork.color } }
    val label = when (key.lowercase()) {
        "black" -> stringResource(R.string.ui_color_black)
        "silver" -> stringResource(R.string.ui_color_silver)
        "blue" -> stringResource(R.string.ui_color_blue)
        "gold" -> stringResource(R.string.ui_color_champagne)
        "violet" -> stringResource(R.string.ui_color_violet)
        else -> stringResource(R.string.ui_color_unknown)
    }
    return label + if (state.artwork.manualColor.isNotBlank()) " · " + stringResource(R.string.ui_color_manual)
    else " · " + stringResource(R.string.ui_color_auto)
}

@Composable
private fun DeviceInfoCard(title: String, tag: String, content: @Composable () -> Unit) {
    val dark = MiuixTheme.colorScheme.background.red < 0.35f
    Card(colors = CardDefaults.defaultColors(color = if (dark) Color(0xFF252525) else Color(0xFFF1F1F3)),
        modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            content()
        }
    }
}

@Composable
private fun BatteryRow(state: TechnicsPodsUiState) {
    val labels = listOf(R.string.battery_left, R.string.battery_right, R.string.battery_case)
    val batteries = listOf(state.leftBattery, state.rightBattery, state.caseBattery)
    val images = listOf(state.artwork.left, state.artwork.right, state.artwork.case)
    val fontScale = LocalConfiguration.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 300.dp || fontScale > 1.35f
        @Composable fun battery(index: Int, modifier: Modifier) {
            val label = stringResource(labels[index])
            val value = batteries[index].value?.let { stringResource(R.string.battery_percent, it) } ?: stringResource(R.string.value_unknown)
            val detail = freshness(batteries[index], state)
            val description = stringResource(R.string.battery_accessibility, label, value, detail)
            Card(modifier = modifier.testTag("battery_card_$index").semantics(mergeDescendants = true) { contentDescription = description }) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp).testTag("battery_image_$index"), contentAlignment = Alignment.Center) {
                        images[index]?.let { Image(it.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                    Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(detail, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { labels.indices.forEach { battery(it, Modifier.fillMaxWidth()) } }
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.indices.forEach { battery(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun AboutPage(state: TechnicsPodsUiState, padding: PaddingValues, modifier: Modifier, onAction: (UiAction) -> Unit,
                      onSetting: (String, Boolean) -> Unit, onAppearance: () -> Unit) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = pagePadding(padding)) {
        item { SectionLabel(stringResource(R.string.section_settings)) }
        item {
            SettingToggle(stringResource(R.string.auto_management), state.autoManage) {
                if (it) onAction(UiAction.EnableAutomation) else onSetting(ModulePreferences.AUTO_MANAGE, false)
            }
            SettingToggle(stringResource(R.string.status_icon), state.statusIcon) { onSetting(ModulePreferences.STATUS_ICON, it) }
            ActionRow(stringResource(R.string.ui_appearance), appearanceName(state.appearance), onClick = onAppearance,
                modifier = Modifier.testTag("appearance_setting"))
        }
        item { SectionLabel(stringResource(R.string.section_permissions)) }
        item {
            ActionRow(stringResource(R.string.request_permissions), stringResource(R.string.ui_permission_brief,
                stringResource(if (state.bluetoothGranted) R.string.permission_granted else R.string.permission_missing),
                stringResource(if (state.notificationsGranted) R.string.permission_granted else R.string.permission_missing))) {
                onAction(UiAction.RequestPermissions)
            }
            ActionRow(stringResource(R.string.open_app_settings)) { onAction(UiAction.OpenAppSettings) }
            ActionRow(stringResource(R.string.open_battery_settings), stringResource(if (state.batteryUnrestricted)
                R.string.battery_unrestricted else R.string.battery_optimized)) { onAction(UiAction.OpenBatterySettings) }
            ActionRow(stringResource(R.string.recover_background), state.startError) { onAction(UiAction.EnableAutomation) }
        }
        item { SectionLabel(stringResource(R.string.section_advanced)) }
        item {
            ActionRow(stringResource(R.string.open_diagnostics)) { onAction(UiAction.OpenDiagnostics) }
            SettingToggle(stringResource(R.string.diagnostic_probes), state.diagnosticProbes) { onSetting(ModulePreferences.DIAGNOSTICS, it) }
        }
        item { SectionLabel(stringResource(R.string.section_capabilities)) }
        item {
            val features = listOf("settings_bridge" to R.string.capability_settings, "hyperos_bridge" to R.string.capability_hyperos,
                "fusion_route" to R.string.capability_fusion, "status_bar" to R.string.capability_status_bar,
                "artwork" to R.string.capability_artwork, "settings_noise_level" to R.string.capability_noise_level,
                "settings_haptics" to R.string.ui_capability_settings_haptics)
            features.forEach { (feature, label) ->
                val reports = state.capabilities.filter { it.feature == feature }
                val reportLines = reports.map { "${it.host} · ${capabilityStatus(it.status)}" }
                InfoRow(stringResource(label), if (reports.isEmpty()) stringResource(R.string.capability_unknown)
                else reportLines.joinToString("\n"))
            }
        }
        item { SectionLabel(stringResource(R.string.ui_developer)) }
        item {
            InfoRow(stringResource(R.string.app_name), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow(stringResource(R.string.ui_developer), stringResource(R.string.ui_contributors))
            ActionRow(stringResource(R.string.ui_license), "GPL-3.0") { onAction(UiAction.OpenLicense) }
            ActionRow("HuaweiPods", stringResource(R.string.ui_reference_project)) { onAction(UiAction.OpenHuaweiReference) }
            ActionRow("SonyPods", stringResource(R.string.ui_reference_project)) { onAction(UiAction.OpenSonyReference) }
            InfoRow("Miuix KMP", "0.9.2 · Apache-2.0")
        }
    }
}

@Composable
private fun SectionLabel(title: String) = SmallTitle(title, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun InfoRow(title: String, value: String, detail: String = "") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value.ifBlank { stringResource(R.string.value_unknown) }, fontSize = 17.sp,
            color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ActionRow(title: String, summary: String = "", enabled: Boolean = true,
                      modifier: Modifier = Modifier, onClick: () -> Unit) {
    BasicComponent(title = title, summary = summary.ifBlank { null }, enabled = enabled,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        endActions = { Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions) })
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    BasicComponent(title = title, onClick = { onChecked(!checked) },
        endActions = { Switch(checked = checked, onCheckedChange = onChecked) })
}

@Composable
private fun AppearanceDialog(show: Boolean, appearance: AppAppearance, onDismiss: () -> Unit, onSelected: (AppAppearance) -> Unit) {
    OverlayDialog(show = show, title = stringResource(R.string.ui_appearance), onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp)) {
        AppAppearance.entries.forEach { mode ->
            BasicComponent(title = appearanceName(mode),
                modifier = Modifier.testTag("theme_${mode.name}").clickable { onSelected(mode) }
                    .semantics { selected = appearance == mode },
                endActions = { if (appearance == mode) Icon(MiuixIcons.Ok, contentDescription = null, tint = MiuixTheme.colorScheme.primary) })
        }
        TextButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun ArtworkDialog(show: Boolean, state: TechnicsPodsUiState, onDismiss: () -> Unit, onAction: (UiAction) -> Unit) {
    OverlayDialog(show = show, title = stringResource(R.string.section_artwork), summary = artworkSource(state),
        modifier = Modifier.widthIn(max = 520.dp), onDismissRequest = onDismiss) {
        ActionRow(stringResource(R.string.import_art)) { onAction(UiAction.ImportImage) }
        ActionRow(stringResource(R.string.official_art)) { onAction(UiAction.ImportOfficialImage) }
        ActionRow(stringResource(R.string.ui_art_manifest)) { onAction(UiAction.ImportArtworkManifest) }
        ActionRow(stringResource(R.string.reset_art)) { onAction(UiAction.ResetImage) }
        TextButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun ColorDialog(show: Boolean, selected: String, onDismiss: () -> Unit, onSelected: (String?) -> Unit) {
    val options = listOf(
        "" to stringResource(R.string.ui_color_auto),
        "black" to stringResource(R.string.ui_color_black),
        "silver" to stringResource(R.string.ui_color_silver),
        "blue" to stringResource(R.string.ui_color_blue),
        "gold" to stringResource(R.string.ui_color_champagne),
        "violet" to stringResource(R.string.ui_color_violet),
    )
    OverlayDialog(show = show, title = stringResource(R.string.ui_art_color), onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp)) {
        options.forEach { (key, label) ->
            BasicComponent(title = label,
                modifier = Modifier.testTag("color_$key").clickable { onSelected(key.ifBlank { null }) },
                endActions = { if (selected.equals(key, true)) Icon(MiuixIcons.Ok, contentDescription = null) })
        }
        TextButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun DevicePickerDialog(state: TechnicsPodsUiState, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    OverlayDialog(show = state.devicePickerOpen, title = stringResource(R.string.device_chooser_title),
        modifier = Modifier.widthIn(max = 520.dp), onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            item { BasicComponent(title = stringResource(R.string.device_automatic_unique),
                modifier = Modifier.clickable { onSelected("") },
                endActions = { if (state.selectedAddress.isEmpty()) Icon(MiuixIcons.Ok, contentDescription = null) }) }
            if (state.deviceChoices.isEmpty()) item { InfoRow(stringResource(R.string.ui_no_devices), stringResource(R.string.ui_pair_device)) }
            state.deviceChoices.forEach { device ->
                item(key = device.address) { BasicComponent(title = device.name, summary = device.address,
                    modifier = Modifier.clickable { onSelected(device.address) }, endActions = {
                        if (state.selectedAddress.equals(device.address, true)) Icon(MiuixIcons.Ok, contentDescription = null)
                    }) }
            }
        }
        TextButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun appearanceName(appearance: AppAppearance) = stringResource(when (appearance) {
    AppAppearance.System -> R.string.ui_theme_system
    AppAppearance.Light -> R.string.ui_theme_light
    AppAppearance.Dark -> R.string.ui_theme_dark
})

@Composable
private fun freshness(field: ObservedValue<*>, state: TechnicsPodsUiState): String = stringResource(when {
    field.value == null || field.updatedAt == 0L -> R.string.state_unknown
    state.reachable && Az100StateFreshness.isFresh(field.updatedAt, state.now) -> R.string.state_recent
    else -> R.string.state_cached
})

@Composable
private fun connectionText(state: TechnicsPodsUiState) = stringResource(when {
    state.loading -> R.string.ui_loading
    state.stateError.isNotBlank() -> R.string.ui_state_unavailable
    !state.bluetoothGranted -> R.string.permission_bluetooth_missing
    !state.bluetoothEnabled -> R.string.ui_bluetooth_off
    else -> when (state.connectionStatus) {
        "reading" -> R.string.state_reading
        "controlling" -> R.string.state_controlling
        "official" -> R.string.state_official
        "capture" -> R.string.state_capture
        "idle" -> if (state.reachable) R.string.state_ready else R.string.state_connected_pending
        else -> R.string.state_disconnected
    }
})

@Composable
private fun controlStatus(state: TechnicsPodsUiState) = stringResource(when (state.controlStatus) {
    "pending", "accepted" -> R.string.ui_control_pending
    "success", "succeeded", "confirmed" -> R.string.ui_control_success
    "failed", "failure", "rejected", "cancelled" -> R.string.ui_control_failed
    "busy" -> R.string.ui_control_busy
    else -> R.string.ui_control_idle
}) + state.controlError.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()

@Composable
private fun capabilityStatus(status: String) = stringResource(when (status) {
    "installed" -> R.string.capability_installed
    "degraded" -> R.string.capability_degraded
    else -> R.string.capability_unavailable
})

@Composable
private fun artworkSource(state: TechnicsPodsUiState): String {
    if (state.artwork.busy) return stringResource(R.string.art_working)
    val source = stringResource(when (state.artwork.source) {
        "official" -> R.string.ui_art_official
        "manual" -> R.string.art_imported
        "bundled" -> R.string.ui_art_bundled
        else -> R.string.ui_art_placeholder
    })
    val color = when (state.artwork.color.lowercase()) {
        "black" -> stringResource(R.string.ui_color_black)
        "silver" -> stringResource(R.string.ui_color_silver)
        "blue" -> stringResource(R.string.ui_color_blue)
        "champagne", "gold" -> stringResource(R.string.ui_color_champagne)
        "violet" -> stringResource(R.string.ui_color_violet)
        "unspecified", "unknown" -> ""
        else -> state.artwork.color
    }
    return listOf(source, color, state.artwork.sourceVersion).filter { it.isNotBlank() }.joinToString(" · ")
}
