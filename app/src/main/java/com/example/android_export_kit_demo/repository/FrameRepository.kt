package com.example.android_export_kit_demo.uime.repository

import android.content.Context
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class FrameRepository(private val context: Context) {

    private val frameCache = mutableMapOf<String, FrameModel>()
    private val loadedFrames = mutableListOf<FrameModel>()

    // ─────────────────────────────────────────────
    // Load ZIP from InputStream (e.g., content URI)
    // ─────────────────────────────────────────────

    suspend fun loadZipFromStream(inputStream: InputStream, zipName: String): List<FrameModel> =
        withContext(Dispatchers.IO) {
            val cleanName = zipName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val tmpDir = context.cacheDir
            val extractDir = File(tmpDir, "frames/${cleanName}/${System.currentTimeMillis()}")
            extractDir.mkdirs()

            // Extract zip
            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(extractDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val frames = parseFramesFromDirectory(extractDir.absolutePath)
            loadedFrames.addAll(frames)
            frames
        }

    // ─────────────────────────────────────────────
    // Load ZIP from byte array
    // ─────────────────────────────────────────────

    suspend fun loadZipBytes(bytes: ByteArray, zipName: String): List<FrameModel> =
        withContext(Dispatchers.IO) {
            loadZipFromStream(bytes.inputStream(), zipName)
        }

    // ─────────────────────────────────────────────
    // Load ZIP from file path
    // ─────────────────────────────────────────────

    suspend fun loadZipFile(filePath: String): List<FrameModel> =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) throw Exception("Zip file not found: $filePath")
            loadZipFromStream(file.inputStream(), file.nameWithoutExtension)
        }

    // ─────────────────────────────────────────────
    // Parse frames from extracted directory
    // ─────────────────────────────────────────────

    private fun parseFramesFromDirectory(dirPath: String): List<FrameModel> {
        val frames = mutableListOf<FrameModel>()
        val dir = File(dirPath)

        // Walk directory recursively looking for JSON files
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.equals("json", ignoreCase = true)) {
                try {
                    val content = file.readText()
                    val jsonData = JSONObject(content)

                    // Must have 'layers' to be a valid frame JSON
                    if (jsonData.has("layers")) {
                        val jsonDir = file.parent ?: dirPath
                        // The skin/asset base is parent of json folder
                        val assetBase = File(jsonDir).parent ?: dirPath

                        val frame = FrameModel.fromJson(jsonData as Map<String, Any>, extractedDir = assetBase)
                        frame.extractedDir?.let { resolveLayerAssets(frame, assetBase, jsonDir) }
                        frames.add(frame)
                    }
                } catch (e: Exception) {
                    // Skip malformed JSON
                    e.printStackTrace()
                }
            }
        }
        return frames
    }

    // ─────────────────────────────────────────────
    // Resolve relative asset paths to absolute paths
    // ─────────────────────────────────────────────

    private fun resolveLayerAssets(frame: FrameModel, assetBase: String, jsonDir: String) {
        for (layer in frame.layers) {
            if (layer.src != null) {
                val src = layer.src!!
                layer.src = when {
                    src.startsWith("../") -> {
                        File(jsonDir, src).canonicalPath
                    }
                    !src.startsWith("/") -> {
                        File(assetBase, src).absolutePath
                    }
                    else -> src
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Font resolution
    // ─────────────────────────────────────────────

    fun resolveFontPath(frame: FrameModel, fontName: String): String? {
        val extractedDir = frame.extractedDir ?: return null
        val fontsDir = File(extractedDir, "fonts")
        if (!fontsDir.exists()) return null

        return fontsDir.listFiles()?.firstOrNull { file ->
            val fname = file.nameWithoutExtension
            fname.equals(fontName, ignoreCase = true) ||
            fname.contains(fontName, ignoreCase = true)
        }?.absolutePath
    }

    // ─────────────────────────────────────────────
    // Thumbnail resolution
    // ─────────────────────────────────────────────

    fun getThumbnailPath(frame: FrameModel): String? {
        frame.extractedDir ?: return null
        return frame.layers.firstOrNull { layer ->
            layer.type == LayerType.IMAGE && layer.src != null && !layer.isPhotoSlot
        }?.src?.let { path ->
            if (File(path).exists()) path else null
        }
    }

    // ─────────────────────────────────────────────
    // Cache management
    // ─────────────────────────────────────────────

    fun clearCache() {
        frameCache.clear()
        loadedFrames.clear()
    }

    fun clearExtractedFiles() {
        try {
            val framesDir = File(context.cacheDir, "frames")
            framesDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
