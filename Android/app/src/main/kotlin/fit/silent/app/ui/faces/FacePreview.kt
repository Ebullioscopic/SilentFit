package fit.silent.app.ui.faces

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.SegmentedSpinner
import fit.silent.app.ui.theme.Nd
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.Par
import fit.silent.core.watchface.WatchFaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The NF 2 screen is 240×280 with ~28 px corners. */
const val SCREEN_ASPECT = 240f / 280f

fun screenShape(width: Dp) = RoundedCornerShape(width * (28f / 240f))

/** Renders a design off the main thread, framed like the watch screen. */
@Composable
fun FacePreview(spec: FaceSpec, modifier: Modifier = Modifier, description: String = spec.name) {
    val graph = LocalGraph.current
    val image by produceState<ImageBitmap?>(null, spec) {
        value = withContext(Dispatchers.Default) { graph.rasterizer.render(spec).asImageBitmap() }
    }
    Screen(image, modifier, description)
}

/** Shows the preview image embedded in an imported "WF" file. */
@Composable
fun BinaryPreview(file: File, modifier: Modifier = Modifier, description: String) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val face = WatchFaceFile.decode(file.readBytes())
                val record = face.records.firstOrNull { it.dataId == WatchFaceFile.ID_PREVIEW } ?: face.records.first()
                val (img, _) = Par.decode(face.images[record.image])
                android.graphics.Bitmap.createBitmap(img.pixels, img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    Screen(image, modifier, description)
}

@Composable
private fun Screen(image: ImageBitmap?, modifier: Modifier, description: String) {
    val shape = RoundedCornerShape(14)
    Box(
        modifier
            .aspectRatio(SCREEN_ASPECT)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.Black)
            .border(1.dp, Nd.colors.borderVisible, shape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) SegmentedSpinner() else Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    }
}
