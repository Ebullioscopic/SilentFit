package fit.silent.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.Base64
import fit.silent.core.watchface.FaceSafety
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.WatchFaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class FaceDocument(
    val id: String,
    val spec: FaceSpec,
    val updatedAt: Long,
    /** Id the face was installed under on the watch, reused so reinstalling replaces it. */
    val watchFaceId: String? = null,
    val presetKey: String? = null,
)

/** A ready-made "WF" file imported as-is (not editable). */
data class FaceBinary(val id: String, val name: String, val file: File, val size: Long)

sealed interface ImportResult {
    data class Design(val doc: FaceDocument) : ImportResult
    data class Binary(val binary: FaceBinary) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/** The user's face designs (JSON) and imported watch-face binaries, stored in app-private files. */
class FaceLibrary(root: File) {
    private val designsDir = File(root, "faces").apply { mkdirs() }
    private val binariesDir = File(root, "face-binaries").apply { mkdirs() }
    private val _designs = MutableStateFlow(loadDesigns())
    private val _binaries = MutableStateFlow(loadBinaries())
    val designs: StateFlow<List<FaceDocument>> = _designs.asStateFlow()
    val binaries: StateFlow<List<FaceBinary>> = _binaries.asStateFlow()

    private fun loadDesigns() = designsDir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
        runCatching { AppJson.decodeFromString(FaceDocument.serializer(), f.readText()) }.getOrNull()
    }.sortedByDescending { it.updatedAt }

    private fun loadBinaries() = binariesDir.listFiles { f -> f.extension == "bin" }.orEmpty().map { f ->
        FaceBinary(f.nameWithoutExtension, f.nameWithoutExtension.substringAfter("__", f.nameWithoutExtension), f, f.length())
    }.sortedBy { it.name }

    fun get(id: String) = _designs.value.firstOrNull { it.id == id }

    suspend fun save(doc: FaceDocument): FaceDocument = withContext(Dispatchers.IO) {
        val stamped = doc.copy(updatedAt = System.currentTimeMillis())
        val atomic = AtomicFile(File(designsDir, "${doc.id}.json"))
        val out = atomic.startWrite()
        try {
            out.write(AppJson.encodeToString(FaceDocument.serializer(), stamped).toByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
        _designs.update { list -> (listOf(stamped) + list.filterNot { it.id == doc.id }).sortedByDescending { it.updatedAt } }
        stamped
    }

    suspend fun create(spec: FaceSpec, presetKey: String? = null) = save(FaceDocument(UUID.randomUUID().toString(), spec, 0, presetKey = presetKey))

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(designsDir, "$id.json").delete()
        _designs.update { list -> list.filterNot { it.id == id } }
    }

    suspend fun deleteBinary(b: FaceBinary) = withContext(Dispatchers.IO) {
        b.file.delete()
        _binaries.value = loadBinaries()
    }

    /** Imports a face from any supported container: `.sfface.json`, `.sfface` (zip) or a raw "WF" file. */
    suspend fun import(resolver: ContentResolver, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(resolver, uri)
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext ImportResult.Failed("Could not read the file")
        importBytes(name, bytes)
    }

    suspend fun importBytes(name: String, bytes: ByteArray): ImportResult = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_IMPORT) return@withContext ImportResult.Failed("File is larger than ${MAX_IMPORT / 1_048_576} MB")
        try {
            when {
                WatchFaceFile.isFace(bytes) -> {
                    FaceSafety.check(bytes).takeIf { it.isNotEmpty() }?.let { problems ->
                        return@withContext ImportResult.Failed("This watch file is not proven safe for the NF 2: ${problems.first()}")
                    }
                    WatchFaceFile.decode(bytes)
                    val safe = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").take(40).ifBlank { "Imported face" }
                    val file = File(binariesDir, "${UUID.randomUUID().toString().take(8)}__$safe.bin")
                    file.writeBytes(bytes)
                    _binaries.value = loadBinaries()
                    ImportResult.Binary(FaceBinary(file.nameWithoutExtension, safe, file, file.length()))
                }
                bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() ->
                    ImportResult.Design(create(unzip(bytes)))
                else -> ImportResult.Design(create(FaceSpec.fromJson(bytes.toString(Charsets.UTF_8))))
            }
        } catch (e: Exception) {
            ImportResult.Failed(e.message ?: "Unsupported file")
        }
    }

    /** `.sfface` zip: `face.json` plus optional `assets/<name>.png` files, inlined into the spec. */
    private fun unzip(bytes: ByteArray): FaceSpec {
        var json: String? = null
        val assets = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            var total = 0L
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    total += data.size
                    require(total <= MAX_IMPORT) { "Archive is too large" }
                    val n = entry.name.substringAfterLast('/')
                    when {
                        entry.name.endsWith(".json") && json == null -> json = data.toString(Charsets.UTF_8)
                        n.endsWith(".png", true) || n.endsWith(".jpg", true) || n.endsWith(".jpeg", true) ->
                            assets[n.substringBeforeLast('.')] = Base64.encodeToString(data, Base64.NO_WRAP)
                    }
                }
                entry = zip.nextEntry
            }
        }
        val spec = FaceSpec.fromJson(json ?: throw IllegalArgumentException("face.json missing from archive"))
        return spec.copy(assets = spec.assets + assets)
    }

    fun exportZip(spec: FaceSpec, target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("face.json"))
            zip.write(spec.copy(assets = emptyMap()).toJson().toByteArray())
            zip.closeEntry()
            spec.assets.forEach { (name, b64) ->
                zip.putNextEntry(ZipEntry("assets/$name.png"))
                zip.write(Base64.decode(b64, Base64.DEFAULT))
                zip.closeEntry()
            }
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "face"

    companion object {
        const val MAX_IMPORT = 8 * 1_048_576
    }
}
