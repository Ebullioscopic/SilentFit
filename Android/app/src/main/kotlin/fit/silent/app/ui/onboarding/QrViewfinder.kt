package fit.silent.app.ui.onboarding

import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import fit.silent.app.ui.theme.Nd
import java.util.concurrent.Executors

/** Square camera viewfinder that reports every QR code it reads. Decoding is local (ZXing); frames are not kept. */
@Composable
fun QrViewfinder(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val corner = Nd.colors.textDisplay

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val usePreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                        .build(),
                )
                .build()
            val reader = QrReader()
            var camera: Camera? = null
            var frames = 0
            analysis.setAnalyzer(executor) { image ->
                val text = image.use { reader.read(it) }
                if (text != null) ContextCompat.getMainExecutor(context).execute { latest(text) }
                // A backlit LCD blooms at normal exposure, so step through darker settings until a frame decodes.
                if (++frames % EXPOSURE_FRAMES == 0) camera?.let { cam ->
                    val range = cam.cameraInfo.exposureState.exposureCompensationRange
                    val steps = listOf(0, range.lower / 3, range.lower * 2 / 3, range.lower).distinct()
                    val index = steps[(frames / EXPOSURE_FRAMES) % steps.size]
                    cam.cameraControl.setExposureCompensationIndex(index)
                    Log.i("SilentFit", "QR scan: $frames frames, ${image.width}x${image.height}, exposure $index, luma ${reader.lastMean}")
                }
            }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, usePreview, analysis).also { cam ->
                    // The watch's code is small; zoom so it fills the frame from a comfortable focus distance.
                    val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    cam.cameraControl.setZoomRatio(minOf(2f, maxZoom))
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.14f
            val frame = Rect(inset, inset, size.width - inset, size.height - inset)
            val arm = frame.width * 0.12f
            val stroke = Stroke(width = 2.dp.toPx())
            val scrim = Color.Black.copy(alpha = 0.45f)
            drawRect(scrim, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, frame.top))
            drawRect(scrim, Offset(0f, frame.bottom), androidx.compose.ui.geometry.Size(size.width, size.height - frame.bottom))
            drawRect(scrim, Offset(0f, frame.top), androidx.compose.ui.geometry.Size(frame.left, frame.height))
            drawRect(scrim, Offset(frame.right, frame.top), androidx.compose.ui.geometry.Size(size.width - frame.right, frame.height))
            listOf(
                Triple(frame.topLeft, 1f, 1f), Triple(frame.topRight, -1f, 1f),
                Triple(frame.bottomLeft, 1f, -1f), Triple(frame.bottomRight, -1f, -1f),
            ).forEach { (p, dx, dy) ->
                drawPath(Path().apply { moveTo(p.x, p.y + arm * dy); lineTo(p.x, p.y); lineTo(p.x + arm * dx, p.y) }, corner, style = stroke)
            }
        }
    }
}

private const val EXPOSURE_FRAMES = 6

private class QrReader {
    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )
    @Volatile var lastMean = 0

    fun read(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val stride = plane.rowStride
        val buffer = plane.buffer.apply { rewind() }
        val luma = ByteArray(width * height)
        if (stride == width) buffer.get(luma, 0, minOf(luma.size, buffer.remaining()))
        else for (row in 0 until height) {
            buffer.position(row * stride)
            buffer.get(luma, row * width, minOf(width, buffer.remaining()))
        }
        var sum = 0L
        for (i in luma.indices step 97) sum += luma[i].toInt() and 0xFF
        lastMean = (sum / (luma.size / 97 + 1)).toInt()
        val full = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        // Centre square roughly under the on-screen frame: fewer pixels and less glare to confuse the binarizer.
        val side = minOf(width, height) * 3 / 4
        val centre = PlanarYUVLuminanceSource(luma, width, height, (width - side) / 2, (height - side) / 2, side, side, false)
        for (source in listOf(centre, full)) {
            // Watch screens show the code light-on-dark as often as dark-on-light.
            for (s in listOf(source, source.invert())) {
                decode(BinaryBitmap(HybridBinarizer(s)))?.let { return it }
                decode(BinaryBitmap(GlobalHistogramBinarizer(s)))?.let { return it }
            }
        }
        return null
    }

    private fun decode(bitmap: BinaryBitmap): String? = try {
        reader.decode(bitmap, hints).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
