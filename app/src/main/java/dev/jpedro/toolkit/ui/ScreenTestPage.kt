package dev.jpedro.toolkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jpedro.toolkit.ToolkitViewModel

enum class ScreenTestMode {
    MENU, STUCK_PIXEL, TOUCH_TEST
}

@Composable
fun ScreenTestPage(vm: ToolkitViewModel) {
    var mode by remember { mutableStateOf(ScreenTestMode.MENU) }
    val settings by vm.settings.collectAsStateWithLifecycle()

    when (mode) {
        ScreenTestMode.MENU -> {
            PageColumn("Screen Test", "Test your display for stuck pixels, burn-in, and touch response") {
                GlassCard(
                    title = "Stuck Pixels & Burn-in Test",
                    onClick = { mode = ScreenTestMode.STUCK_PIXEL }
                ) {
                    Text("Display solid RGB, primary, and grayscale colors fullscreen to easily spot dead pixels or AMOLED burn-in.")
                    if (settings.showDescriptions) {
                        Muted("Tap anywhere during the test to cycle through colors. Long press to toggle controls.")
                    }
                    SiteButton("Start Stuck Pixel Test", { mode = ScreenTestMode.STUCK_PIXEL })
                }

                GlassCard(
                    title = "Touch Response & Multi-touch Test",
                    onClick = { mode = ScreenTestMode.TOUCH_TEST }
                ) {
                    Text("Test touch accuracy, multi-touch tracking, dead zones, and responsiveness across the entire screen.")
                    if (settings.showDescriptions) {
                        Muted("Draw anywhere on the screen. Tracks multiple touch points simultaneously.")
                    }
                    SiteButton("Start Touch Test", { mode = ScreenTestMode.TOUCH_TEST })
                }
            }
        }
        ScreenTestMode.STUCK_PIXEL -> {
            StuckPixelTestView(onExit = { mode = ScreenTestMode.MENU })
        }
        ScreenTestMode.TOUCH_TEST -> {
            TouchTestView(onExit = { mode = ScreenTestMode.MENU })
        }
    }
}

@Composable
fun StuckPixelTestView(onExit: () -> Unit) {
    val colors = listOf(
        Color.White,
        Color.Black,
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Cyan,
        Color.Magenta,
        Color.Yellow,
        Color.Gray
    )
    var colorIndex by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors[colorIndex])
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        colorIndex = (colorIndex + 1) % colors.size
                    },
                    onLongPress = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        if (showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    Modifier
                        .clip(shape())
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Color: ${colorIndex + 1}/${colors.size} (Tap for next, Long press to hide)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SiteButton("Exit", onExit)
                }
            }
        }
    }
}

@Composable
fun TouchTestView(onExit: () -> Unit) {
    var touches by remember { mutableStateOf(mapOf<PointerId, List<Offset>>()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val activeMap = mutableMapOf<PointerId, List<Offset>>()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val oldPath = touches[change.id] ?: emptyList()
                                activeMap[change.id] = if (oldPath.isEmpty()) listOf(change.position) else oldPath + change.position
                            }
                        }
                        touches = activeMap
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 100.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }

            touches.forEach { (_, path) ->
                if (path.size > 1) {
                    val p = Path().apply {
                        moveTo(path.first().x, path.first().y)
                        path.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(p, Color(0xFF3B82F6), style = Stroke(width = 6f))
                }
                path.lastOrNull()?.let { pos ->
                    drawCircle(Color(0xFF60A5FA), radius = 36f, center = pos, style = Stroke(width = 4f))
                    drawCircle(Color(0x663B82F6), radius = 32f, center = pos)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                Modifier
                    .clip(shape())
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Active Touches: ${touches.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                SiteButton("Clear", { touches = emptyMap() })
                SiteButton("Exit", onExit)
            }
        }
    }
}
