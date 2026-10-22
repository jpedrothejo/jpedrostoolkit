package dev.jpedro.toolkit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.data.NavStyle
import dev.jpedro.toolkit.data.NavPosition

enum class Page(val title: String, val emoji: String, val description: String, val tool: Boolean) {
    HOME("Home", "🏠", "Access and connected phones", false),
    DEVICE("Device", "🔍", "Check root, Knox and basic info on this or a connected phone", true),
    IMEI("IMEI Check", "🪪", "Check FRP, iCloud lock, blacklist status and model via IMEI", true),
    FLASH("Flash", "⚡", "Flash Samsung firmware to a phone in Download mode with odin4", true),
    INSTALL("Install", "📦", "Install one or many APKs, or a split set", true),
    POWER("Power", "🔌", "Reboot, recovery, Download mode, bootloader and shutdown", true),
    SCREENTEST("Screen Test", "📱", "Test screen touch response, stuck pixels and burn-in", true),
    WATEREJECT("Water Eject", "💧", "Eject water from speakers using 165Hz sound waves & haptics", true),
    TERMINAL("Terminal", "💻", "Run shell and adb commands", true),
    SETTINGS("Settings", "⚙️", "Customize the app", false),
    SOFTWARE_UPDATE("Software Update", "📲", "Software update in One UI 9 style", false)
}

const val CURRENT_APP_VERSION = "b1.2.1"

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape(),
        title = { Text("What's New in JpedroToolkit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("b1.2", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.link)
                    Text("• Updater tab")
                    Text("• Customizable update channels (Beta / Stable) & download sources")
                    Text("• App Debloat & Package Manager with batch uninstallation")
                    Text("• Coroutine & shell performance optimizations")
                    Text("• Speaker Water Ejector using 165Hz sound wave generator & haptics")
                    Text("• Adjustable frequency slider & haptic vibration assist")
                    Text("• Distinct tab icon 💧")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("b1.1.2", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.link)
                    Text("• Add this screen after every update :3")
                    Text("• Dynamic Material You theme fixes")
                    Text("• Tab Bar styling & position improvements")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("b1.1.1", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.link)
                    Text("• Dual-SIM IMEI detection & Luhn validation")
                    Text("• Live Google FRP Lock & OEM Unlock status check")
                    Text("• Dedicated IMEI tab with 🪪 icon & Android Easter Egg shortcut")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("b1.1", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.link)
                    Text("• Shizuku & Root command execution engine")
                    Text("• Samsung odin4 firmware flashing (BL, AP, CP, CSC, USERDATA)")
                    Text("• Split APK installer & local package installation")
                    Text("• Terminal with command history & target device selection")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("b1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.link)
                    Text("• Make the app")
                }
            }
        },
        confirmButton = {
            SiteButton("Got it", onDismiss)
        }
    )
}

@Composable
fun ToolkitRoot(vm: ToolkitViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    val view = LocalView.current
    val keepOn = settings.keepAwakeWhileFlashing && vm.flashRunning

    var showWelcome by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.lastVersion, settings.firstRun) {
        if (settings.firstRun) {
            showWelcome = true
            vm.updateLastVersion(CURRENT_APP_VERSION)
        } else if (settings.lastVersion != CURRENT_APP_VERSION) {
            vm.openChangelog()
        }
    }

    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = page != Page.HOME) {
        if (page == Page.SOFTWARE_UPDATE) {
            page = Page.SETTINGS
        } else {
            page = Page.HOME
        }
    }

    ToolkitTheme(settings) {
        AppBackground(settings) {
            AnimatedVisibility(
                visible = page == Page.SOFTWARE_UPDATE,
                enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(350)) { it / 3 },
                exit = fadeOut(tween(250)) + slideOutVertically(animationSpec = tween(250)) { it / 3 }
            ) {
                SoftwareUpdatePage(vm) { page = Page.SETTINGS }
            }
            if (page != Page.SOFTWARE_UPDATE) {
                if (vm.showChangelogDialog) {
                    ChangelogDialog(onDismiss = { vm.dismissChangelog(CURRENT_APP_VERSION) })
                }
                if (showWelcome) {
                    AlertDialog(
                        onDismissRequest = {
                            showWelcome = false
                            vm.dismissFirstRun()
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface,
                        shape = shape(),
                        title = { Text("Welcome to jpedro's Toolkit!") },
                        text = {
                            Text(
                                "To use most features (such as flashing Samsung firmware, running adb/shell commands, and installing APKs), please:\n\n" +
                                "1. Import arm64 binaries for odin4, adb, and fastboot in Settings under Binaries.\n" +
                                "2. Have Root or Shizuku access granted."
                            )
                        },
                        confirmButton = {
                            SiteButton("Got it", {
                                showWelcome = false
                                vm.dismissFirstRun()
                            })
                        }
                    )
                }
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    if (settings.navPosition == NavPosition.TOP) TopNav(page, settings.navStyle) { page = it }
                    Crossfade(
                        targetState = page,
                        animationSpec = if (settings.animations) tween(220) else snap(),
                        label = "page",
                        modifier = Modifier.weight(1f)
                    ) { current ->
                        when (current) {
                            Page.HOME -> HomePage(vm) { page = it }
                            Page.DEVICE -> DevicePage(vm)
                            Page.IMEI -> ImeiPage(vm)
                            Page.FLASH -> FlashPage(vm)
                            Page.INSTALL -> InstallPage(vm)
                            Page.POWER -> PowerPage(vm)
                            Page.SCREENTEST -> ScreenTestPage(vm)
                            Page.WATEREJECT -> WaterEjectPage(vm)
                            Page.TERMINAL -> TerminalPage(vm)
                            Page.SETTINGS -> SettingsPage(vm) { page = it }
                            Page.SOFTWARE_UPDATE -> SoftwareUpdatePage(vm) { page = Page.SETTINGS }
                        }
                    }
                    if (settings.navPosition == NavPosition.BOTTOM) {
                        TopNav(page, settings.navStyle, modifier = Modifier.navigationBarsPadding().padding(bottom = 6.dp)) { page = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopNav(current: Page, style: NavStyle, modifier: Modifier = Modifier, onSelect: (Page) -> Unit) {
    val palette = LocalPalette.current
    val navShape = shape()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .widthIn(max = 560.dp)
                .clip(navShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .background(palette.primary)
                .border(1.dp, Base.border, navShape)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Page.values().filter { it != Page.SOFTWARE_UPDATE }.forEach { page ->
                val selected = page == current
                val label = if (style == NavStyle.ICONS || page == Page.HOME) page.emoji else page.title
                Box(
                    Modifier
                        .clip(shape(0.7f))
                        .background(if (selected) palette.button else Color.Transparent)
                        .clickable { onSelect(page) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}
