package dev.jpedro.toolkit.ui

import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel

@Composable
fun DevicePage(vm: ToolkitViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        if (vm.rootReport == null && !vm.checking) vm.runChecks()
        vm.loadImei()
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val imeis = vm.deviceImeis

    val root = vm.rootReport
    val knox = vm.knoxReport
    val props = vm.deviceProps

    PageColumn("Device", "Root, Knox and basic info for the selected phone") {
        TargetCard(vm)
        GlassCard(title = "This phone") {
            InfoRow("Manufacturer", Build.MANUFACTURER.ifBlank { "Unknown" })
            InfoRow("Brand", Build.BRAND.ifBlank { "Unknown" })
            InfoRow("Model", Build.MODEL.ifBlank { "Unknown" })
            InfoRow("Device", Build.DEVICE.ifBlank { "Unknown" })
            if (imeis.isNotEmpty()) {
                imeis.forEachIndexed { index, raw ->
                    val label = if (imeis.size > 1) "IMEI ${index + 1}" else "IMEI"
                    val display = if (settings.hideImei) {
                        if (raw.length > 4) raw.take(6) + "••••••••" else "•••••••••••••••"
                    } else {
                        raw
                    }
                    InfoRow(label, display)
                }
            } else {
                InfoRow("IMEI", vm.imeiStatus)
            }
            InfoRow("Android", Build.VERSION.RELEASE ?: "Unknown")
            InfoRow("SDK", Build.VERSION.SDK_INT.toString())
            InfoRow("Security patch", Build.VERSION.SECURITY_PATCH.ifBlank { "Unknown" })
            InfoRow("Build", Build.DISPLAY.ifBlank { "Unknown" })
            Muted("Basic details, root status and Knox indicators are shown together. Some protected values may only be available with root or on supported Samsung devices.")
        }
        SiteButton(
            text = if (vm.checking) "Checking device, root & Knox" else "Refresh all device checks",
            onClick = vm::runChecks,
            enabled = !vm.checking,
            modifier = Modifier.fillMaxWidth()
        )
        vm.checkError?.let { message ->
            GlassCard { Text(message, color = Base.error) }
        }
        if (root != null) {
            GlassCard(title = "Root") {
                StatusBadge(
                    text = if (root.granted) "Rooted, root access granted" else "Root access not granted",
                    tone = if (root.granted) Tone.GOOD else Tone.BAD
                )
                InfoRow("Root manager app", root.managers.joinToString().ifEmpty { "None found" })
                if (root.traces.isNotEmpty()) {
                    InfoRow("Found in /data/adb", root.traces.joinToString { it.substringAfterLast('/') })
                }
                if (!root.granted && root.managers.isNotEmpty()) {
                    Muted("A root manager is installed but did not grant root. Open it, allow access, then run the checks again.")
                }
            }
        }
        if (knox != null) {
            GlassCard(title = "Knox") {
                val badge = when (knox.tripped) {
                    true -> "Knox tripped" to Tone.BAD
                    false -> "Knox intact" to Tone.GOOD
                    null -> "Knox status not reported" to Tone.NEUTRAL
                }
                StatusBadge(badge.first, badge.second)
                InfoRow("Warranty bit", knox.warrantyBit ?: "Not reported")
                InfoRow("EM status", knox.emStatus ?: "Not reported")
                InfoRow("Bootloader locked", knox.bootloaderLocked ?: "Not reported")
                InfoRow("Verified boot", knox.verifiedBoot ?: "Not reported")
                InfoRow("Knox version", knox.knoxVersion ?: "Not reported")
                if (!knox.samsung) Muted("This phone does not report Samsung as manufacturer, so Knox values may be missing.")
            }
        }
        if (props.isNotEmpty()) {
            GlassCard(title = "Info") {
                InfoRow("Manufacturer", props["ro.product.manufacturer"].orEmpty().ifEmpty { "Unknown" })
                InfoRow("Model", props["ro.product.model"].orEmpty().ifEmpty { "Unknown" })
                InfoRow("Android", props["ro.build.version.release"].orEmpty().ifEmpty { "Unknown" })
                InfoRow("Security patch", props["ro.build.version.security_patch"].orEmpty().ifEmpty { "Unknown" })
                InfoRow("Bootloader", props["ro.bootloader"].orEmpty().ifEmpty { "Unknown" })
            }
        }

        val context = LocalContext.current
        var showEasterEgg by remember { mutableStateOf(false) }

        if (showEasterEgg) {
            AlertDialog(
                onDismissRequest = { showEasterEgg = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = shape(),
                title = { Text("🐣 Easter Egg") },
                text = { Text("Android Easter Egg (com.android.egg) is not installed or available on this device.") },
                confirmButton = {
                    SiteButton("OK", onClick = { showEasterEgg = false })
                }
            )
        }

        var showDebloat by remember { mutableStateOf(false) }

        if (showDebloat) {
            DebloatDialog(vm = vm, onDismiss = { showDebloat = false })
        }

        SiteButton(
            text = "Debloat & App Manager",
            onClick = {
                vm.loadInstalledApps()
                showDebloat = true
            },
            modifier = Modifier.fillMaxWidth()
        )

        SiteButton(
            text = "Open Android Easter Egg",
            onClick = {
                if (!openAndroidEasterEgg(context)) {
                    showEasterEgg = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebloatDialog(vm: ToolkitViewModel, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    val selectedPackages = remember { mutableStateListOf<String>() }

    val filteredApps = vm.installedApps.filter { app ->
        val matchesQuery = searchQuery.isBlank() || app.label.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true)
        val matchesType = when (filterType) {
            "System" -> app.isSystem
            "User" -> !app.isSystem
            else -> true
        }
        matchesQuery && matchesType
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape(),
        title = { Text("Debloat & App Manager (${vm.target.label})") },
        text = {
            val ctx = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps or package name...") },
                    singleLine = true,
                    shape = shape(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.link,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "User", "System").forEach { type ->
                        FilterChip(
                            selected = filterType == type,
                            onClick = { filterType = type },
                            label = { Text(type) },
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

                if (vm.loadingApps) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.link)
                    }
                } else {
                    Text("${filteredApps.size} apps found · ${selectedPackages.size} selected", style = MaterialTheme.typography.bodySmall, color = Base.muted)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredApps.forEach { app ->
                            val isChecked = app.packageName in selectedPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape(0.6f))
                                    .clickable {
                                        if (isChecked) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Base.muted)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SiteButton(
                                        text = "Open",
                                        onClick = { vm.openApp(app.packageName, ctx) }
                                    )
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPackages.add(app.packageName)
                                            else selectedPackages.remove(app.packageName)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = palette.link)
                                    )
                                }
                            }
                        }
                    }
                }

                if (vm.debloatLog.isNotEmpty()) {
                    LogView(vm.debloatLog, settings.terminalFontSize, maxHeight = 100.dp)
                }
            }
        },
        confirmButton = {
            SiteButton(
                text = if (vm.debloatRunning) "Uninstalling..." else "Uninstall Selected (${selectedPackages.size})",
                onClick = { vm.uninstallApps(selectedPackages.toList()) },
                enabled = selectedPackages.isNotEmpty() && !vm.debloatRunning,
                danger = true
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = palette.link) }
        }
    )
}

private fun openAndroidEasterEgg(context: android.content.Context): Boolean {
    val pm = context.packageManager
    val packages = listOf("com.android.egg", "com.google.android.egg")
    for (pkg in packages) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
                return true
            } catch (_: Exception) {
                // continue
            }
        }
        try {
            val pkgInfo = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES)
            val activities = pkgInfo.activities
            if (activities != null) {
                val targetActivity = activities.firstOrNull { it.name.contains("PlatLogo", ignoreCase = true) || it.name.contains("Egg", ignoreCase = true) }
                    ?: activities.firstOrNull { it.exported }
                if (targetActivity != null) {
                    val intent = android.content.Intent().setClassName(pkg, targetActivity.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            }
        } catch (_: Exception) {
            // continue
        }
    }
    return false
}
