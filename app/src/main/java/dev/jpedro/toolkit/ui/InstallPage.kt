package dev.jpedro.toolkit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel

@Composable
fun InstallPage(vm: ToolkitViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addApks(uris)
    }

    PageColumn("Install", "Install APKs on this phone or a connected one") {
        TargetCard(vm)

        GlassCard(title = "APK files") {
            if (vm.apks.isEmpty()) Muted("No files selected")
            vm.apks.toList().forEach { file ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = file.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SiteButton("Remove", { vm.removeApk(file) }, enabled = !vm.installRunning)
                }
            }
            SiteButton("Add APKs", { picker.launch(arrayOf("*/*")) }, enabled = !vm.installRunning)
        }

        GlassCard(title = "Options") {
            SwitchSetting(
                title = "Install as one split set",
                description = "For base and split APKs of the same app",
                checked = vm.installSplit,
                onChange = { vm.installSplit = it }
            )
            SwitchSetting(
                title = "Allow downgrade",
                checked = vm.installDowngrade,
                onChange = { vm.installDowngrade = it }
            )
            SwitchSetting(
                title = "Grant all permissions",
                checked = vm.installGrant,
                onChange = { vm.installGrant = it }
            )
        }

        SiteButton(
            text = if (vm.installRunning) "Installing" else "Install on ${vm.target.label}",
            onClick = vm::install,
            enabled = vm.apks.isNotEmpty() && !vm.installRunning,
            modifier = Modifier.fillMaxWidth()
        )

        LogCard("Output", vm.installLog, settings.terminalFontSize, onClear = { vm.installLog.clear() })
    }
}
