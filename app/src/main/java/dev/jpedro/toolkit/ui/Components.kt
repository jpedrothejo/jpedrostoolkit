package dev.jpedro.toolkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jpedro.toolkit.ToolkitViewModel
import dev.jpedro.toolkit.core.Target
import dev.jpedro.toolkit.data.ThemeName

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun PageColumn(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Base.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPalette.current
    val opacity = LocalCardOpacity.current
    val cardShape = shape()
    val themeName = LocalThemeName.current
    val isMaterialTheme = themeName == ThemeName.MATERIAL_EXPRESSIVE

    val backgroundModifier = if (isMaterialTheme) {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), cardShape)
    } else {
        Modifier
            .background(Color.Black.copy(alpha = 0.22f * opacity))
            .background(palette.primary.copy(alpha = (palette.primary.alpha * opacity).coerceIn(0f, 1f)))
            .border(1.dp, Base.border, cardShape)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(backgroundModifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isMaterialTheme) MaterialTheme.colorScheme.onSurfaceVariant else Base.text
            )
            HorizontalDivider(color = if (isMaterialTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color(0x14FFFFFF))
        }
        content()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = Base.muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun SiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val container = if (danger) Base.danger else LocalPalette.current.button
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape(),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusBadge(text: String, tone: Tone) {
    val dot = when (tone) {
        Tone.GOOD -> Color(0xFF4ADE80)
        Tone.WARN -> Color(0xFFFACC15)
        Tone.BAD -> Color(0xFFF87171)
        Tone.NEUTRAL -> Color(0xFF9CA3AF)
    }
    val badgeShape = shape(2f)
    Row(
        Modifier
            .clip(badgeShape)
            .background(Base.field)
            .border(1.dp, Base.border, badgeShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = Base.muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (description != null) Muted(description)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.link.copy(alpha = 0.55f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SliderSetting(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    val palette = LocalPalette.current
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(valueText, color = palette.link, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onChange(it)
            },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = palette.link,
                activeTrackColor = palette.link,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
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
}

@Composable
fun LogView(lines: List<String>, fontSize: Int, maxHeight: Dp = 320.dp) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = maxHeight)
            .clip(shape(0.7f))
            .background(Color(0xCC0B0D12))
            .border(1.dp, Base.border, shape(0.7f))
            .padding(10.dp)
    ) {
        if (lines.isEmpty()) {
            Text("No output yet", color = Base.muted, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(state = listState) {
            items(lines.size) { index ->
                Text(
                    text = lines[index],
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 4).sp
                )
            }
        }
    }
}

@Composable
fun LogCard(title: String, lines: List<String>, fontSize: Int, onClear: () -> Unit, maxHeight: Dp = 320.dp) {
    val clipboard = LocalClipboardManager.current
    GlassCard(title = title) {
        LogView(lines, fontSize, maxHeight)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SiteButton("Copy", { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }, enabled = lines.isNotEmpty())
            SiteButton("Clear", onClear, enabled = lines.isNotEmpty())
        }
    }
}

data class Pending(val title: String, val message: String, val confirmText: String, val action: () -> Unit)

class ConfirmState {
    var pending by mutableStateOf<Pending?>(null)

    fun ask(enabled: Boolean, title: String, message: String, confirmText: String, action: () -> Unit) {
        if (enabled) pending = Pending(title, message, confirmText, action) else action()
    }
}

@Composable
fun rememberConfirmState(): ConfirmState = remember { ConfirmState() }

@Composable
fun ConfirmHost(state: ConfirmState) {
    val pending = state.pending ?: return
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = { state.pending = null },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = shape(),
        title = { Text(pending.title) },
        text = { Text(pending.message) },
        confirmButton = {
            SiteButton(pending.confirmText, {
                state.pending = null
                pending.action()
            }, danger = true)
        },
        dismissButton = {
            TextButton(onClick = { state.pending = null }) { Text("Cancel", color = palette.link) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetCard(vm: ToolkitViewModel) {
    val palette = LocalPalette.current
    LaunchedEffect(Unit) {
        if (vm.devices.isEmpty()) vm.refreshDevices()
    }
    GlassCard(title = "Target") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val options = listOf(Target.Local) + vm.devices.filter { it.ready }.map { Target(it.serial, it.label) }
            options.forEach { option ->
                FilterChip(
                    selected = vm.target.serial == option.serial,
                    onClick = { vm.selectTarget(option) },
                    label = { Text(option.label) },
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
        vm.devices.filter { !it.ready }.forEach { Muted("${it.serial} is ${it.state}. Accept the debugging prompt on that phone.") }
        vm.deviceNote?.let { Muted(it) }
        SiteButton(
            if (vm.devicesBusy) "Scanning" else "Refresh phones",
            vm::refreshDevices,
            enabled = !vm.devicesBusy
        )
    }
}
