package fit.silent.app.ui.faces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.data.FaceDocument
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.Routes
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Chip
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.Icon
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedBar
import fit.silent.app.ui.components.SegmentedControl
import fit.silent.app.ui.components.Stepper
import fit.silent.app.ui.components.SwitchRow
import fit.silent.app.ui.components.TextInput
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.watch.FaceInstall
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.BindingKind
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceSafety
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.colorHex
import fit.silent.core.watchface.isFixedGlyph
import fit.silent.core.watchface.parseColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Panel(val label: String) { LAYERS("Layers"), STYLE("Style"), ADD("Add"), FACE("Face") }

private val PALETTE = listOf("#FFFFFF", "#E8E8E8", "#999999", "#666666", "#333333", "#000000", "#D71921", "#4A9E5C", "#D4A843", "#5B9BF6", "#F5F5F5", "#1A1A1A")
private val FONTS = listOf("doto" to "Doto", "space-mono" to "Mono", "space-grotesk" to "Grotesk")

/** Undo/redo history of whole-spec snapshots. */
private class History {
    val undo = mutableStateListOf<FaceSpec>()
    val redo = mutableStateListOf<FaceSpec>()
    fun push(s: FaceSpec) { undo.add(s); if (undo.size > 60) undo.removeAt(0); redo.clear() }
}

@Composable
fun FaceEditorScreen(id: String, nav: NavController) {
    val graph = LocalGraph.current
    val designs by graph.faces.designs.collectAsStateWithLifecycle()
    val doc = designs.firstOrNull { it.id == id }
    if (doc == null) {
        Page { item { BackHeader("Face", { nav.popBackStack() }) }; item { EmptyState("Design not found", "It may have been deleted.") } }
        return
    }
    Editor(doc, nav)
}

@Composable
private fun Editor(doc: FaceDocument, nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection by graph.session.connection.collectAsStateWithLifecycle()
    val transfer by graph.session.transfer.collectAsStateWithLifecycle()
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var spec by remember(doc.id) { mutableStateOf(doc.spec) }
    val history = remember(doc.id) { History() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var panel by rememberSaveable { mutableStateOf(Panel.STYLE) }
    var status by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(false) }
    val installed by graph.session.faces.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = spec.elements.firstOrNull { it.id == selectedId }
    val c = Nd.colors

    fun edit(next: FaceSpec) { if (next != spec) { history.push(spec); spec = next } }
    fun editSelected(transform: (FaceElement) -> FaceElement) { selected?.let { s -> edit(spec.withElement(s.id, transform)) } }

    LaunchedEffect(spec) {
        delay(700)
        if (spec != doc.spec) graph.faces.save(doc.copy(spec = spec))
    }

    val unconfirmed = spec.elements.mapNotNull { it.binding }.filter { !it.verified }.distinct()
    var safety by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(spec) {
        safety = null
        delay(500)
        safety = withContext(Dispatchers.Default) {
            runCatching { FaceSafety.check(graph.compiler.compile(spec).bytes) }.getOrElse { listOf(it.message ?: "Cannot build this face") }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { loadImage(context, uri, spec.width, spec.height, cover = true) } ?: run { status = "Error: could not read image"; return@launch }
            edit(spec.copy(assets = spec.assets + ("background" to encoded), background = spec.background.copy(image = "background")))
        }
    }
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { loadImage(context, uri, 96, 96, cover = false) } ?: run { status = "Error: could not read image"; return@launch }
            val name = "img${spec.assets.size + 1}"
            val e = FaceElement(uniqueId(spec, "image"), "image", 72, 92, 96, 96, src = name)
            edit(spec.copy(assets = spec.assets + (name to encoded), elements = spec.elements + e))
            selectedId = e.id; panel = Panel.STYLE
        }
    }

    var confirmInstall by remember { mutableStateOf(false) }
    var wearAfterInstall by remember { mutableStateOf(false) }

    fun install(freeSpace: Boolean = false): kotlinx.coroutines.Job = scope.launch {
        installing = true
        retry = false
        status = "Compiling"
        val compiled = withContext(Dispatchers.Default) { runCatching { graph.compiler.compile(spec) } }.getOrElse {
            status = "Error: ${it.message}"; installing = false; return@launch
        }
        status = null
        when (val r = graph.session.installFace(compiled.bytes, spec.name, makeCurrent = wearAfterInstall, replaces = doc.watchFaceId, freeSpace = freeSpace)) {
            is FaceInstall.Installed -> { graph.faces.save(doc.copy(spec = spec, watchFaceId = r.faceId)); status = "Installed on watch" }
            is FaceInstall.Failed -> { status = "Error: ${r.reason}"; retry = r.needsSpace && !freeSpace }
        }
        installing = false
    }

    fun export(binary: Boolean) = scope.launch {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = withContext(Dispatchers.Default) {
            if (binary) File(dir, "${spec.fileStem()}.bin").apply { writeBytes(graph.compiler.compile(spec).bytes) }
            else File(dir, "${spec.fileStem()}.sfface").apply { graph.faces.exportZip(spec, this) }
        }
        shareFile(context, file, if (binary) "application/octet-stream" else "application/zip", "Share ${spec.name}")
    }

    Page {
        item {
            BackHeader(spec.name, { nav.popBackStack() }) {
                CircleButton(R.drawable.ic_undo_2, "Undo", { history.undo.removeLastOrNull()?.let { history.redo.add(spec); spec = it } }, enabled = history.undo.isNotEmpty())
                Spacer(Modifier.width(Space.xs))
                CircleButton(R.drawable.ic_redo_2, "Redo", { history.redo.removeLastOrNull()?.let { history.undo.add(spec); spec = it } }, enabled = history.redo.isNotEmpty())
                Spacer(Modifier.width(Space.xs))
                CircleButton(R.drawable.ic_file_code, "Edit code", { scope.launch { graph.faces.save(doc.copy(spec = spec)); nav.navigate(Routes.faceCode(doc.id)) } })
            }
        }
        item {
            EditorCanvas(
                spec = spec,
                selected = selectedId,
                onSelect = { selectedId = it; if (it != null) panel = Panel.STYLE },
                onDragStart = { history.push(spec) },
                onMove = { elementId, x, y -> spec = spec.withElement(elementId) { it.copy(x = x, y = y) } },
                // Only a centred pivot is proven on the watch, so hands are not draggable.
                onMovePivot = { _, _, _ -> },
            )
        }
        item {
            Spacer(Modifier.height(Space.md))
            SegmentedControl(Panel.entries, panel, { panel = it }, label = { it.label })
        }
        when (panel) {
            Panel.LAYERS -> item { LayersPanel(spec, selectedId, { selectedId = it; panel = Panel.STYLE }, ::edit) }
            Panel.STYLE -> item {
                if (selected == null) Txt("Tap an element on the face, or pick one under Layers.", Nd.type.bodySm, Modifier.padding(vertical = Space.lg), c.textDisabled)
                else StylePanel(selected, ::editSelected, onDelete = { edit(spec.copy(elements = spec.elements.filterNot { it.id == selected.id })); selectedId = null })
            }
            Panel.ADD -> item { AddPanel(spec, { e -> edit(spec.copy(elements = spec.elements + e)); selectedId = e.id; panel = Panel.STYLE }, { stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) }
            Panel.FACE -> item {
                FacePanel(spec, ::edit, onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onExport = ::export, onDelete = { confirmDelete = true })
                if (confirmDelete) Row(Modifier.padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Txt("Delete this design?", Nd.type.bodySm, Modifier.weight(1f))
                    NdButton("Cancel", { confirmDelete = false }, style = ButtonStyle.GHOST)
                    NdButton("Delete", { scope.launch { graph.faces.delete(doc.id); nav.popBackStack() } }, style = ButtonStyle.DESTRUCTIVE)
                }
            }
        }
        item {
            Column(Modifier.padding(top = Space.xl)) {
                if (unconfirmed.isNotEmpty()) {
                    InlineStatus("Not supported on this watch: ${unconfirmed.joinToString { it.label }} — remove before installing", color = c.accent)
                    Spacer(Modifier.height(Space.sm))
                } else safety?.let { problems ->
                    InlineStatus(if (problems.isEmpty()) "Safety check passed" else "Safety check: ${problems.first()}", color = if (problems.isEmpty()) c.success else c.accent)
                    Spacer(Modifier.height(Space.sm))
                }
                transfer?.let { t ->
                    Row { Label("${t.phase}", Modifier.weight(1f)); Txt(Format.percent(t.fraction), Nd.type.caption) }
                    Spacer(Modifier.height(Space.xs))
                    SegmentedBar(t.fraction, segments = 32)
                    Spacer(Modifier.height(Space.md))
                }
                status?.let { InlineStatus(it, color = if (it.startsWith("Error")) c.accent else c.textSecondary); Spacer(Modifier.height(Space.sm)) }
                if (retry) ReplacePrompt(installed.filter { it.canRemove }.map { it.name }, { install(freeSpace = true) }) { retry = false }
                FaceTrialBanner()
                if (confirmInstall) InstallConfirm(spec.name, onInstall = { wear -> confirmInstall = false; wearAfterInstall = wear; install() }, onCancel = { confirmInstall = false })
                NdButton(if (connection.isConnected) "Install on watch" else "Connect watch to install",
                    { confirmInstall = true }, Modifier.fillMaxWidth(), enabled = connection.isConnected && !installing && !confirmInstall && unconfirmed.isEmpty() && safety?.isEmpty() == true, icon = R.drawable.ic_upload,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ canvas

@Composable
private fun EditorCanvas(
    spec: FaceSpec,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDragStart: () -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onMovePivot: (String, Int, Int) -> Unit,
) {
    val graph = LocalGraph.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var guides by remember { mutableStateOf(Pair(false, false)) }
    LaunchedEffect(spec) { image = withContext(Dispatchers.Default) { graph.rasterizer.render(spec).asImageBitmap() } }
    val currentSpec by rememberUpdatedState(spec)
    val currentSelected by rememberUpdatedState(selected)
    val c = Nd.colors

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
        val width = maxWidth.coerceAtMost(320.dp)
        val shape = screenShape(width)
        Box(
            Modifier
                .width(width)
                .aspectRatio(spec.width.toFloat() / spec.height)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, c.borderVisible, shape)
                .semantics { contentDescription = "Face canvas. Tap to select, drag to move." }
                .pointerInput(Unit) {
                    fun toFace(o: Offset) = Offset(o.x / size.width * currentSpec.width, o.y / size.height * currentSpec.height)
                    detectTapGestures { offset ->
                        val p = toFace(offset)
                        onSelect(hitTest(graph, currentSpec, p))
                    }
                }
                .pointerInput(Unit) {
                    var dragId: String? = null
                    var acc = Offset.Zero
                    var origin = Offset.Zero
                    detectDragGestures(
                        onDragStart = { offset ->
                            val p = Offset(offset.x / size.width * currentSpec.width, offset.y / size.height * currentSpec.height)
                            val hit = hitTest(graph, currentSpec, p) ?: currentSelected
                            val e = currentSpec.elements.firstOrNull { it.id == hit }
                            dragId = e?.takeIf { !it.locked }?.id
                            if (dragId != null) {
                                onSelect(dragId); onDragStart()
                                origin = if (e!!.type == "hand") Offset(e.pivotX.toFloat(), e.pivotY.toFloat()) else Offset(e.x.toFloat(), e.y.toFloat())
                                acc = Offset.Zero
                            }
                        },
                        onDragEnd = { dragId = null; guides = false to false },
                        onDragCancel = { dragId = null; guides = false to false },
                    ) { change, amount ->
                        val id = dragId ?: return@detectDragGestures
                        change.consume()
                        acc += Offset(amount.x / size.width * currentSpec.width, amount.y / size.height * currentSpec.height)
                        val e = currentSpec.elements.first { it.id == id }
                        var x = (origin.x + acc.x).roundToInt()
                        var y = (origin.y + acc.y).roundToInt()
                        if (e.type == "hand") {
                            onMovePivot(id, x.coerceIn(0, currentSpec.width), y.coerceIn(0, currentSpec.height))
                            return@detectDragGestures
                        }
                        val b = graph.rasterizer.bounds(currentSpec, e.copy(x = x, y = y))
                        val snapX = abs(b.centerX() - currentSpec.width / 2f) < 4f
                        val snapY = abs(b.centerY() - currentSpec.height / 2f) < 4f
                        if (snapX) x += (currentSpec.width / 2f - b.centerX()).roundToInt()
                        if (snapY) y += (currentSpec.height / 2f - b.centerY()).roundToInt()
                        guides = snapX to snapY
                        onMove(id, x.coerceIn(-currentSpec.width / 2, currentSpec.width), y.coerceIn(-currentSpec.height / 2, currentSpec.height))
                    }
                },
        ) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None) }
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / spec.width
                val sy = size.height / spec.height
                if (guides.first) drawLine(c.accent, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                if (guides.second) drawLine(c.accent, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                spec.elements.firstOrNull { it.id == selected }?.let { e ->
                    val b = graph.rasterizer.bounds(spec, e)
                    val tl = Offset(b.left * sx - 3, b.top * sy - 3)
                    val sz = Size(b.width() * sx + 6, b.height() * sy + 6)
                    drawRect(Color.White, tl, sz, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                    listOf(tl, Offset(tl.x + sz.width, tl.y), Offset(tl.x, tl.y + sz.height), Offset(tl.x + sz.width, tl.y + sz.height)).forEach {
                        drawRect(c.accent, Offset(it.x - 3, it.y - 3), Size(6f, 6f))
                    }
                }
            }
        }
    }
}

/** The smallest element under the finger wins, so full-screen rings never block what sits inside them. */
private fun hitTest(graph: fit.silent.app.AppGraph, spec: FaceSpec, p: Offset): String? =
    spec.elements.map { it to graph.rasterizer.bounds(spec, it).apply { inset(-6f, -6f) } }
        .filter { (_, b) -> b.contains(p.x, p.y) }
        .minByOrNull { (_, b) -> b.width() * b.height() }?.first?.id

// ------------------------------------------------------------------ panels

@Composable
private fun LayersPanel(spec: FaceSpec, selected: String?, onSelect: (String) -> Unit, edit: (FaceSpec) -> Unit) {
    Column(Modifier.padding(top = Space.md)) {
        if (spec.elements.isEmpty()) Txt("No layers yet — add some under Add.", Nd.type.bodySm, color = Nd.colors.textDisabled)
        spec.elements.asReversed().forEach { e ->
            val i = spec.elements.indexOf(e)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(e.id) }.padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (e.id == selected) Nd.colors.accent else Color.Transparent))
                Spacer(Modifier.width(Space.sm))
                Icon(layerIcon(e), size = 16.dp, tint = Nd.colors.textSecondary)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Txt(layerName(e), Nd.type.body, maxLines = 1)
                    Label(listOfNotNull(e.type, e.binding?.let { if (it.verified) null else "unsupported" }, if (e.locked) "locked" else null).joinToString(" · "))
                }
                CircleButton(R.drawable.ic_chevron_up, "Bring forward", {
                    if (i < spec.elements.lastIndex) edit(spec.copy(elements = spec.elements.toMutableList().apply { add(i + 1, removeAt(i)) }))
                }, enabled = i < spec.elements.lastIndex)
                Spacer(Modifier.width(Space.xs))
                CircleButton(R.drawable.ic_chevron_down, "Send backward", {
                    if (i > 0) edit(spec.copy(elements = spec.elements.toMutableList().apply { add(i - 1, removeAt(i)) }))
                }, enabled = i > 0)
                Spacer(Modifier.width(Space.xs))
                CircleButton(if (e.locked) R.drawable.ic_lock else R.drawable.ic_lock_open, if (e.locked) "Unlock" else "Lock", {
                    edit(spec.withElement(e.id) { it.copy(locked = !it.locked) })
                })
            }
            Divider()
        }
    }
}

private fun layerName(e: FaceElement) = when {
    e.binding != null -> e.binding!!.label
    e.type == "text" -> "\u201C${e.text.orEmpty().take(18)}\u201D"
    e.type == "shape" -> (e.shape ?: "shape").replace('-', ' ').replaceFirstChar { it.uppercase() }
    else -> e.type.replaceFirstChar { it.uppercase() }
}

private fun layerIcon(e: FaceElement) = when (e.type) {
    "hand" -> R.drawable.ic_clock
    "number", "sprite" -> R.drawable.ic_activity
    "text" -> R.drawable.ic_type
    "image" -> R.drawable.ic_image
    else -> R.drawable.ic_circle
}

@Composable
private fun StylePanel(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(top = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(layerName(e), Nd.type.subheading, Modifier.weight(1f), Nd.colors.textDisplay)
            CircleButton(R.drawable.ic_trash, "Delete element", onDelete, tint = Nd.colors.accent)
        }
        if (e.type == "hand") {
            HandStyle(e, edit)
        } else {
            Stepper("X", e.x, { v -> edit { it.copy(x = v) } }, -240..480)
            Stepper("Y", e.y, { v -> edit { it.copy(y = v) } }, -280..560)
        }
        when (e.type) {
            "number", "sprite" -> {
                BindingPicker(e, edit)
                GlyphStyle(e, edit)
                if (e.glyph == "font") FontPicker(e, edit)
                Stepper("Size", e.size.roundToInt(), { v -> edit { it.copy(size = v.toFloat()) } }, 8..140)
                Label(if (e.isFixedGlyph) "Align in box" else "Grow from", Modifier.padding(top = Space.md))
                Spacer(Modifier.height(Space.sm))
                SegmentedControl(if (e.isFixedGlyph) listOf("left", "center", "right") else listOf("left", "right"), e.align, { v -> edit { it.copy(align = v) } }, label = { it })
                if (e.isFixedGlyph) Stepper("Box width", e.w, { v -> edit { it.copy(w = v) } }, 0..240, step = 4, format = { if (it == 0) "Auto" else "$it px" })
                if (e.type == "sprite") Rotation(e, edit)
            }
            "text" -> {
                TextInput(e.text.orEmpty(), { v -> edit { it.copy(text = v.take(40)) } }, "Text")
                GlyphStyle(e, edit)
                if (e.glyph == "font") FontPicker(e, edit)
                Stepper("Size", e.size.roundToInt(), { v -> edit { it.copy(size = v.toFloat()) } }, 6..140)
                Stepper("Tracking", (e.tracking * 100).roundToInt(), { v -> edit { it.copy(tracking = v / 100f) } }, -10..40, format = { "${it}%" })
                SwitchRow("All caps", e.uppercase, { v -> edit { it.copy(uppercase = v) } })
                Label("Align", Modifier.padding(top = Space.md))
                Spacer(Modifier.height(Space.sm))
                SegmentedControl(listOf("left", "center", "right"), e.align, { v -> edit { it.copy(align = v) } }, label = { it })
                Rotation(e, edit)
            }
            "shape" -> ShapeStyle(e, edit)
            "image" -> {
                Stepper("Width", e.w, { v -> edit { it.copy(w = v) } }, 4..240)
                Stepper("Height", e.h, { v -> edit { it.copy(h = v) } }, 4..280)
                Stepper("Opacity", (e.opacity * 100).roundToInt(), { v -> edit { it.copy(opacity = v / 100f) } }, 10..100, step = 10, format = { "$it%" })
            }
        }
        if (e.type != "image") ColorPicker(e.color) { v -> edit { it.copy(color = v) } }
    }
}

@Composable
private fun BindingPicker(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    val kind = if (e.type == "sprite") BindingKind.SPRITE else BindingKind.NUMBER
    Label("Shows", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Binding.entries.filter { it.kind == kind && it.verified }.forEach { b ->
            Chip(b.label, e.bind == b.key, { edit { it.copy(bind = b.key) } })
        }
    }
}

@Composable
private fun GlyphStyle(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    Label("Glyphs", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    SegmentedControl(listOf("font", "dot-matrix", "segment"), e.glyph, { v -> edit { it.copy(glyph = v) } }, label = { mapOf("font" to "Font", "dot-matrix" to "LED dots", "segment" to "7-segment")[it]!! })
    if (e.glyph != "font") {
        SwitchRow("Show unlit", e.ghost != null, { on -> edit { it.copy(ghost = if (on) "#1F1F1F" else null) } }, subtitle = "Dim dots or segments behind the lit ones")
    }
}

@Composable
private fun Rotation(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    Label("Rotation", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    SegmentedControl(listOf(0, 90, 180, 270), e.rotation, { v -> edit { it.copy(rotation = v) } }, label = { "$it°" })
}

@Composable
private fun FontPicker(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    Label("Typeface", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    SegmentedControl(FONTS.map { it.first }, e.font, { v -> edit { it.copy(font = v, weight = if (v == "doto") 700 else it.weight) } }, label = { k -> FONTS.first { it.first == k }.second })
    if (e.font != "space-mono") {
        Spacer(Modifier.height(Space.sm))
        SegmentedControl(listOf(300, 400, 500, 700), e.weight.let { w -> listOf(300, 400, 500, 700).minBy { abs(it - w) } }, { v -> edit { it.copy(weight = v) } }, label = { mapOf(300 to "Light", 400 to "Regular", 500 to "Medium", 700 to "Bold")[it]!! })
    }
}

@Composable
private fun ShapeStyle(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    Stepper("Width", e.w, { v -> edit { it.copy(w = v) } }, 1..240)
    Stepper("Height", e.h, { v -> edit { it.copy(h = v) } }, 1..280)
    Stepper("Stroke", e.stroke.roundToInt(), { v -> edit { it.copy(stroke = v.toFloat()) } }, 1..24)
    when (e.shape) {
        "ticks", "dot-ring", "segments" -> Stepper("Count", e.count, { v -> edit { it.copy(count = v) } }, 1..120)
        "arc" -> {
            Stepper("Start", e.startAngle.roundToInt(), { v -> edit { it.copy(startAngle = v.toFloat()) } }, 0..359, step = 5, format = { "$it°" })
            Stepper("Sweep", e.sweep.roundToInt(), { v -> edit { it.copy(sweep = v.toFloat()) } }, 5..360, step = 5, format = { "$it°" })
        }
        "rect" -> Stepper("Corner", e.size.roundToInt(), { v -> edit { it.copy(size = v.toFloat()) } }, 0..60)
    }
    if (e.shape == "ticks") Stepper("Tick length", e.size.roundToInt(), { v -> edit { it.copy(size = v.toFloat()) } }, 2..30)
    if (e.shape == "segments") Stepper("Lit", e.sweep.roundToInt(), { v -> edit { it.copy(sweep = v.toFloat()) } }, 0..360, step = 15, format = { "${it * 100 / 360}%" })
    if (e.shape == "rect" || e.shape == "circle") SwitchRow("Filled", e.fill, { v -> edit { it.copy(fill = v) } })
}

@Composable
private fun HandStyle(e: FaceElement, edit: ((FaceElement) -> FaceElement) -> Unit) {
    Label("Hand", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Binding.entries.filter { it.kind == BindingKind.HAND && it.verified }.forEach { b -> Chip(b.label, e.bind == b.key, { edit { it.copy(bind = b.key) } }) }
    }
    Label("Style", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        listOf("bar", "needle", "line", "dots", "dot", "outline").forEach { s -> Chip(s, e.handStyle == s, { edit { it.copy(handStyle = s) } }) }
    }
    Stepper("Length", e.length, { v -> edit { it.copy(length = v) } }, 10..140)
    Stepper("Width", e.stroke.roundToInt(), { v -> edit { it.copy(stroke = v.toFloat()) } }, 1..20)
    Stepper("Tail", e.tail, { v -> edit { it.copy(tail = v) } }, 0..40)
    Txt("Hands turn around the screen centre, like the official faces.", Nd.type.caption, Modifier.padding(top = Space.sm), Nd.colors.textDisabled)
}

@Composable
private fun ColorPicker(value: String, onChange: (String) -> Unit) {
    var hex by remember(value) { mutableStateOf(value) }
    Label("Colour", Modifier.padding(top = Space.md))
    Spacer(Modifier.height(Space.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PALETTE.forEach { p ->
            val active = p.equals(value, true)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(parseColor(p)))
                    .border(if (active) 2.dp else 1.dp, if (active) Nd.colors.textDisplay else Nd.colors.borderVisible, CircleShape)
                    .clickable(role = Role.RadioButton) { onChange(p) }
                    .semantics { contentDescription = "Colour $p" },
            )
        }
    }
    TextInput(hex, { v ->
        hex = v.take(9)
        if (Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$").matches(hex)) onChange(colorHex(parseColor(hex)))
    }, "Hex", mono = true)
}

@Composable
private fun AddPanel(spec: FaceSpec, add: (FaceElement) -> Unit, onPickImage: () -> Unit) {
    data class Item(val label: String, val icon: Int, val make: () -> FaceElement)
    val n = { base: String -> uniqueId(spec, base) }
    val groups = listOf(
        "Time" to listOf(
            Item("Hands", R.drawable.ic_clock) { FaceElement(n("hand"), "hand", bind = Binding.MINUTE_HAND.key, length = 92, stroke = 4f, pivotX = 120, pivotY = 140, size = 0f) },
            Item("Hour", R.drawable.ic_clock) { FaceElement(n("hour"), "number", 30, 90, bind = Binding.HOUR.key, font = "doto", size = 56f, weight = 700) },
            Item("Minute", R.drawable.ic_clock) { FaceElement(n("minute"), "number", 130, 90, bind = Binding.MINUTE.key, font = "doto", size = 56f, weight = 700) },
            Item("AM/PM", R.drawable.ic_sun) { FaceElement(n("ampm"), "sprite", 100, 40, bind = Binding.AM_PM.key, size = 12f) },
        ),
        "Date" to listOf(
            Item("Day", R.drawable.ic_calendar) { FaceElement(n("day"), "number", 110, 220, bind = Binding.DAY.key, size = 16f) },
            Item("Month", R.drawable.ic_calendar) { FaceElement(n("month"), "number", 80, 220, bind = Binding.MONTH.key, size = 16f) },
            Item("Weekday", R.drawable.ic_calendar) { FaceElement(n("weekday"), "sprite", 60, 240, bind = Binding.WEEKDAY.key, size = 13f, color = "#999999") },
        ),
        "Data" to listOf(
            Item("Steps", R.drawable.ic_footprints) { FaceElement(n("steps"), "number", 30, 20, bind = Binding.STEPS.key, size = 16f) },
            Item("Heart rate", R.drawable.ic_heart) { FaceElement(n("hr"), "number", 30, 250, bind = Binding.HEART_RATE.key, size = 16f, color = "#D71921") },
            Item("Calories", R.drawable.ic_flame) { FaceElement(n("kcal"), "number", 150, 20, bind = Binding.CALORIES.key, size = 16f) },
            Item("Battery", R.drawable.ic_battery) { FaceElement(n("battery"), "number", 170, 250, w = 40, bind = Binding.BATTERY.key, size = 16f, align = "right") },
        ),
        "Decoration" to listOf(
            Item("Text", R.drawable.ic_type) { FaceElement(n("text"), "text", 0, 120, 240, text = "SILENTFIT", size = 12f, align = "center", uppercase = true, tracking = 0.1f, color = "#999999") },
            Item("Dot ring", R.drawable.ic_circle_dot) { FaceElement(n("dots"), "shape", 10, 30, 220, 220, shape = "dot-ring", count = 60, stroke = 1.5f, color = "#666666") },
            Item("Ticks", R.drawable.ic_timer) { FaceElement(n("ticks"), "shape", 6, 26, 228, 228, shape = "ticks", count = 60, size = 6f, stroke = 1f, color = "#666666") },
            Item("Arc", R.drawable.ic_circle) { FaceElement(n("arc"), "shape", 20, 40, 200, 200, shape = "arc", startAngle = 0f, sweep = 120f, stroke = 3f) },
            Item("Ring", R.drawable.ic_circle) { FaceElement(n("ring"), "shape", 20, 40, 200, 200, shape = "ring", stroke = 2f, color = "#333333") },
            Item("Rectangle", R.drawable.ic_square) { FaceElement(n("rect"), "shape", 20, 120, 200, 40, shape = "rect", size = 8f, color = "#1A1A1A") },
            Item("Segments", R.drawable.ic_grid_3x3) { FaceElement(n("segments"), "shape", 20, 200, 200, 8, shape = "segments", count = 20, sweep = 216f) },
            Item("Dot matrix", R.drawable.ic_grid_3x3) { FaceElement(n("matrix"), "shape", 16, 16, 208, 24, shape = "dot-matrix", stroke = 1f, color = "#333333") },
            Item("Red dot", R.drawable.ic_circle_dot) { FaceElement(n("dot"), "shape", 200, 40, 10, 10, shape = "circle", color = "#D71921") },
        ),
    )
    Column(Modifier.padding(top = Space.md)) {
        groups.forEach { (title, items) ->
            Section(title) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items.forEach { NdButton(it.label, { add(it.make()) }, style = ButtonStyle.SECONDARY, icon = it.icon) }
                    if (title == "Decoration") NdButton("Image", onPickImage, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_image)
                }
            }
        }
    }
}

@Composable
private fun FacePanel(spec: FaceSpec, edit: (FaceSpec) -> Unit, onPickImage: () -> Unit, onExport: (Boolean) -> Unit, onDelete: () -> Unit) {
    var name by remember(spec.name) { mutableStateOf(spec.name) }
    Column(Modifier.padding(top = Space.md)) {
        TextInput(name, { name = it.take(32); if (name.isNotBlank()) edit(spec.copy(name = name)) }, "Name")
        Section("Background") {
            ColorPicker(spec.background.color) { edit(spec.copy(background = spec.background.copy(color = it))) }
            Label("Pattern", Modifier.padding(top = Space.md))
            Spacer(Modifier.height(Space.sm))
            SegmentedControl(listOf("none", "dots", "grid", "lines"), spec.background.pattern, { edit(spec.copy(background = spec.background.copy(pattern = it))) }, label = { it })
            if (spec.background.pattern != "none") {
                Stepper("Spacing", spec.background.patternSpacing, { edit(spec.copy(background = spec.background.copy(patternSpacing = it))) }, 4..48, step = 2)
                ColorPicker(spec.background.patternColor) { edit(spec.copy(background = spec.background.copy(patternColor = it))) }
            }
            Row(Modifier.padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                NdButton(if (spec.background.image == null) "Photo" else "Replace photo", onPickImage, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_image)
                if (spec.background.image != null) NdButton("Remove", { edit(spec.copy(background = spec.background.copy(image = null), assets = spec.assets - "background")) }, style = ButtonStyle.GHOST)
            }
        }
        Section("Share") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                NdButton(".sfface", { onExport(false) }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_share_2)
                NdButton("Watch file", { onExport(true) }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_share_2)
            }
        }
        Spacer(Modifier.height(Space.lg))
        NdButton("Delete design", onDelete, style = ButtonStyle.DESTRUCTIVE)
    }
}

private fun uniqueId(spec: FaceSpec, base: String): String {
    var i = 1
    while (spec.elements.any { it.id == "$base$i" }) i++
    return "$base$i"
}

/** Loads a picked image and scales it to fit (or cover) [w]×[h], returning base64 PNG. */
private fun loadImage(context: android.content.Context, uri: Uri, w: Int, h: Int, cover: Boolean): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= w * 2 && bounds.outHeight / (sample * 2) >= h * 2) sample *= 2
    val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val scale = if (cover) maxOf(w.toFloat() / src.width, h.toFloat() / src.height) else minOf(w.toFloat() / src.width, h.toFloat() / src.height)
    val scaled = src.scale((src.width * scale).roundToInt().coerceAtLeast(1), (src.height * scale).roundToInt().coerceAtLeast(1))
    val out = if (cover) Bitmap.createBitmap(scaled, ((scaled.width - w) / 2).coerceAtLeast(0), ((scaled.height - h) / 2).coerceAtLeast(0), minOf(w, scaled.width), minOf(h, scaled.height)) else scaled
    val bytes = ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}.getOrNull()
