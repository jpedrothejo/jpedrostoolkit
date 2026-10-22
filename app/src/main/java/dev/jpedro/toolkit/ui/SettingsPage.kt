package dev.jpedro.toolkit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.core.BackendPreference
import dev.jpedro.toolkit.core.Tool
import dev.jpedro.toolkit.data.Keys
import dev.jpedro.toolkit.data.NavStyle
import dev.jpedro.toolkit.data.NavPosition
import dev.jpedro.toolkit.data.NavLayout
import dev.jpedro.toolkit.data.ThemeName
import kotlin.math.roundToInt

@Composable
fun SettingsPage(vm: ToolkitViewModel, onNavigate: (Page) -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val confirm = rememberConfirmState()
    var pendingTool by remember { mutableStateOf<Tool?>(null) }

    val toolPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = pendingTool
        if (uri != null && tool != null) vm.importTool(tool, uri)
    }

    LaunchedEffect(Unit) { vm.refreshTools() }

    PageColumn("Settings", "Customize the app") {
        GlassCard(title = "Appearance") {
            ChoiceRow(
                title = "App theme",
                options = ThemeName.values().toList(),
                selected = settings.theme,
                label = { it.label },
                onSelect = { vm.set(Keys.THEME, it.name) }
            )
            SliderSetting(
                title = "Roundness",
                valueText = "${settings.roundness.roundToInt()} dp",
                value = settings.roundness,
                range = 0f..28f
            ) { vm.set(Keys.ROUNDNESS, it) }
            SliderSetting(
                title = "Card opacity",
                valueText = "${(settings.cardOpacity * 100).roundToInt()}%",
                value = settings.cardOpacity,
                range = 0.2f..1f
            ) { vm.set(Keys.CARD_OPACITY, it) }
            ChoiceRow(
                title = "Tab Bar style",
                options = NavStyle.values().toList(),
                selected = settings.navStyle,
                label = { it.label },
                onSelect = { vm.set(Keys.NAV_STYLE, it.name) }
            )
            ChoiceRow(
                title = "Tab Bar position",
                options = NavPosition.values().toList(),
                selected = settings.navPosition,
                label = { it.label },
                onSelect = { vm.set(Keys.NAV_POSITION, it.name) }
            )
            ChoiceRow(
                title = "Tab bar layout",
                options = NavLayout.values().toList(),
                selected = settings.navLayout,
                label = { it.label },
                onSelect = { vm.set(Keys.NAV_LAYOUT, it.name) }
            )
            SwitchSetting("Bold text", settings.boldText, { vm.set(Keys.BOLD_TEXT, it) })
            SwitchSetting("Page animations", settings.animations, { vm.set(Keys.ANIMATIONS, it) })
            SwitchSetting(
                title = "Show descriptions",
                description = "Hints under tools and actions",
                checked = settings.showDescriptions,
                onChange = { vm.set(Keys.SHOW_DESCRIPTIONS, it) }
            )
            SwitchSetting(
                title = "Hide IMEI",
                description = "Mask IMEI numbers across the app",
                checked = settings.hideImei,
                onChange = { vm.set(Keys.HIDE_IMEI, it) }
            )
        }

        GlassCard(title = "Access") {
            ChoiceRow(
                title = "Run commands with",
                options = BackendPreference.values().toList(),
                selected = settings.backend,
                label = { it.label },
                onSelect = { vm.set(Keys.BACKEND, it.name) }
            )
            SwitchSetting(
                title = "Confirm risky actions",
                description = "Ask before flashing, rebooting and shutting down",
                checked = settings.confirmDangerous,
                onChange = { vm.set(Keys.CONFIRM, it) }
            )
        }

        GlassCard(title = "Flashing") {
            SwitchSetting("Reboot after flashing", settings.rebootAfterFlash, { vm.set(Keys.REBOOT_AFTER_FLASH, it) })
            SwitchSetting("Validate binaries (-V)", settings.validateBinaries, { vm.set(Keys.VALIDATE, it) })
            SwitchSetting("Keep screen on while flashing", settings.keepAwakeWhileFlashing, { vm.set(Keys.KEEP_AWAKE, it) })
        }

        GlassCard(title = "Terminal and logs") {
            SliderSetting(
                title = "Font size",
                valueText = "${settings.terminalFontSize} sp",
                value = settings.terminalFontSize.toFloat(),
                range = 10f..20f,
                steps = 9
            ) { vm.set(Keys.TERM_FONT, it.roundToInt()) }
            SliderSetting(
                title = "Lines kept in terminal",
                valueText = "${settings.terminalMaxLines}",
                value = settings.terminalMaxLines.toFloat(),
                range = 200f..5000f
            ) { vm.set(Keys.TERM_LINES, (it / 100f).roundToInt() * 100) }
        }

        GlassCard(title = "Binaries") {
            Muted("Import arm64 Android builds of these tools. They are copied to a folder only root and Shizuku can use.")
            Tool.values().forEach { tool ->
                val installed = vm.toolStatus[tool] == true
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tool.label)
                            if (settings.showDescriptions) Muted(tool.purpose)
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(if (installed) "Imported" else "Missing", if (installed) Tone.GOOD else Tone.NEUTRAL)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiteButton(
                            text = if (installed) "Replace" else "Import",
                            onClick = {
                                pendingTool = tool
                                toolPicker.launch(arrayOf("*/*"))
                            },
                            enabled = !vm.toolBusy
                        )
                        SiteButton("Remove", { vm.removeTool(tool) }, enabled = installed && !vm.toolBusy)
                    }
                }
            }
        }

        GlassCard(title = "About & Updates") {
            SiteButton(
                text = "Software update",
                onClick = { onNavigate(Page.SOFTWARE_UPDATE) },
                modifier = Modifier.fillMaxWidth()
            )
            SiteButton(
                text = "What's New?",
                onClick = vm::openChangelog,
                modifier = Modifier.fillMaxWidth()
            )
        }

        GlassCard(title = "Reset") {
            SiteButton(
                text = "Reset all settings",
                onClick = {
                    confirm.ask(
                        enabled = true,
                        title = "Reset all settings?",
                        message = "Theme, wallpaper and every option return to their defaults. Imported binaries stay.",
                        confirmText = "Reset",
                        action = vm::resetSettings
                    )
                },
                danger = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    ConfirmHost(confirm)
}
