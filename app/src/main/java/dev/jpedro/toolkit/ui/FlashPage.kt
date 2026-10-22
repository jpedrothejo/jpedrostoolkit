package dev.jpedro.toolkit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.OdinSlot
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.core.Tool
import dev.jpedro.toolkit.data.Keys

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashPage(vm: ToolkitViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val confirm = rememberConfirmState()
    var pendingSlot by remember { mutableStateOf<OdinSlot?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingSlot
        if (uri != null && slot != null) vm.setSlot(slot, uri)
    }

    LaunchedEffect(Unit) { vm.refreshTools() }

    val odinReady = vm.toolStatus[Tool.ODIN] == true
    val canFlash = odinReady && vm.slots.isNotEmpty() && !vm.flashRunning

    PageColumn("Flash", "Flash Samsung firmware to another phone with odin4") {
        GlassCard(title = "Before you start") {
            Muted("Put the other phone in Download mode and connect it with a USB OTG cable. odin4 needs raw USB access, which normally means root. Flashing the wrong files can brick the phone.")
            StatusBadge(
                text = if (odinReady) "odin4 ready" else "odin4 not imported, add it in Settings under Binaries",
                tone = if (odinReady) Tone.GOOD else Tone.WARN
            )
        }

        GlassCard(title = "Firmware files") {
            OdinSlot.values().forEach { slot ->
                val file = vm.slots[slot]
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(slot.label, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                    Text(
                        text = file?.name ?: "Not selected",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (file == null) Base.muted else Color.Unspecified,
                        style = MaterialTheme.typography.bodySmall
                    )
                    SiteButton(
                        text = if (file == null) "Pick" else "Clear",
                        onClick = {
                            if (file == null) {
                                pendingSlot = slot
                                picker.launch(arrayOf("*/*"))
                            } else {
                                vm.clearSlot(slot)
                            }
                        },
                        enabled = !vm.flashRunning
                    )
                }
            }
        }

        GlassCard(title = "Phone in Download mode") {
            if (vm.odinDevices.isEmpty()) {
                Muted("Scan to find connected phones. With a single phone odin4 picks it automatically.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.odinDevices.forEach { path ->
                        FilterChip(
                            selected = vm.odinDevice == path,
                            onClick = { vm.odinDevice = path },
                            label = { Text(path) },
                            shape = shape(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.button,
                                selectedLabelColor = Color.White,
                                containerColor = Base.field,
                                labelColor = Base.text
                            )
                        )
                    }
                }
            }
            SiteButton(
                text = if (vm.scanning) "Scanning" else "Scan for phones",
                onClick = vm::scanOdin,
                enabled = odinReady && !vm.scanning && !vm.flashRunning
            )
        }

        GlassCard(title = "Options") {
            SwitchSetting(
                title = "Reboot after flashing",
                checked = settings.rebootAfterFlash,
                onChange = { vm.set(Keys.REBOOT_AFTER_FLASH, it) }
            )
            SwitchSetting(
                title = "Validate binaries",
                description = "Runs odin4's home binary validation check (-V)",
                checked = settings.validateBinaries,
                onChange = { vm.set(Keys.VALIDATE, it) }
            )
            SwitchSetting(
                title = "Erase userdata",
                description = "Wipes the phone's data (-e)",
                checked = vm.eraseUserdata,
                onChange = { vm.eraseUserdata = it }
            )
        }

        SiteButton(
            text = if (vm.flashRunning) "Flashing" else "Start flashing",
            onClick = {
                confirm.ask(
                    enabled = settings.confirmDangerous,
                    title = "Start flashing?",
                    message = "Do not unplug the phone or close the app until it finishes.",
                    confirmText = "Flash",
                    action = vm::flash
                )
            },
            enabled = canFlash,
            danger = true,
            modifier = Modifier.fillMaxWidth()
        )

        LogCard("Output", vm.flashLog, settings.terminalFontSize, onClear = { vm.flashLog.clear() })
    }
    ConfirmHost(confirm)
}
