package fit.silent.app.ui.faces

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.data.FaceBinary
import fit.silent.app.data.ImportResult
import fit.silent.app.facestudio.Presets
import fit.silent.app.ui.ImportRequests
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.Routes
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Card
import fit.silent.app.ui.components.Chip
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.PageTitle
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedBar
import fit.silent.app.ui.components.StatusDot
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.watch.FaceInstall
import fit.silent.app.watch.WatchSession
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FacesScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val designs by graph.faces.designs.collectAsStateWithLifecycle()
    val binaries by graph.faces.binaries.collectAsStateWithLifecycle()
    val installed by graph.session.faces.collectAsStateWithLifecycle()
    val connection by graph.session.connection.collectAsStateWithLifecycle()
    val transfer by graph.session.transfer.collectAsStateWithLifecycle()
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val pendingImport by ImportRequests.pending.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var busy by remember { mutableStateOf(false) }
    val c = Nd.colors

    fun handleImport(uri: Uri) = scope.launch {
        when (val r = graph.faces.import(context.contentResolver, uri)) {
            is ImportResult.Design -> { status = "Imported ${r.doc.spec.name}"; nav.navigate(Routes.face(r.doc.id)) }
            is ImportResult.Binary -> status = "Imported watch file ${r.binary.name}"
            is ImportResult.Failed -> status = "Error: ${r.reason}"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { handleImport(it) } }
    LaunchedEffect(pendingImport) { pendingImport?.let { ImportRequests.pending.value = null; handleImport(it) } }

    fun install(name: String, bytes: suspend () -> ByteArray, wear: Boolean, freeSpace: Boolean = false): kotlinx.coroutines.Job = scope.launch {
        busy = true
        status = null
        retry = null
        val result = graph.session.installFace(withContext(Dispatchers.Default) { bytes() }, name, makeCurrent = wear, freeSpace = freeSpace)
        status = when (result) {
            is FaceInstall.Installed -> "Installed $name"
            is FaceInstall.Failed -> {
                if (result.needsSpace && !freeSpace) retry = { install(name, bytes, wear, freeSpace = true) }
                "Error: ${result.reason}"
            }
        }
        busy = false
    }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> ByteArray>?>(null) }

    Page {
        item {
            PageTitle("Faces", eyebrow = "Studio") {
                CircleButton(R.drawable.ic_download, "Import face file", { picker.launch(arrayOf("*/*")) })
                Spacer(Modifier.width(Space.sm))
                CircleButton(R.drawable.ic_plus, "New design", {
                    scope.launch { nav.navigate(Routes.face(graph.faces.create(Presets.dotMatrix.spec.copy(name = "My face")).id)) }
                })
            }
        }
        transfer?.let { t ->
            item {
                Card(Modifier.padding(bottom = Space.md)) {
                    Row { Label("${t.phase} · ${t.title}", Modifier.weight(1f)); Txt(Format.percent(t.fraction), Nd.type.caption) }
                    Spacer(Modifier.height(Space.sm))
                    SegmentedBar(t.fraction, segments = 32)
                }
            }
        }
        status?.let { item { InlineStatus(it, Modifier.padding(bottom = Space.md), if (it.startsWith("Error")) c.accent else c.textSecondary) } }
        item { FaceTrialBanner() }
        confirm?.let { (name, bytes) ->
            item { InstallConfirm(name, onInstall = { wear -> confirm = null; install(name, bytes, wear) }, onCancel = { confirm = null }) }
        }
        retry?.let { again -> item { ReplacePrompt(installed.filter { it.canRemove }.map { it.name }, again) { retry = null } } }

        item {
            Section("On your watch", trailing = {
                if (connection.isConnected) CircleButton(R.drawable.ic_refresh_cw, "Refresh", { graph.session.refreshFaces() })
            }) {
                if (!connection.isConnected) Txt("Connect your watch to manage its faces.", Nd.type.bodySm, color = c.textDisabled)
                else if (installed.isEmpty()) Txt("Reading faces…", Nd.type.bodySm, color = c.textDisabled)
                installed.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !f.isCurrent) { graph.session.setFace(f.id) }.padding(vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(if (f.isCurrent) c.accent else c.borderVisible)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Txt(f.name, Nd.type.body, maxLines = 1)
                            Txt(listOfNotNull(f.id, if (f.isCurrent) "current" else null, if (f.canEdit) "editable" else null).joinToString(" · ").uppercase(), Nd.type.label, color = c.textDisabled)
                        }
                        if (f.canRemove && !f.isCurrent) CircleButton(R.drawable.ic_trash, "Remove ${f.name}", { graph.session.removeFace(f.id) })
                    }
                    Divider()
                }
            }
        }

        item {
            Section("My designs") {
                if (designs.isEmpty()) Txt("Start from a preset below or tap + to design your own.", Nd.type.bodySm, color = c.textDisabled)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md), maxItemsInEachRow = 2) {
                    designs.forEach { doc ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { nav.navigate(Routes.face(doc.id)) }) {
                            FacePreview(doc.spec, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(Space.sm))
                            Txt(doc.spec.name, Nd.type.bodySm, maxLines = 1)
                            Label(if (doc.watchFaceId != null) "On watch · ${doc.watchFaceId}" else "Draft", color = c.textDisabled)
                        }
                    }
                    if (designs.size % 2 == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        item {
            Section("Presets") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.lg), maxItemsInEachRow = 2) {
                    Presets.all.forEach { p ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                            scope.launch { nav.navigate(Routes.face(graph.faces.create(p.spec, p.key).id)) }
                        }) {
                            FacePreview(p.spec, Modifier.fillMaxWidth(), "${p.title} preset")
                            Spacer(Modifier.height(Space.sm))
                            Txt(p.title, Nd.type.bodySm, maxLines = 1)
                            Label("Ready", color = c.textDisabled)
                        }
                    }
                }
            }
        }

        if (binaries.isNotEmpty()) item {
            Section("Imported watch files") {
                binaries.forEach { b -> BinaryRow(b, connection.isConnected && !busy && confirm == null, { confirm = b.name to suspend { b.file.readBytes() } }, { scope.launch { graph.faces.deleteBinary(b) } }) }
            }
        }

        item {
            Spacer(Modifier.height(Space.xl))
            Txt("Formats: .sfface (zip), .sfface.json (code) and raw watch files (WF). See docs/WATCHFACE_DESIGN.md.", Nd.type.caption, color = c.textDisabled)
        }
        if (designs.isEmpty() && binaries.isEmpty() && installed.isEmpty() && !connection.isConnected) item { EmptyState("Your face studio", "Designs you create or import live here.") }
    }
}

@Composable
private fun BinaryRow(b: FaceBinary, canInstall: Boolean, onInstall: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        BinaryPreview(b.file, Modifier.width(56.dp), b.name)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Txt(b.name, Nd.type.body, maxLines = 1)
            Label("${b.size / 1024} KB · WF")
        }
        NdButton("Install", onInstall, style = ButtonStyle.SECONDARY, enabled = canInstall)
        Spacer(Modifier.width(Space.xs))
        CircleButton(R.drawable.ic_trash, "Delete ${b.name}", onDelete)
    }
    Divider()
}

/** Explicit consent before anything is written to the watch. */
@Composable
fun InstallConfirm(name: String, onInstall: (wear: Boolean) -> Unit, onCancel: () -> Unit) {
    Card(Modifier.padding(bottom = Space.md)) {
        Txt("Install $name?", Nd.type.subheading, color = Nd.colors.textDisplay)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "The file has passed the safety check against the official face layout. " +
                "If you wear it now, SilentFit watches the connection for ${WatchSession.TRIAL_MS / 60_000} minutes and switches back to a built-in face if the watch restarts.",
            Nd.type.bodySm, color = Nd.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Install & wear", { onInstall(true) })
            NdButton("Install only", { onInstall(false) }, style = ButtonStyle.SECONDARY)
            NdButton("Cancel", onCancel, style = ButtonStyle.GHOST)
        }
    }
}

/** Shows a running face trial and any message from the watchdog. */
@Composable
fun FaceTrialBanner() {
    val session = LocalGraph.current.session
    val trial by session.faceTrial.collectAsStateWithLifecycle()
    val notice by session.notice.collectAsStateWithLifecycle()
    val c = Nd.colors
    trial?.let { t ->
        Card(Modifier.padding(bottom = Space.md)) {
            Label("Trying face ${t.faceId}")
            Spacer(Modifier.height(Space.sm))
            Txt("Keep the watch near your phone. If it restarts in the next few minutes, SilentFit restores a built-in face.", Nd.type.bodySm, color = c.textSecondary)
            Spacer(Modifier.height(Space.md))
            NdButton("It works — keep it", { session.keepTrialFace() }, style = ButtonStyle.SECONDARY)
        }
    }
    notice?.let { n ->
        Card(Modifier.padding(bottom = Space.md)) {
            Txt(n, Nd.type.bodySm, color = c.textPrimary)
            Spacer(Modifier.height(Space.sm))
            NdButton("OK", { session.clearNotice() }, style = ButtonStyle.GHOST)
        }
    }
}

/** The NF 2 keeps one downloadable face; installing another means replacing it. */
@Composable
fun ReplacePrompt(removable: List<String>, onReplace: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.padding(bottom = Space.md)) {
        Txt("Your watch is full", Nd.type.subheading, color = Nd.colors.textDisplay)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "It keeps one downloaded face at a time. Replace ${removable.joinToString().ifEmpty { "the current downloaded face" }} with this one? " +
                "Built-in faces are not affected.",
            Nd.type.bodySm, color = Nd.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Replace", onReplace, style = ButtonStyle.DESTRUCTIVE)
            NdButton("Cancel", onCancel, style = ButtonStyle.GHOST)
        }
    }
}

/** Shares a file from the app cache through the FileProvider. */
fun shareFile(context: android.content.Context, file: java.io.File, mime: String, title: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun FaceSpec.fileStem() = name.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifEmpty { "face" }.lowercase()
