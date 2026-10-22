package dev.jpedro.toolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.TerminalMode
import dev.jpedro.toolkit.ToolkitViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalPage(vm: ToolkitViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    var input by remember { mutableStateOf("") }

    fun send() {
        vm.runTerminal(input)
        input = ""
    }

    PageColumn("Terminal", "Runs one command at a time. Type clear to empty the output") {
        TargetCard(vm)

        GlassCard(title = "Terminal") {
            ChoiceRow(
                title = "Mode",
                options = TerminalMode.values().toList(),
                selected = vm.terminalMode,
                label = { it.label },
                onSelect = { vm.terminalMode = it }
            )
            if (vm.terminalMode == TerminalMode.ADB) {
                Muted("Arguments are passed to adb, for example: devices, connect 192.168.1.20:5555, logcat -d")
            } else if (!vm.target.isLocal) {
                Muted("Commands run on ${vm.target.label} through adb shell")
            }
            LogView(vm.terminal, settings.terminalFontSize, maxHeight = 380.dp)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White),
                placeholder = { Text(if (vm.terminalMode == TerminalMode.ADB) "adb arguments" else "command") },
                shape = shape(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.link,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    cursorColor = palette.link
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteButton("Run", ::send, enabled = input.isNotBlank() && !vm.terminalRunning)
                SiteButton("Stop", vm::stopTerminal, enabled = vm.terminalRunning)
                SiteButton("Clear", { vm.terminal.clear() }, enabled = vm.terminal.isNotEmpty())
            }
            if (vm.terminalHistory.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.terminalHistory.toList().forEach { entry ->
                        AssistChip(onClick = { input = entry }, label = { Text(entry, maxLines = 1) })
                    }
                }
            }
        }
    }
}
