package dev.jpedro.toolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel

@Composable
fun FontPage(vm: ToolkitViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    PageColumn("Font & Typography", "Change system font scale and installed fonts via Shizuku / Root") {
        TargetCard(vm)

        GlassCard(title = "Installed Font Family") {
            Text("Select Installed System Font", style = MaterialTheme.typography.titleSmall)
            val fontOptions = listOf("Default") + vm.installedFonts.toList()
            ChoiceRow(
                title = "System Font",
                options = fontOptions,
                selected = vm.selectedFontFamily,
                label = { it },
                onSelect = { vm.applyFontFamily(it) }
            )
            if (settings.showDescriptions) {
                Muted("Queries available font files in /system/fonts/ and applies the font family system-wide using root/Shizuku.")
            }
        }

        GlassCard(title = "System Font Scale") {
            Text("Presets", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("0.85" to "Small", "1.0" to "Normal", "1.15" to "Large", "1.3" to "Huge").forEach { (scale, label) ->
                    SiteButton(
                        text = label,
                        onClick = { vm.applyFontScale(scale) },
                        enabled = !vm.fontBusy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Custom Scale: ${vm.fontScale}x", style = MaterialTheme.typography.titleSmall)
            SliderSetting(
                title = "Scale",
                valueText = "${vm.fontScale}x",
                value = vm.fontScale.toFloatOrNull() ?: 1.0f,
                range = 0.5f..2.0f,
                onChange = { vm.fontScale = String.format(java.util.Locale.US, "%.2f", it) }
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteButton(
                    text = "Apply Custom Scale",
                    onClick = { vm.applyFontScale(vm.fontScale) },
                    enabled = !vm.fontBusy,
                    modifier = Modifier.weight(1f)
                )
                SiteButton(
                    text = "Get Current",
                    onClick = { vm.getFontScale() },
                    enabled = !vm.fontBusy
                )
            }
        }

        GlassCard(title = "Quick Typography Commands") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Reset Font Scale")
                    if (settings.showDescriptions) Muted("Restore default system font scale (1.0x)")
                }
                Spacer(Modifier.width(12.dp))
                SiteButton(
                    text = "Reset",
                    onClick = { vm.applyFontScale("1.0") },
                    enabled = !vm.fontBusy
                )
            }
        }

        LogCard("Output", vm.fontLog, settings.terminalFontSize, onClear = { vm.fontLog.clear() }, maxHeight = 220.dp)
    }
}
