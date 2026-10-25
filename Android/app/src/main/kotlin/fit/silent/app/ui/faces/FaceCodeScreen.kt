package fit.silent.app.ui.faces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.FormPage
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.core.watchface.FaceSpec
import kotlinx.coroutines.launch

@Composable
fun FaceCodeScreen(id: String, nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val designs by graph.faces.designs.collectAsStateWithLifecycle()
    val doc = designs.firstOrNull { it.id == id } ?: run {
        FormPage { BackHeader("Code", { nav.popBackStack() }); EmptyState("Design not found", "It may have been deleted.") }
        return
    }
    val original = remember(doc.id) { doc.spec.copy(assets = emptyMap()).toJson() }
    var text by remember(doc.id) { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val c = Nd.colors

    fun apply() = scope.launch {
        val parsed = runCatching { FaceSpec.fromJson(text) }
        parsed.onSuccess { spec ->
            graph.faces.save(doc.copy(spec = spec.copy(assets = doc.spec.assets + spec.assets)))
            error = null; saved = true
        }.onFailure { error = it.message?.lineSequence()?.firstOrNull() ?: "Invalid face file"; saved = false }
    }

    FormPage {
        BackHeader("${doc.spec.name} · code", { nav.popBackStack() }) {
            CircleButton(R.drawable.ic_copy, "Copy JSON", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("face", text))
            })
        }
        Txt("Edit the face as JSON — the same format as .sfface.json files. Images stay attached and are not shown here.", Nd.type.bodySm, color = c.textSecondary)
        Spacer(Modifier.height(Space.md))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .background(c.surface, RoundedCornerShape(8.dp))
                .border(1.dp, if (error != null) c.accent else c.borderVisible, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(Space.md),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; saved = false },
                textStyle = Nd.type.caption.copy(color = c.textPrimary, fontSize = 12.sp, lineHeight = 18.sp),
                cursorBrush = SolidColor(c.textDisplay),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
        }
        Spacer(Modifier.height(Space.md))
        error?.let { InlineStatus("Error: $it", color = c.accent) }
        if (saved) InlineStatus("Saved")
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Apply", { apply() })
            NdButton("Revert", { text = original; error = null }, style = ButtonStyle.GHOST)
        }
    }
}
