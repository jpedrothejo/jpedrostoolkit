package dev.jpedro.toolkit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.jpedro.toolkit.ToolkitViewModel

fun isValidImei(imei: String): Boolean {
    val clean = imei.filter { it.isDigit() }
    if (clean.length != 15) return false
    var sum = 0
    for (i in 0..14) {
        var num = clean[i].digitToInt()
        if (i % 2 == 1) {
            num *= 2
            if (num > 9) num -= 9
        }
        sum += num
    }
    return sum % 10 == 0
}

@Composable
fun ImeiPage(vm: ToolkitViewModel) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var imeiInput by remember { mutableStateOf("") }
    val cleanImei = imeiInput.filter { it.isDigit() }
    val isValid = isValidImei(cleanImei)
    val tac = if (cleanImei.length >= 8) cleanImei.substring(0, 8) else cleanImei

    PageColumn("IMEI Check", "Verify IMEI, model, FRP lock, iCloud lock & blacklist status") {
        TargetCard(vm)

        SiteButton(
            text = if (vm.checkingFrp) "Checking Target Device Lock & FRP..." else "Auto-fill Target IMEI & Check FRP/Lock",
            onClick = {
                val targetImei = if (vm.target.isLocal) vm.deviceImeis.firstOrNull() else null
                if (targetImei != null) {
                    imeiInput = targetImei
                }
                vm.checkTargetFrp()
            },
            enabled = !vm.checkingFrp,
            modifier = Modifier.fillMaxWidth()
        )

        val frp = vm.frpReport
        if (frp != null) {
            GlassCard(title = "Connected Device FRP & Lock Status") {
                val badgeText = when (frp.googleAccountPresent) {
                    true -> "FRP Active (Google Account Registered)" to Tone.WARN
                    false -> "FRP Clear (No Google Account Registered)" to Tone.GOOD
                    null -> "FRP / Account Status Unknown" to Tone.NEUTRAL
                }
                StatusBadge(badgeText.first, badgeText.second)
                InfoRow("Target Phone", frp.targetLabel)
                InfoRow("Google Account", when (frp.googleAccountPresent) {
                    true -> "Present (Factory Reset Protection Enabled)"
                    false -> "None Found"
                    null -> "Not Reported / Access Needed"
                })
                InfoRow("OEM Unlock Allowed", when (frp.oemUnlockAllowed) {
                    true -> "Yes (1)"
                    false -> "No (0)"
                    null -> "Not Reported"
                })
                InfoRow("Bootloader Lock", when (frp.bootloaderLocked) {
                    true -> "Locked (1)"
                    false -> "Unlocked (0)"
                    null -> "Not Reported"
                })
            }
        }

        GlassCard(title = "Enter IMEI") {
            OutlinedTextField(
                value = imeiInput,
                onValueChange = { if (it.length <= 15) imeiInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("15-digit IMEI number") },
                shape = shape(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.link,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    cursorColor = palette.link
                )
            )
            Text("${cleanImei.length} / 15 digits", color = Base.muted)
        }

        if (cleanImei.isNotEmpty()) {
            GlassCard(title = "Analysis & Validation") {
                StatusBadge(
                    text = if (isValid) "Valid IMEI (Luhn checksum passed)" else "Invalid IMEI format or checksum",
                    tone = if (isValid) Tone.GOOD else Tone.BAD
                )
                InfoRow("IMEI Number", cleanImei)
                InfoRow("TAC (Type Allocation Code)", tac.ifEmpty { "N/A" })
                InfoRow("Device Type", if (cleanImei.startsWith("35") || cleanImei.startsWith("86") || cleanImei.startsWith("01")) "Cellular Mobile Device" else "Unknown / Other")
                Muted("Note: FRP Lock (Android), iCloud Lock (Apple), and carrier Blacklist/Lost status require online database verification using carrier or manufacturer servers.")
            }

            GlassCard(title = "Online Status Verification") {
                Muted("Tap below to check this IMEI on trusted external lookup services:")
                SiteButton(
                    text = "Check on IMEI.info",
                    onClick = {
                        val url = "https://www.imei.info/check/?imei=$cleanImei"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SiteButton(
                    text = "Check Apple iCloud Activation / Lost Mode",
                    onClick = {
                        val url = "https://sndeep.info/en"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SiteButton(
                    text = "Copy IMEI to Clipboard",
                    onClick = { clipboard.setText(AnnotatedString(cleanImei)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
