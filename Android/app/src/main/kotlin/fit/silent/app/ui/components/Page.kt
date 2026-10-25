package fit.silent.app.ui.components

import android.app.TimePickerDialog
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import java.text.NumberFormat
import kotlin.math.roundToInt

/** Page scaffold: OLED/off-white canvas, status-bar inset, 20 dp gutters, constrained width on tablets. */
@Composable
fun Page(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Nd.colors.black), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().widthIn(max = 640.dp).windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = Space.xxl),
            content = content,
        )
    }
}

/** Non-lazy variant for short forms. */
@Composable
fun FormPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Nd.colors.black), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 640.dp).windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            content()
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** Taps open the system time picker; value is minutes since midnight. */
@Composable
fun TimeRow(title: String, minutes: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    StatRow(title, Format.clock(minutes), Modifier.heightIn(min = 48.dp), onClick = if (!enabled) null else {
        { TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(context)).show() }
    })
}

object Format {
    private val number = NumberFormat.getIntegerInstance()

    fun int(v: Int): String = number.format(v)

    fun clock(minutes: Int): String = "%02d:%02d".format((minutes / 60) % 24, minutes % 60)

    fun duration(minutes: Int): String = when {
        minutes <= 0 -> "--"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
    }

    fun distance(meters: Int, metric: Boolean): Pair<String, String> =
        if (metric) "%.2f".format(meters / 1000f) to "km" else "%.2f".format(meters / 1609.34f) to "mi"

    fun percent(f: Float) = "${(f * 100).roundToInt()}%"

    fun ago(millis: Long): String =
        if (millis <= 0) "Never" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    fun weekdays(mask: Int): String {
        if (mask == 0) return "Once"
        if (mask == 0x7F) return "Every day"
        if (mask == 0x1F) return "Weekdays"
        if (mask == 0x60) return "Weekends"
        return listOf("M", "T", "W", "T", "F", "S", "S").filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(" ")
    }
}
