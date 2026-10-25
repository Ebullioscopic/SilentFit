package fit.silent.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Nothing-inspired tokens (tmp/nothing-design-skill/references/tokens.md). */
@Immutable
data class NdColors(
    val black: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderVisible: Color,
    val textDisabled: Color,
    val textSecondary: Color,
    val textPrimary: Color,
    val textDisplay: Color,
    val interactive: Color,
    val segmentEmpty: Color,
    val isDark: Boolean,
) {
    val accent = Color(0xFFD71921)
    val accentSubtle = Color(0x26D71921)
    val success = Color(0xFF4A9E5C)
    val warning = Color(0xFFD4A843)
}

val DarkColors = NdColors(
    black = Color(0xFF000000), surface = Color(0xFF111111), surfaceRaised = Color(0xFF1A1A1A),
    border = Color(0xFF222222), borderVisible = Color(0xFF333333), textDisabled = Color(0xFF666666),
    textSecondary = Color(0xFF999999), textPrimary = Color(0xFFE8E8E8), textDisplay = Color(0xFFFFFFFF),
    interactive = Color(0xFF5B9BF6), segmentEmpty = Color(0xFF222222), isDark = true,
)

val LightColors = NdColors(
    black = Color(0xFFF5F5F5), surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFF0F0F0),
    border = Color(0xFFE8E8E8), borderVisible = Color(0xFFCCCCCC), textDisabled = Color(0xFF999999),
    textSecondary = Color(0xFF666666), textPrimary = Color(0xFF1A1A1A), textDisplay = Color(0xFF000000),
    interactive = Color(0xFF007AFF), segmentEmpty = Color(0xFFE0E0E0), isDark = false,
)

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
    val huge = 96.dp
}

@Immutable
class NdFonts(val doto: FontFamily, val grotesk: FontFamily, val mono: FontFamily)

/** Type scale; Doto for hero moments only, Space Mono for data and labels, Space Grotesk for body. */
@Immutable
class NdType(f: NdFonts) {
    val displayXl = TextStyle(fontFamily = f.doto, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.03).em, fontWeight = FontWeight.Bold)
    val displayLg = TextStyle(fontFamily = f.doto, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.Bold)
    val displayMd = TextStyle(fontFamily = f.grotesk, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.Light)
    val dataLg = TextStyle(fontFamily = f.mono, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em)
    val dataMd = TextStyle(fontFamily = f.mono, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em)
    val heading = TextStyle(fontFamily = f.grotesk, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.01).em)
    val subheading = TextStyle(fontFamily = f.grotesk, fontSize = 18.sp, lineHeight = 23.sp)
    val body = TextStyle(fontFamily = f.grotesk, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySm = TextStyle(fontFamily = f.grotesk, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.01.em)
    val caption = TextStyle(fontFamily = f.mono, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.04.em)
    val label = TextStyle(fontFamily = f.mono, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.08.em)
    val button = TextStyle(fontFamily = f.mono, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)
}

object FontAssets {
    const val DOTO = "fonts/doto.ttf"
    const val GROTESK = "fonts/space_grotesk.ttf"
    const val MONO = "fonts/space_mono.ttf"
    const val MONO_BOLD = "fonts/space_mono_bold.ttf"

    @Volatile private var cache: NdFonts? = null

    fun families(context: Context): NdFonts = cache ?: synchronized(this) {
        cache ?: run {
            val assets = context.assets
            fun variable(path: String, vararg weights: Int) = FontFamily(
                weights.map { w -> Font(path, assets, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
            )
            NdFonts(
                doto = variable(DOTO, 400, 700),
                grotesk = variable(GROTESK, 300, 400, 500, 700),
                mono = FontFamily(Font(MONO, assets, FontWeight.Normal), Font(MONO_BOLD, assets, FontWeight.Bold)),
            ).also { cache = it }
        }
    }

    /** Typefaces for the face rasterizer (android.graphics). */
    fun typeface(context: Context, key: String, weight: Int): Typeface {
        val path = when (key) {
            "doto" -> DOTO
            "space-grotesk" -> GROTESK
            else -> if (weight >= 600) MONO_BOLD else MONO
        }
        return typefaces.getOrPut("$path@$weight") {
            if (path == MONO || path == MONO_BOLD) Typeface.createFromAsset(context.assets, path)
            else Typeface.Builder(context.assets, path).setFontVariationSettings("'wght' $weight").build()
        }
    }

    private val typefaces = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
}

private val LocalColors = staticCompositionLocalOf { DarkColors }
private val LocalType = staticCompositionLocalOf<NdType> { error("NdTheme missing") }
private val LocalFonts = staticCompositionLocalOf<NdFonts> { error("NdTheme missing") }

object Nd {
    val colors: NdColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: NdType @Composable @ReadOnlyComposable get() = LocalType.current
    val fonts: NdFonts @Composable @ReadOnlyComposable get() = LocalFonts.current
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun NdTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val fonts = remember { FontAssets.families(context) }
    val type = remember(fonts) { NdType(fonts) }
    CompositionLocalProvider(
        LocalColors provides if (dark) DarkColors else LightColors,
        LocalType provides type,
        LocalFonts provides fonts,
        content = content,
    )
}
