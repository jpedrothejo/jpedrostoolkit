package dev.jpedro.toolkit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.core.RootState
import dev.jpedro.toolkit.core.SHIZUKU_PACKAGE
import dev.jpedro.toolkit.core.ShizukuState

@Composable
fun HomePage(vm: ToolkitViewModel, navigate: (Page) -> Unit) {
    val context = LocalContext.current
    val shizuku by vm.access.shizuku.collectAsStateWithLifecycle()
    val root by vm.access.root.collectAsStateWithLifecycle()
    val backend by vm.activeBackend.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val shizukuText = when (shizuku) {
        ShizukuState.MISSING -> "Shizuku not installed"
        ShizukuState.NOT_RUNNING -> "Shizuku not running"
        ShizukuState.NEEDS_PERMISSION -> "Shizuku permission needed"
        ShizukuState.GRANTED -> "Shizuku granted"
    }
    val shizukuTone = when (shizuku) {
        ShizukuState.MISSING -> Tone.BAD
        ShizukuState.NOT_RUNNING, ShizukuState.NEEDS_PERMISSION -> Tone.WARN
        ShizukuState.GRANTED -> Tone.GOOD
    }
    val shizukuButton = when (shizuku) {
        ShizukuState.MISSING -> "Get Shizuku"
        ShizukuState.NOT_RUNNING -> "Open Shizuku"
        ShizukuState.NEEDS_PERMISSION -> "Request"
        ShizukuState.GRANTED -> "Refresh"
    }
    val rootText = when (root) {
        RootState.UNKNOWN -> "Root not requested"
        RootState.DENIED -> "Root denied or unavailable"
        RootState.GRANTED -> "Root granted"
    }
    val rootTone = when (root) {
        RootState.UNKNOWN -> Tone.NEUTRAL
        RootState.DENIED -> Tone.BAD
        RootState.GRANTED -> Tone.GOOD
    }

    fun onShizukuClick() {
        when (shizuku) {
            ShizukuState.MISSING ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            ShizukuState.NOT_RUNNING ->
                context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { context.startActivity(it) }
            ShizukuState.NEEDS_PERMISSION -> vm.access.requestShizuku()
            ShizukuState.GRANTED -> vm.access.refreshShizuku()
        }
    }

    PageColumn("jpedro's Toolkit", "Root, flash and manage Android phones") {
        GlassCard(title = "Access") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(shizukuText, shizukuTone)
                SiteButton(shizukuButton, ::onShizukuClick)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(rootText, rootTone)
                SiteButton("Request root", vm::requestRoot)
            }
            StatusBadge(
                text = backend?.let { "Running commands with ${it.label}" } ?: "No access yet, tools are disabled",
                tone = if (backend == null) Tone.WARN else Tone.GOOD
            )
            if (settings.showDescriptions) {
                Muted("Shizuku has to be started first (wireless debugging or root). If the permission prompt never shows, allow this app inside Shizuku.")
            }
        }

        TargetCard(vm)

        Page.values().filter { it.tool }.forEach { page ->
            GlassCard(onClick = { navigate(page) }) {
                Text("${page.emoji}  ${page.title}", style = MaterialTheme.typography.titleMedium)
                if (settings.showDescriptions) Muted(page.description)
            }
        }
    }
}
