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
import fit.silent.core.watchface.glyphSlots
import fit.silent.core.watchface.isFixedGlyph
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

    override fun glyphs(spec: FaceSpec, element: FaceElement, frames: List<String>, at: Pair<Int, Int>?): GlyphStrip {
        val (cw, ch) = cellSize(element, frames)
        val rot = rotationOf(element)
        val (fw, fh) = if (rot % 180 == 90) ch to cw else cw to ch
        val bmp = createBitmap(fw, fh * frames.size)
        val canvas = Canvas(bmp)
        val patch = at?.let { backgroundPatch(spec, it.first, it.second, fw, fh) }
        if (patch == null) canvas.drawColor(backgroundColorBehind(spec, element, fw, fh))
        frames.forEachIndexed { i, text ->
            val top = (i * fh).toFloat()
            patch?.let { canvas.drawBitmap(it, 0f, top, null) }
            canvas.save()
            canvas.translate(0f, top)
            canvas.clipRect(0, 0, fw, fh)
            applyRotation(canvas, rot, cw, ch)
            drawGlyphText(canvas, element, text, cw, ch)
            canvas.restore()
        }
        patch?.recycle()
        val raster = bmp.toRaster()
        return GlyphStrip(fw, fh, frames.size, raster.pixels)
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
            val value = values[e.bind] ?: sampleValue(e, time)
            if (e.isFixedGlyph) {
                val (cw, ch) = cellSize(e, frames)
                val rot = rotationOf(e)
                val slots = e.glyphSlots(if (rot % 180 == 90) ch else cw)
                val texts = if (e.type == "sprite") listOf(frames[frameIndices(e, frames, value).first()])
                else value.filter { it.isDigit() }.padStart(slots.size, '0').takeLast(slots.size).map { frames.getOrElse(it - '0') { frames[0] } }
                slots.forEachIndexed { i, x ->
                    canvas.save()
                    canvas.translate(x.toFloat(), e.y.toFloat())
                    applyRotation(canvas, rot, cw, ch)
                    drawGlyphText(canvas, e, texts[i], cw, ch)
                    canvas.restore()
                }
                continue
            }
            val strip = glyphs(spec, e, frames, null)
            val stripBmp = Bitmap.createBitmap(strip.pixels, strip.width, strip.height, Bitmap.Config.ARGB_8888)
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
            val (cw, ch) = cellSize(e, e.glyphFrames())
            val (fw, fh) = if (rotationOf(e) % 180 == 90) ch to cw else cw to ch
            val count = if (e.type == "sprite") 1 else (e.binding?.digitIds?.size ?: e.binding?.maxDigits ?: 2)
            val w = if (e.w > 0) e.w.toFloat() else (fw * count).toFloat()
            RectF(e.x.toFloat(), e.y.toFloat(), e.x + w, (e.y + fh).toFloat())
        }
        "text" -> {
            val text = (e.text ?: "").let { if (e.uppercase) it.uppercase() else it }
            val (cw, ch) = cellSize(e, listOf(text))
            val (bw, bh) = if (rotationOf(e) % 180 == 90) ch to cw else cw to ch
            val left = textLeft(e, bw.toFloat())
            RectF(left, e.y.toFloat(), left + bw, (e.y + bh).toFloat())
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
                val text = (e.text ?: "").let { if (e.uppercase) it.uppercase() else it }
                val (cw, ch) = cellSize(e, listOf(text))
                val rot = rotationOf(e)
                val bw = if (rot % 180 == 90) ch else cw
                canvas.saveLayerAlpha(null, alpha)
                canvas.translate(textLeft(e, bw.toFloat()), e.y.toFloat())
                applyRotation(canvas, rot, cw, ch)
                drawGlyphText(canvas, e, text, cw, ch)
                canvas.restore()
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
        if (spec.background.image == null && spec.background.pattern == "none" && spec.elements.none { !it.isLive && it.type != "text" }) {
            return parseColor(spec.background.color, 0xFF000000.toInt())
        }
        val x = (if (e.align == "right") (e.x + max(e.w, frameW) - frameW / 2) else (e.x + frameW / 2)).coerceIn(0, spec.width - 1)
        val y = (e.y + frameH / 2).coerceIn(0, spec.height - 1)
        return cachedBackground(spec).getPixel(x, y) or 0xFF000000.toInt()
    }

    private var bgCache: Pair<FaceSpec, Bitmap>? = null

    @Synchronized
    private fun cachedBackground(spec: FaceSpec): Bitmap {
        bgCache?.let { (s, b) -> if (s === spec) return b }
        return backgroundBitmap(spec).also { bgCache = spec to it }
    }

    /** The [w]×[h] piece of the flattened background whose top-left is at ([x], [y]). */
    private fun backgroundPatch(spec: FaceSpec, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val patch = createBitmap(w, h)
        val canvas = Canvas(patch)
        canvas.drawColor(0xFF000000.toInt())
        canvas.drawBitmap(cachedBackground(spec), -x.toFloat(), -y.toFloat(), null)
        return patch
    }

    // ------------------------------------------------------------------ glyph styles

    private fun rotationOf(e: FaceElement) = if (e.type == "number") 0 else ((e.rotation % 360 + 360) % 360) / 90 * 90

    /** Rotates the canvas so content drawn in a [cw]×[ch] cell lands rotated inside the frame at the origin. */
    private fun applyRotation(canvas: Canvas, rot: Int, cw: Int, ch: Int) {
        when (rot) {
            90 -> { canvas.translate(ch.toFloat(), 0f); canvas.rotate(90f) }
            180 -> { canvas.translate(cw.toFloat(), ch.toFloat()); canvas.rotate(180f) }
            270 -> { canvas.translate(0f, cw.toFloat()); canvas.rotate(270f) }
        }
    }

    private fun textLeft(e: FaceElement, width: Float) = when (e.align) {
        "center" -> if (e.w > 0) e.x + (e.w - width) / 2 else e.x - width / 2
        "right" -> if (e.w > 0) e.x + e.w - width else e.x - width
        else -> e.x.toFloat()
    }

    private fun dotPitch(e: FaceElement) = max(1.5f, e.size / 7f)
    private fun segWidth(e: FaceElement) = e.size * 0.56f
    private fun segGap(e: FaceElement) = e.size * 0.14f
    private fun isSegmentChar(c: Char) = c.isDigit() || c == '-' || c == ' '

    private fun advance(e: FaceElement, c: Char) = when {
        e.glyph == "segment" && isSegmentChar(c) -> segWidth(e)
        e.glyph == "segment" && c == ':' -> e.size * 0.2f
        else -> dotPitch(e) * 5
    }

    private fun styledWidth(e: FaceElement, text: String): Float {
        if (text.isEmpty()) return 0f
        val gap = if (e.glyph == "segment") segGap(e) else dotPitch(e)
        return text.sumOf { advance(e, it).toDouble() }.toFloat() + gap * (text.length - 1)
    }

    /** Unrotated size of one frame that fits every string in [frames]. */
    private fun cellSize(e: FaceElement, frames: List<String>): Pair<Int, Int> {
        val (w, h) = when (e.glyph) {
            "dot-matrix" -> (ceil(frames.maxOf { styledWidth(e, it.uppercase()) }).toInt() + 2) to (ceil(dotPitch(e) * 7).toInt() + 2)
            "segment" -> (ceil(frames.maxOf { styledWidth(e, it.uppercase()) }).toInt() + 2) to (ceil(e.size).toInt() + 2)
            else -> {
                val paint = textPaint(e)
                val fm = paint.fontMetrics
                (ceil(frames.maxOf { paint.measureText(it) }).toInt() + 1) to ceil(fm.descent - fm.ascent).toInt()
            }
        }
        return max(1, w) to max(1, h)
    }

    /** Draws [text] centred in an unrotated [cw]×[ch] cell at the canvas origin. */
    private fun drawGlyphText(canvas: Canvas, e: FaceElement, text: String, cw: Int, ch: Int) {
        if (e.glyph != "dot-matrix" && e.glyph != "segment") {
            val paint = textPaint(e)
            canvas.drawText(text, (cw - paint.measureText(text)) / 2f, -paint.fontMetrics.ascent, paint)
            return
        }
        val t = text.uppercase()
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(e.color) }
        val ghost = e.ghost?.let { g -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(g) } }
        val gap = if (e.glyph == "segment") segGap(e) else dotPitch(e)
        var x = (cw - styledWidth(e, t)) / 2f
        for (c in t) {
            when {
                e.glyph == "segment" && isSegmentChar(c) -> drawSegments(canvas, c, x, 1f, segWidth(e), e.size, lit, ghost)
                e.glyph == "segment" && c == ':' -> {
                    val r = e.size * 0.065f
                    canvas.drawCircle(x + e.size * 0.1f, 1f + e.size * 0.3f, r, lit)
                    canvas.drawCircle(x + e.size * 0.1f, 1f + e.size * 0.7f, r, lit)
                }
                else -> drawDots(canvas, c, x, (ch - dotPitch(e) * 7) / 2f, dotPitch(e), lit, ghost)
            }
            x += advance(e, c) + gap
        }
    }

    private fun drawDots(canvas: Canvas, c: Char, left: Float, top: Float, p: Float, lit: Paint, ghost: Paint?) {
        val rows = DOT_FONT[c] ?: DOT_FONT.getValue(' ')
        val r = max(0.6f, p * 0.42f)
        for (row in 0 until 7) for (col in 0 until 5) {
            val paint = if ((rows[row] shr (4 - col)) and 1 == 1) lit else ghost ?: continue
            canvas.drawCircle(left + col * p + p / 2, top + row * p + p / 2, r, paint)
        }
    }

    private fun drawSegments(canvas: Canvas, c: Char, left: Float, top: Float, w: Float, h: Float, lit: Paint, ghost: Paint?) {
        val mask = SEGMENTS[c] ?: 0
        val t = h * 0.13f
        val inset = t * 0.6f
        val mid = top + h / 2
        val boxes = listOf(
            RectF(left + inset, top, left + w - inset, top + t),
            RectF(left + w - t, top + inset, left + w, mid - t * 0.35f),
            RectF(left + w - t, mid + t * 0.35f, left + w, top + h - inset),
            RectF(left + inset, top + h - t, left + w - inset, top + h),
            RectF(left, mid + t * 0.35f, left + t, top + h - inset),
            RectF(left, top + inset, left + t, mid - t * 0.35f),
            RectF(left + inset, mid - t / 2, left + w - inset, mid + t / 2),
        )
        boxes.forEachIndexed { i, box ->
            val paint = if ((mask shr i) and 1 == 1) lit else ghost ?: return@forEachIndexed
            canvas.drawRoundRect(box, t / 2, t / 2, paint)
        }
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

        /** Segments a..g as bits 0..6. */
        private val SEGMENTS = mapOf(
            '0' to 0b0111111, '1' to 0b0000110, '2' to 0b1011011, '3' to 0b1001111, '4' to 0b1100110,
            '5' to 0b1101101, '6' to 0b1111101, '7' to 0b0000111, '8' to 0b1111111, '9' to 0b1101111, '-' to 0b1000000, ' ' to 0,
        )

        /** Classic 5×7 LED font, seven 5-bit rows per character. */
        private val DOT_FONT: Map<Char, IntArray> = mapOf(
            '0' to "01110 10001 10011 10101 11001 10001 01110", '1' to "00100 01100 00100 00100 00100 00100 01110",
            '2' to "01110 10001 00001 00010 00100 01000 11111", '3' to "11111 00010 00100 00010 00001 10001 01110",
            '4' to "00010 00110 01010 10010 11111 00010 00010", '5' to "11111 10000 11110 00001 00001 10001 01110",
            '6' to "00110 01000 10000 11110 10001 10001 01110", '7' to "11111 00001 00010 00100 01000 01000 01000",
            '8' to "01110 10001 10001 01110 10001 10001 01110", '9' to "01110 10001 10001 01111 00001 00010 01100",
            'A' to "01110 10001 10001 10001 11111 10001 10001", 'B' to "11110 10001 10001 11110 10001 10001 11110",
            'C' to "01110 10001 10000 10000 10000 10001 01110", 'D' to "11100 10010 10001 10001 10001 10010 11100",
            'E' to "11111 10000 10000 11110 10000 10000 11111", 'F' to "11111 10000 10000 11110 10000 10000 10000",
            'G' to "01110 10001 10000 10111 10001 10001 01111", 'H' to "10001 10001 10001 11111 10001 10001 10001",
            'I' to "01110 00100 00100 00100 00100 00100 01110", 'J' to "00111 00010 00010 00010 00010 10010 01100",
            'K' to "10001 10010 10100 11000 10100 10010 10001", 'L' to "10000 10000 10000 10000 10000 10000 11111",
            'M' to "10001 11011 10101 10101 10001 10001 10001", 'N' to "10001 10001 11001 10101 10011 10001 10001",
            'O' to "01110 10001 10001 10001 10001 10001 01110", 'P' to "11110 10001 10001 11110 10000 10000 10000",
            'Q' to "01110 10001 10001 10001 10101 10010 01101", 'R' to "11110 10001 10001 11110 10100 10010 10001",
            'S' to "01111 10000 10000 01110 00001 00001 11110", 'T' to "11111 00100 00100 00100 00100 00100 00100",
            'U' to "10001 10001 10001 10001 10001 10001 01110", 'V' to "10001 10001 10001 10001 10001 01010 00100",
            'W' to "10001 10001 10001 10101 10101 10101 01010", 'X' to "10001 10001 01010 00100 01010 10001 10001",
            'Y' to "10001 10001 10001 01010 00100 00100 00100", 'Z' to "11111 00001 00010 00100 01000 10000 11111",
            '-' to "00000 00000 00000 11111 00000 00000 00000", ':' to "00000 01100 01100 00000 01100 01100 00000",
            '.' to "00000 00000 00000 00000 00000 01100 01100", '%' to "11000 11001 00010 00100 01000 10011 00011",
            '/' to "00000 00001 00010 00100 01000 10000 00000", ' ' to "00000 00000 00000 00000 00000 00000 00000",
        ).mapValues { (_, rows) -> rows.split(' ').map { it.toInt(2) }.toIntArray() }
    }
}
