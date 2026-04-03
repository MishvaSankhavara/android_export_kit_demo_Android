package com.example.android_export_kit_demo.repository

import android.content.Context
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
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
                        val assetBase = File(jsonDir).parent ?: jsonDir

                        val jsonMap = jsonData.toMap()
                        val frame = FrameModel.fromJson(jsonMap, extractedDir = assetBase)
                        frame.extractedDir = assetBase
                        resolveLayerAssets(frame, assetBase, jsonDir)
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
        val extractedDir = frame.extractedDir ?: assetBase

        for (layer in frame.layers) {
            if (layer.src != null) {
                val src = layer.src!!
                
                // 1. Try resolving relative to jsonDir (folder with the .json)
                var resolvedFile = File(jsonDir, src)

                // 2. If not found and not absolute, try relative to assetBase
                if (!resolvedFile.exists() && !src.startsWith("/")) {
                    resolvedFile = File(assetBase, src)
                }

                // 3. AGGRESSIVE: Search in "skins/" folder for the filename if still not found
                if (!resolvedFile.exists()) {
                    val fileName = src.substringAfterLast("/")
                    val skinsDir = findSkinsDir(File(extractedDir))
                    if (skinsDir != null) {
                        val potentialFile = File(skinsDir, fileName)
                        if (potentialFile.exists()) {
                            resolvedFile = potentialFile
                        }
                    }
                }


                if (resolvedFile.exists()) {
                    layer.src = resolvedFile.absolutePath
                    // If it's in a skins folder, it should NOT be a photo slot
                    if (resolvedFile.absolutePath.contains("skins", ignoreCase = true)) {
                        layer.isPhotoSlot = false
                        layer.isSticker = true
                        layer.type = LayerType.IMAGE
                    }

                } else {
                    // Final fallback: canonical path for ../ logic
                    if (src.startsWith("../")) {
                        try {
                            layer.src = File(jsonDir, src).canonicalPath
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 4. Auto-discover additional stickers from skins folder
        discoverExtraSkins(frame, extractedDir)
    }

    private fun findSkinsDir(dir: File): File? {
        if (dir.name.equals("skins", ignoreCase = true)) return dir
        return dir.listFiles()?.firstNotNullOfOrNull { file ->
            if (file.isDirectory) findSkinsDir(file) else null
        }
    }

    private fun discoverExtraSkins(frame: FrameModel, extractedDir: String) {
        val skinsDir = findSkinsDir(File(extractedDir)) ?: return
        val existingPaths = frame.layers.mapNotNull { it.src }.toSet()
        var offsetCount = 0

        skinsDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile && (file.extension == "png" || file.extension == "jpg" || file.extension == "jpeg" || file.extension == "webp")) {
                if (!existingPaths.contains(file.absolutePath)) {
                    val isBackground = file.name.contains("BG", ignoreCase = true) || file.name.contains("background", ignoreCase = true)

                    // Add as a new sticker layer at the VERY BACK (index 0)
                    val newLayer = FrameLayer(
                        id = "skin_${file.nameWithoutExtension}_${System.currentTimeMillis()}",
                        name = if (isBackground) "Background" else "Sticker",
                        type = LayerType.IMAGE,
                        x = if (isBackground) 0f else (frame.canvasWidth / 4 + (offsetCount * 20)),
                        y = if (isBackground) 0f else (frame.canvasHeight / 4 + (offsetCount * 20)),
                        width = if (isBackground) frame.canvasWidth else 200f,
                        height = if (isBackground) frame.canvasHeight else 200f,
                        src = file.absolutePath,
                        isSticker = true,
                        isPhotoSlot = false,
                        isBackground = isBackground
                    )
                    // Insert at beginning of list to put behind everything else (like user photos)
                    frame.layers.add(0, newLayer)
                    if (!isBackground) offsetCount++
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

// ─────────────────────────────────────────────
// Extension: JSONObject → Map
// ─────────────────────────────────────────────

fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { key ->
        map[key] = when (val value = get(key)) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

fun JSONArray.toList(): List<Any?> {
    return (0 until length()).map { i ->
        when (val value = get(i)) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
}

