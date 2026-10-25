package fit.silent.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fit.silent.app.R
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ text & icons

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Nd.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Space Mono, all caps, secondary — the "instrument panel" label. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Nd.colors.textSecondary) =
    Txt(text.uppercase(), Nd.type.label, modifier, color, maxLines = 1)

@Composable
fun Icon(@DrawableRes res: Int, modifier: Modifier = Modifier, tint: Color = Nd.colors.textPrimary, size: Dp = 20.dp, description: String? = null) {
    Image(
        painter = painterResource(res),
        contentDescription = description,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Bracketed inline status, e.g. `[SAVED]`, `[ERROR: …]`. Replaces toasts. */
@Composable
fun InlineStatus(text: String, modifier: Modifier = Modifier, color: Color = Nd.colors.textSecondary) =
    Txt("[${text.uppercase()}]", Nd.type.caption, modifier, color, maxLines = 2)

// ------------------------------------------------------------------ layout

/** Top-level page: oversized light title, generous breathing room, scroll handled by caller. */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.lg)) {
        if (eyebrow != null) {
            Label(eyebrow)
            Spacer(Modifier.height(Space.sm))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, Nd.type.displayMd, Modifier.weight(1f), Nd.colors.textDisplay, maxLines = 1)
            trailing?.invoke(this)
        }
    }
}

/** Sub-page header: circular back button and a compact title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(R.drawable.ic_chevron_left, "Back", onBack)
        Spacer(Modifier.width(Space.md))
        Txt(title, Nd.type.subheading, Modifier.weight(1f), Nd.colors.textDisplay, maxLines = 1)
        trailing?.invoke(this)
    }
}

@Composable
fun CircleButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Nd.colors.textPrimary, enabled: Boolean = true) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Nd.colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint = if (enabled) tint else Nd.colors.textDisabled) }
}

@Composable
fun Section(title: String?, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = Space.xl)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Label(title, Modifier.weight(1f))
                trailing?.invoke(this)
            }
        }
        content()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(Space.md), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Nd.colors.surface)
            .border(1.dp, Nd.colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Nd.colors.border))

/** Uniform dot grid used as a subtle background motif. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 16.dp, radius: Dp = 1.dp): Modifier = drawBehind {
    val step = spacing.toPx()
    val r = radius.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

// ------------------------------------------------------------------ rows

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, valueColor: Color = Nd.colors.textPrimary, onClick: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(vertical = Space.sm)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(label, Modifier.weight(1f))
            Txt(value, Nd.type.caption.copy(fontSize = Nd.type.bodySm.fontSize), color = valueColor, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(Space.xs))
                Txt(unit.uppercase(), Nd.type.label, color = Nd.colors.textSecondary)
            }
            if (onClick != null) {
                Spacer(Modifier.width(Space.sm))
                Icon(R.drawable.ic_chevron_right, tint = Nd.colors.textDisabled, size = 16.dp)
            }
        }
        Divider()
    }
}

@Composable
fun NavRow(title: String, modifier: Modifier = Modifier, value: String? = null, subtitle: String? = null, @DrawableRes icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, tint = if (enabled) Nd.colors.textPrimary else Nd.colors.textDisabled)
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f)) {
                Txt(title, Nd.type.body, color = if (enabled) Nd.colors.textPrimary else Nd.colors.textDisabled, maxLines = 1)
                if (subtitle != null) Txt(subtitle, Nd.type.bodySm, color = Nd.colors.textSecondary, maxLines = 2)
            }
            if (value != null) {
                Spacer(Modifier.width(Space.sm))
                Txt(value, Nd.type.caption, color = Nd.colors.textSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(Space.sm))
            Icon(R.drawable.ic_chevron_right, tint = Nd.colors.textDisabled, size = 16.dp)
        }
        Divider()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
                .heightIn(min = 56.dp)
                .padding(vertical = Space.md)
                .semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(title, Nd.type.body, color = if (enabled) Nd.colors.textPrimary else Nd.colors.textDisabled)
                if (subtitle != null) Txt(subtitle, Nd.type.bodySm, color = Nd.colors.textSecondary)
            }
            Spacer(Modifier.width(Space.md))
            NdSwitch(checked, enabled)
        }
        Divider()
    }
}

/** Pill track, circle thumb — a physical switch. */
@Composable
fun NdSwitch(checked: Boolean, enabled: Boolean = true) {
    val c = Nd.colors
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    val track = if (checked) c.textDisplay else c.borderVisible
    val thumb = if (checked) c.black else c.textDisabled
    Canvas(Modifier.size(width = 44.dp, height = 24.dp)) {
        val alpha = if (enabled) 1f else 0.4f
        drawRoundRect(track.copy(alpha = alpha), cornerRadius = CornerRadius(size.height / 2))
        val r = size.height / 2 - 3.dp.toPx()
        val x = size.height / 2 + (size.width - size.height) * progress
        drawCircle(thumb.copy(alpha = alpha), r, Offset(x, size.height / 2))
    }
}

// ------------------------------------------------------------------ buttons & inputs

enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE }

@Composable
fun NdButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.PRIMARY, enabled: Boolean = true, @DrawableRes icon: Int? = null) {
    val c = Nd.colors
    val shape = RoundedCornerShape(999.dp)
    val (bg, fg, border) = when (style) {
        ButtonStyle.PRIMARY -> Triple(c.textDisplay, c.black, Color.Transparent)
        ButtonStyle.SECONDARY -> Triple(Color.Transparent, c.textPrimary, c.borderVisible)
        ButtonStyle.GHOST -> Triple(Color.Transparent, c.textSecondary, Color.Transparent)
        ButtonStyle.DESTRUCTIVE -> Triple(Color.Transparent, c.accent, c.accent)
    }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.4f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, tint = if (enabled) fg else fg.copy(alpha = 0.5f), size = 16.dp)
            Spacer(Modifier.width(Space.sm))
        }
        Txt(text.uppercase(), Nd.type.button, color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

@Composable
fun <T> SegmentedControl(options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, label: (T) -> String = { it.toString() }) {
    val c = Nd.colors
    val shape = RoundedCornerShape(999.dp)
    Row(modifier.fillMaxWidth().height(40.dp).clip(shape).border(1.dp, c.borderVisible, shape)) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(shape)
                    .background(if (active) c.textDisplay else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .semantics { stateDescription = if (active) "Selected" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label(option).uppercase(), Nd.type.label, color = if (active) c.black else c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Nd.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (selected) c.textDisplay else c.borderVisible, shape)
            .background(if (selected) c.textDisplay else Color.Transparent)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Txt(text.uppercase(), Nd.type.label, color = if (selected) c.black else c.textPrimary, maxLines = 1) }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    mono: Boolean = false,
    error: String? = null,
) {
    val c = Nd.colors
    Column(modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Label(label)
        Spacer(Modifier.height(Space.xs))
        val style = (if (mono || numeric) Nd.type.caption.copy(fontSize = Nd.type.body.fontSize) else Nd.type.body).copy(color = c.textPrimary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = style,
            cursorBrush = SolidColor(c.textDisplay),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Txt(placeholder, style, color = c.textDisabled)
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (error != null) c.accent else c.borderVisible))
        if (error != null) Txt(error, Nd.type.caption, color = c.accent)
    }
}

/** `−  value  +` stepper in Space Mono with bounded range. */
@Composable
fun Stepper(title: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1, modifier: Modifier = Modifier, format: (Int) -> String = { it.toString() }) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Txt(title, Nd.type.body, Modifier.weight(1f))
            CircleButton(R.drawable.ic_minus, "Decrease $title", { onChange((value - step).coerceIn(range)) }, enabled = value > range.first)
            Txt(format(value), Nd.type.caption.copy(fontSize = Nd.type.body.fontSize), Modifier.widthIn(min = 72.dp).semantics { contentDescription = "$title ${format(value)}" }, align = TextAlign.Center)
            CircleButton(R.drawable.ic_plus, "Increase $title", { onChange((value + step).coerceIn(range)) }, enabled = value < range.last)
        }
        Divider()
    }
}

// ------------------------------------------------------------------ data visualisation

/** Hero metric: Doto/Space Mono number with a small raised unit. */
@Composable
fun Hero(value: String, modifier: Modifier = Modifier, unit: String? = null, dotMatrix: Boolean = true, color: Color = Nd.colors.textDisplay) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
        Txt(value, if (dotMatrix) Nd.type.displayXl else Nd.type.dataLg, color = color, maxLines = 1)
        if (unit != null) {
            Spacer(Modifier.width(Space.sm))
            Txt(unit.uppercase(), Nd.type.label, Modifier.padding(bottom = 12.dp), Nd.colors.textSecondary)
        }
    }
}

/** Discrete-block progress bar; filled blocks past the goal turn accent red. */
@Composable
fun SegmentedBar(fraction: Float, modifier: Modifier = Modifier, segments: Int = 24, height: Dp = 10.dp, color: Color = Nd.colors.textDisplay, overflowColor: Color = Nd.colors.accent) {
    val empty = Nd.colors.segmentEmpty
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "${(fraction * 100).toInt()} percent" }) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val filled = (fraction.coerceAtLeast(0f) * segments)
        for (i in 0 until segments) {
            val on = i < filled.toInt() || (i == filled.toInt() && filled % 1f >= 0.5f)
            val over = fraction > 1f && i >= (segments / fraction).toInt()
            drawRect(if (on) (if (over) overflowColor else color) else empty, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

@Composable
fun BarChart(values: List<Int>, modifier: Modifier = Modifier, height: Dp = 120.dp, highlight: Int = -1, color: Color = Nd.colors.textDisplay, axis: Boolean = true) {
    val c = Nd.colors
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
            val n = values.size.coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            drawLine(c.border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = if (v <= 0) 0f else (v.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
                val col = if (i == highlight) c.accent else color
                drawRect(col, Offset(i * (w + gap), size.height - h), Size(w, h))
            }
        }
        if (axis && values.size == 24) HourAxis()
    }
}

@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Space.xs)) {
        listOf("00", "06", "12", "18").forEach { Txt(it, Nd.type.label, Modifier.weight(1f), Nd.colors.textDisabled, maxLines = 1) }
    }
}

/** Dot plot of periodic samples (heart rate, SpO₂, stress); zero = no reading. */
@Composable
fun SampleDots(values: List<Int>, modifier: Modifier = Modifier, height: Dp = 120.dp, color: Color = Nd.colors.textDisplay, floor: Int? = null, average: Int = 0) {
    val c = Nd.colors
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val present = values.filter { it > 0 }
            if (present.isEmpty()) return@Canvas
            val lo = floor ?: (present.min() - 5).coerceAtLeast(0)
            val hi = (present.max() + 5).coerceAtLeast(lo + 1)
            val step = size.width / values.size.coerceAtLeast(1)
            val r = (step * 0.45f).coerceIn(1.dp.toPx(), 2.5.dp.toPx())
            fun y(v: Int) = size.height - (v - lo).toFloat() / (hi - lo) * size.height
            if (average > 0) {
                drawLine(c.textSecondary, Offset(0f, y(average)), Offset(size.width, y(average)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            values.forEachIndexed { i, v -> if (v > 0) drawCircle(color, r, Offset(i * step + step / 2, y(v))) }
        }
        if (values.size >= 24) HourAxis()
    }
}

/** Thin instrument gauge with ticks; [fraction] 0..1. */
@Composable
fun Gauge(fraction: Float, modifier: Modifier = Modifier, size: Dp = 120.dp, color: Color = Nd.colors.textDisplay, content: @Composable BoxScope.() -> Unit = {}) {
    val c = Nd.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            val inset = 10.dp.toPx()
            val r = this.size.minDimension / 2 - inset
            val center = Offset(this.size.width / 2, this.size.height / 2)
            for (i in 0 until 60) {
                val a = (i / 60f) * 2 * PI.toFloat() - PI.toFloat() / 2
                val major = i % 5 == 0
                val outer = r + inset * 0.8f
                val inner = outer - if (major) 6.dp.toPx() else 3.dp.toPx()
                val on = i / 60f < fraction
                drawLine(if (on) color else c.borderVisible, Offset(center.x + cos(a) * inner, center.y + sin(a) * inner), Offset(center.x + cos(a) * outer, center.y + sin(a) * outer), if (major) 1.5.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Butt)
            }
            drawArc(c.border, 0f, 360f, false, Offset(center.x - r + inset, center.y - r + inset), Size((r - inset) * 2, (r - inset) * 2), style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(center.x - r + inset, center.y - r + inset), Size((r - inset) * 2, (r - inset) * 2), style = Stroke(stroke, cap = StrokeCap.Butt))
        }
        content()
    }
}

/** Hardware-style segmented spinner. */
@Composable
fun SegmentedSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = Nd.colors.textDisplay) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val phase by transition.animateFloat(0f, 12f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.size(size).semantics { contentDescription = "Loading" }) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        for (i in 0 until 12) {
            val a = i / 12f * 2 * PI.toFloat()
            val age = ((phase.toInt() - i + 12) % 12) / 12f
            drawLine(color.copy(alpha = 1f - age * 0.85f), Offset(center.x + cos(a) * r * 0.45f, center.y + sin(a) * r * 0.45f), Offset(center.x + cos(a) * r, center.y + sin(a) * r), 2.dp.toPx(), cap = StrokeCap.Butt)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Nd.colors
    Column(modifier.fillMaxWidth().padding(vertical = Space.huge), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 120.dp, height = 48.dp).dotGrid(c.borderVisible, 12.dp, 1.5.dp))
        Spacer(Modifier.height(Space.lg))
        Txt(title, Nd.type.subheading, color = c.textSecondary, align = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Txt(body, Nd.type.bodySm, color = c.textDisabled, align = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** Status light: a small dot that is the only colour on a monochrome row. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) = Box(modifier.size(size).clip(CircleShape).background(color))

@Composable
fun noRipple() = remember { MutableInteractionSource() }
