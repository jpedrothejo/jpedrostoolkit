package dev.jpedro.toolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.core.PowerAction

@Composable
fun PowerPage(vm: ToolkitViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val backend by vm.activeBackend.collectAsStateWithLifecycle()
    val confirm = rememberConfirmState()

    PageColumn("Power", "Reboot, boot modes and shutdown") {
        TargetCard(vm)

        GlassCard(title = "Actions for ${vm.target.label}") {
            PowerAction.values().forEach { action ->
                val available = backend != null && vm.powerCommandFor(action) != null
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(action.label)
                        if (settings.showDescriptions) Muted(action.description)
                    }
                    Spacer(Modifier.width(12.dp))
                    SiteButton(
                        text = "Run",
                        onClick = {
                            confirm.ask(
                                enabled = settings.confirmDangerous,
                                title = "${action.label} ${vm.target.label}?",
                                message = "The phone will restart or power off right away.",
                                confirmText = action.label,
                                action = { vm.power(action) }
                            )
                        },
                        enabled = available && !vm.powerBusy,
                        danger = action == PowerAction.SHUTDOWN
                    )
                }
            }
        }

        LogCard("Output", vm.powerLog, settings.terminalFontSize, onClear = { vm.powerLog.clear() }, maxHeight = 200.dp)
    }
    ConfirmHost(confirm)
}
