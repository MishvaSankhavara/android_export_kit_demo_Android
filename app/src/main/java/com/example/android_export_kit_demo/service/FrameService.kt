package com.example.android_export_kit_demo.service

import android.content.Context
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class FrameService(private val context: Context) {

    // Singleton pattern
    companion object {
        @Volatile
        private var INSTANCE: FrameService? = null

        fun getInstance(context: Context): FrameService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FrameService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val frameCache = mutableMapOf<String, FrameModel>()
    private val _loadedFrames = mutableListOf<FrameModel>()

    val loadedFrames: List<FrameModel> get() = _loadedFrames.toList()

    // ─────────────────────────────────────────────
    // Load zip from file path
    // ─────────────────────────────────────────────

    suspend fun loadZipFile(zipFilePath: String): List<FrameModel> = withContext(Dispatchers.IO) {
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) throw Exception("Zip file not found: $zipFilePath")

        val tmpDir = context.cacheDir
        val zipName = zipFile.nameWithoutExtension
        val extractDir = File(tmpDir, "frames/$zipName")
        extractDir.mkdirs()

        extractZip(zipFile.inputStream().buffered(), extractDir)

        val frames = parseFramesFromDirectory(extractDir.absolutePath)
        _loadedFrames.addAll(frames)
        frames
    }

    // ─────────────────────────────────────────────
    // Load zip from bytes (e.g. from file picker / SAF)
    // ─────────────────────────────────────────────

    suspend fun loadZipBytes(bytes: ByteArray, zipName: String): List<FrameModel> =
        withContext(Dispatchers.IO) {
            val cleanName = zipName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val timestamp = System.currentTimeMillis()
            val extractDir = File(context.cacheDir, "frames/${cleanName}/$timestamp")
            extractDir.mkdirs()

            extractZip(bytes.inputStream(), extractDir)

            val frames = parseFramesFromDirectory(extractDir.absolutePath)
            _loadedFrames.addAll(frames)
            frames
        }

    // ─────────────────────────────────────────────
    // Extract zip entries into destination directory
    // ─────────────────────────────────────────────

    private fun extractZip(inputStream: java.io.InputStream, destDir: File) {
        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ─────────────────────────────────────────────
    // Parse all frames from extracted directory
    // ─────────────────────────────────────────────

    private fun parseFramesFromDirectory(dirPath: String): List<FrameModel> {
        val frames = mutableListOf<FrameModel>()
        val dir = File(dirPath)

        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "json") {
                try {
                    val content = file.readText()
                    val jsonObj = JSONObject(content)

                    if (jsonObj.has("layers")) {
                        val jsonDir = file.parent ?: dirPath
                        val assetBase = File(jsonDir).parent ?: jsonDir
                        val jsonMap = jsonObj.toMap()

                        val frame = FrameModel.fromJson(jsonMap, extractedDir = assetBase)
                        frame.extractedDir = assetBase
                        resolveLayerAssets(frame, assetBase, jsonDir)
                        frames.add(frame)
                    }
                } catch (_: Exception) {
                    // Skip malformed JSON
                }
            }
        }

        return frames
    }

    // ─────────────────────────────────────────────
    // Resolve relative src paths to absolute paths
    // ─────────────────────────────────────────────

    private fun resolveLayerAssets(frame: FrameModel, assetBase: String, jsonDir: String) {
        for (layer in frame.layers) {
            val src = layer.src ?: continue
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

    // ─────────────────────────────────────────────
    // Resolve font path
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
    // Get thumbnail path for a frame
    // ─────────────────────────────────────────────

    fun getThumbnailPath(frame: FrameModel): String? {
        if (frame.extractedDir == null) return null
        return frame.layers.firstOrNull { layer ->
            layer.type == LayerType.IMAGE &&
                layer.src != null &&
                !layer.isPhotoSlot &&
                File(layer.src!!).exists()
        }?.src
    }

    // ─────────────────────────────────────────────
    // Clear cache
    // ─────────────────────────────────────────────

    fun clearCache() {
        frameCache.clear()
        _loadedFrames.clear()
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
            is org.json.JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

fun org.json.JSONArray.toList(): List<Any?> {
    return (0 until length()).map { i ->
        when (val value = get(i)) {
            is JSONObject -> value.toMap()
            is org.json.JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
}
