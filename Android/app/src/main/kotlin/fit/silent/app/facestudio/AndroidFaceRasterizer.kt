package fit.silent.app.facestudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import fit.silent.app.ui.theme.FontAssets
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.BindingKind
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceRasterizer
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.GlyphStrip
import fit.silent.core.watchface.Raster
import fit.silent.core.watchface.parseColor
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sample moment shown in previews: 10:08:36, the classic watch-photo pose. */
data class SampleTime(val hour: Int = 10, val minute: Int = 8, val second: Int = 36)

/**
 * Renders [FaceSpec] layers with android.graphics. The same code produces the images sent to the watch
 * and the previews shown in the studio, so what you see is what the watch draws.
 */
class AndroidFaceRasterizer(private val context: Context) : FaceRasterizer {
    private val assetCache = LruCache<String, Bitmap>(8)

    // ------------------------------------------------------------------ FaceRasterizer

    override fun background(spec: FaceSpec): Raster = backgroundBitmap(spec).toRaster()

    override fun glyphs(spec: FaceSpec, element: FaceElement, frames: List<String>): GlyphStrip {
        val paint = textPaint(element)
        val fm = paint.fontMetrics
        val frameW = max(1, ceil(frames.maxOf { paint.measureText(it) }).toInt() + 1)
        val frameH = max(1, ceil(fm.descent - fm.ascent).toInt())
        val bg = backgroundColorBehind(spec, element, frameW, frameH)
        val bmp = createBitmap(frameW, frameH * frames.size)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        frames.forEachIndexed { i, text ->
            val x = (frameW - paint.measureText(text)) / 2f
            canvas.drawText(text, x, i * frameH - fm.ascent, paint)
        }
        val raster = bmp.toRaster()
        return GlyphStrip(frameW, frameH, frames.size, raster.pixels)
    }

    override fun hand(spec: FaceSpec, element: FaceElement): Raster {
        val bmp = createBitmap(spec.width, spec.height)
        drawHand(Canvas(bmp), element, 0f)
        return bmp.toRaster()
    }

    override fun preview(spec: FaceSpec, width: Int, height: Int): Raster =
        render(spec, SampleTime()).let { full -> full.scale(width, height, filter = true).toRaster().also { full.recycle() } }

    // ------------------------------------------------------------------ previews

    /** The whole face as the watch would draw it at [time], at native resolution. */
    fun render(spec: FaceSpec, time: SampleTime = SampleTime(), values: Map<String, String> = emptyMap()): Bitmap {
        val bmp = backgroundBitmap(spec)
        val canvas = Canvas(bmp)
        for (e in spec.elements) {
            if (!e.isLive) continue
            if (e.type == "hand") continue
            val frames = e.glyphFrames()
            val strip = glyphs(spec, e, frames)
            val stripBmp = Bitmap.createBitmap(strip.pixels, strip.width, strip.height, Bitmap.Config.ARGB_8888)
            val value = values[e.bind] ?: sampleValue(e, time)
            val indices = frameIndices(e, frames, value)
            val total = indices.size * strip.width
            val right = if (e.w > 0) e.x + e.w else e.x + strip.width * (e.binding?.maxDigits ?: 1)
            var x = when {
                e.align == "right" -> right - total
                e.align == "center" && e.w > 0 && e.binding?.digitIds != null -> e.x + (e.w - total) / 2
                else -> e.x
            }
            for (index in indices) {
                val src = Rect(0, index * strip.frameHeight, strip.width, (index + 1) * strip.frameHeight)
                canvas.drawBitmap(stripBmp, src, Rect(x, e.y, x + strip.width, e.y + strip.frameHeight), null)
                x += strip.width
            }
            stripBmp.recycle()
        }
        spec.elements.filter { it.type == "hand" }.sortedBy { it.binding?.dataId ?: 0 }.forEach { e ->
            val angle = when (e.binding) {
                Binding.HOUR_HAND -> (time.hour % 12 + time.minute / 60f) * 30f
                Binding.MINUTE_HAND -> (time.minute + time.second / 60f) * 6f
                else -> time.second * 6f
            }
            drawHand(canvas, e, angle)
        }
        return bmp
    }

    private fun sampleValue(e: FaceElement, t: SampleTime): String = when (e.binding) {
        Binding.HOUR -> "%02d".format(t.hour)
        Binding.MINUTE -> "%02d".format(t.minute)
        Binding.SECOND -> "%02d".format(t.second)
        null -> "8"
        else -> e.binding!!.sample
    }

    /** Which frames the watch would draw for [value]: digits per character, or a single sprite index. */
    private fun frameIndices(e: FaceElement, frames: List<String>, value: String): List<Int> {
        val kind = e.binding?.kind ?: if (e.type == "sprite") BindingKind.SPRITE else BindingKind.NUMBER
        return if (kind == BindingKind.SPRITE) {
            listOf(frames.indexOfFirst { it.equals(value, true) }.coerceAtLeast(0))
        } else {
            value.filter { it.isDigit() }.map { it - '0' }.filter { it < frames.size }
        }
    }

    /** Screen-space box an element occupies, for selection and dragging in the studio. */
    fun bounds(spec: FaceSpec, e: FaceElement): RectF = when (e.type) {
        "hand" -> RectF(e.pivotX - 10f, e.pivotY - 10f, e.pivotX + 10f, e.pivotY + 10f)
        "number", "sprite" -> {
            val paint = textPaint(e)
            val fm = paint.fontMetrics
            val glyphW = e.glyphFrames().maxOf { paint.measureText(it) } + 1
            val count = if (e.type == "sprite") 1 else (e.binding?.maxDigits ?: 2)
            val w = if (e.w > 0) e.w.toFloat() else glyphW * count
            RectF(e.x.toFloat(), e.y.toFloat(), e.x + w, e.y + (fm.descent - fm.ascent))
        }
        "text" -> {
            val paint = textPaint(e)
            val text = (e.text ?: "").let { if (e.uppercase) it.uppercase() else it }
            val width = paint.measureText(text)
            val h = paint.fontMetrics.let { it.descent - it.ascent }
            val left = when (e.align) {
                "center" -> if (e.w > 0) e.x + (e.w - width) / 2 else e.x - width / 2
                "right" -> if (e.w > 0) e.x + e.w - width else e.x - width
                else -> e.x.toFloat()
            }
            RectF(left, e.y.toFloat(), left + width, e.y + h)
        }
        else -> RectF(e.x.toFloat(), e.y.toFloat(), (e.x + max(e.w, 4)).toFloat(), (e.y + max(e.h, 4)).toFloat())
    }

    // ------------------------------------------------------------------ drawing

    private fun backgroundBitmap(spec: FaceSpec): Bitmap {
        val bmp = createBitmap(spec.width, spec.height)
        val canvas = Canvas(bmp)
        val b = spec.background
        canvas.drawColor(parseColor(b.color, 0xFF000000.toInt()))
        b.image?.let { asset(spec, it) }?.let { img -> drawCover(canvas, img, RectF(0f, 0f, spec.width.toFloat(), spec.height.toFloat())) }
        drawPattern(canvas, spec)
        spec.elements.filter { !it.isLive }.forEach { drawStatic(canvas, spec, it) }
        return bmp
    }

    private fun drawPattern(canvas: Canvas, spec: FaceSpec) {
        val b = spec.background
        if (b.pattern == "none") return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(b.patternColor, 0xFF222222.toInt()) }
        val step = b.patternSpacing.coerceIn(4, 64).toFloat()
        when (b.pattern) {
            "dots" -> {
                var y = step / 2
                while (y < spec.height) {
                    var x = step / 2
                    while (x < spec.width) { canvas.drawCircle(x, y, 1f, p); x += step }
                    y += step
                }
            }
            "grid" -> {
                p.strokeWidth = 1f
                var x = 0f
                while (x <= spec.width) { canvas.drawLine(x, 0f, x, spec.height.toFloat(), p); x += step }
                var y = 0f
                while (y <= spec.height) { canvas.drawLine(0f, y, spec.width.toFloat(), y, p); y += step }
            }
            "lines" -> {
                p.strokeWidth = 1f
                var y = step / 2
                while (y < spec.height) { canvas.drawLine(0f, y, spec.width.toFloat(), y, p); y += step }
            }
        }
    }

    private fun drawStatic(canvas: Canvas, spec: FaceSpec, e: FaceElement) {
        val color = parseColor(e.color)
        val alpha = (e.opacity.coerceIn(0f, 1f) * 255).roundToInt()
        when (e.type) {
            "text" -> {
                val paint = textPaint(e).apply { this.alpha = alpha * (color ushr 24) / 255 }
                val text = (e.text ?: "").let { if (e.uppercase) it.uppercase() else it }
                val width = paint.measureText(text)
                val x = when (e.align) {
                    "center" -> if (e.w > 0) e.x + (e.w - width) / 2 else e.x - width / 2
                    "right" -> if (e.w > 0) e.x + e.w - width else e.x - width
                    else -> e.x.toFloat()
                }
                canvas.drawText(text, x, e.y - paint.fontMetrics.ascent, paint)
            }
            "image" -> e.src?.let { asset(spec, it) }?.let { img ->
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
                canvas.drawBitmap(img, null, RectF(e.x.toFloat(), e.y.toFloat(), (e.x + max(1, e.w)).toFloat(), (e.y + max(1, e.h)).toFloat()), paint)
            }
            "shape" -> drawShape(canvas, e, color, alpha)
        }
    }

    private fun drawShape(canvas: Canvas, e: FaceElement, color: Int, alpha: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = alpha * (color ushr 24) / 255
            style = if (e.fill && e.shape in FILLABLE) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = e.stroke
            strokeCap = Paint.Cap.BUTT
        }
        val rect = RectF(e.x.toFloat(), e.y.toFloat(), (e.x + e.w).toFloat(), (e.y + e.h).toFloat())
        val cx = rect.centerX(); val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) / 2f
        when (e.shape) {
            "rect" -> canvas.drawRoundRect(rect, e.size.coerceAtMost(r), e.size.coerceAtMost(r), p)
            "circle" -> canvas.drawCircle(cx, cy, if (p.style == Paint.Style.STROKE) r - e.stroke / 2 else r, p)
            "ring" -> { p.style = Paint.Style.STROKE; canvas.drawCircle(cx, cy, r - e.stroke / 2, p) }
            "arc" -> {
                p.style = Paint.Style.STROKE
                val inset = e.stroke / 2
                canvas.drawArc(RectF(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset), e.startAngle - 90f, e.sweep, false, p)
            }
            "line" -> canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, p.apply { style = Paint.Style.STROKE })
            "ticks" -> {
                p.style = Paint.Style.STROKE
                val n = e.count.coerceIn(1, 240)
                for (i in 0 until n) {
                    val a = Math.toRadians((e.startAngle + i * e.sweep / n - 90f).toDouble())
                    val major = n >= 24 && i % (n / 12) == 0
                    val len = if (major) e.size * 1.8f else e.size
                    val outer = r
                    val inner = r - len
                    p.strokeWidth = if (major) e.stroke * 1.5f else e.stroke
                    canvas.drawLine(cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), p)
                }
            }
            "dot-ring" -> {
                p.style = Paint.Style.FILL
                val n = e.count.coerceIn(1, 240)
                val dot = e.stroke
                for (i in 0 until n) {
                    val a = Math.toRadians((e.startAngle + i * e.sweep / n - 90f).toDouble())
                    val major = n >= 24 && i % (n / 12) == 0
                    val rr = r - dot * 1.5f
                    canvas.drawCircle(cx + (cos(a) * rr).toFloat(), cy + (sin(a) * rr).toFloat(), if (major) dot * 1.6f else dot, p)
                }
            }
            "segments" -> {
                p.style = Paint.Style.FILL
                val n = e.count.coerceIn(1, 120)
                val gap = 2f
                val w = (rect.width() - gap * (n - 1)) / n
                val lit = (n * (e.sweep / 360f)).roundToInt()
                for (i in 0 until n) {
                    p.alpha = if (i < lit) alpha else alpha / 4
                    canvas.drawRect(rect.left + i * (w + gap), rect.top, rect.left + i * (w + gap) + w, rect.bottom, p)
                }
            }
            "dot-matrix" -> {
                p.style = Paint.Style.FILL
                val dot = e.stroke
                val step = dot * 3f
                var y = rect.top + step / 2
                while (y < rect.bottom) {
                    var x = rect.left + step / 2
                    while (x < rect.right) { canvas.drawCircle(x, y, dot, p); x += step }
                    y += step
                }
            }
        }
    }

    /** Draws a hand rotated [degrees] clockwise from 12 o'clock; the watch file stores it at 3 o'clock (90°). */
    private fun drawHand(canvas: Canvas, e: FaceElement, degrees: Float) {
        val color = parseColor(e.color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND }
        val px = e.pivotX.toFloat(); val py = e.pivotY.toFloat()
        canvas.save()
        canvas.rotate(degrees - 90f, px, py)
        val len = e.length.toFloat()
        val tail = e.tail.toFloat()
        val w = e.stroke
        when (e.handStyle) {
            "needle" -> {
                val path = Path().apply {
                    moveTo(px - tail, py - w / 2); lineTo(px + len, py); lineTo(px - tail, py + w / 2); close()
                }
                canvas.drawPath(path, p)
            }
            "line" -> { p.strokeWidth = w; p.style = Paint.Style.STROKE; canvas.drawLine(px - tail, py, px + len, py, p) }
            "dot" -> canvas.drawCircle(px + len, py, w, p)
            "dots" -> {
                val step = w * 2.6f
                var d = step
                while (d <= len) { canvas.drawCircle(px + d, py, w / 2f + 0.5f, p); d += step }
            }
            "outline" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f
                canvas.drawRoundRect(RectF(px + tail, py - w / 2, px + len, py + w / 2), w / 2, w / 2, p)
                canvas.drawLine(px, py, px + tail, py, p)
            }
            else -> canvas.drawRoundRect(RectF(px - tail, py - w / 2, px + len, py + w / 2), w / 2, w / 2, p)
        }
        if (e.size > 0 && e.handStyle != "dot") {
            p.style = Paint.Style.FILL
            canvas.drawCircle(px, py, e.size / 4f, p)
        }
        canvas.restore()
    }

    private fun textPaint(e: FaceElement) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = FontAssets.typeface(context, e.font, e.weight)
        textSize = e.size
        color = parseColor(e.color)
        letterSpacing = e.tracking
        if (e.font == "doto") isAntiAlias = true
    }

    /** Live glyph images are opaque on the watch, so they carry the background colour found behind them. */
    private fun backgroundColorBehind(spec: FaceSpec, e: FaceElement, frameW: Int, frameH: Int): Int {
        if (spec.background.image == null && spec.elements.none { !it.isLive && it.type != "text" }) return parseColor(spec.background.color, 0xFF000000.toInt())
        val bg = backgroundBitmap(spec)
        val x = (if (e.align == "right") (e.x + max(e.w, frameW) - frameW / 2) else (e.x + frameW / 2)).coerceIn(0, spec.width - 1)
        val y = (e.y + frameH / 2).coerceIn(0, spec.height - 1)
        val c = bg.getPixel(x, y)
        bg.recycle()
        return c or 0xFF000000.toInt()
    }

    private fun drawCover(canvas: Canvas, img: Bitmap, dst: RectF) {
        val scale = max(dst.width() / img.width, dst.height() / img.height)
        val w = dst.width() / scale; val h = dst.height() / scale
        val src = Rect(((img.width - w) / 2).toInt(), ((img.height - h) / 2).toInt(), ((img.width + w) / 2).toInt(), ((img.height + h) / 2).toInt())
        canvas.drawBitmap(img, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun asset(spec: FaceSpec, name: String): Bitmap? {
        val data = spec.assets[name] ?: return null
        val key = "$name:${data.length}:${data.hashCode()}"
        assetCache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { assetCache.put(key, it) }
    }

    private fun Bitmap.toRaster(): Raster {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height)
        return Raster(width, height, px)
    }

    companion object {
        private val FILLABLE = setOf("rect", "circle")
    }
}
