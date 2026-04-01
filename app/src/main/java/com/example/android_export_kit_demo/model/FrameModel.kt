package com.example.android_export_kit_demo.model

import android.graphics.Color
import java.io.File

// ─────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────

enum class LayerType {
    IMAGE, TEXT, ICON, DRAWING
}

// ─────────────────────────────────────────────
// Drawing Models
// ─────────────────────────────────────────────

data class DrawingPoint(
    val x: Float,
    val y: Float
) {
    fun toJson(): Map<String, Any> = mapOf("x" to x, "y" to y)

    companion object {
        fun fromJson(json: Map<String, Any>): DrawingPoint = DrawingPoint(
            x = (json["x"] as Number).toFloat(),
            y = (json["y"] as Number).toFloat()
        )
    }
}

data class DrawingStroke(
    val points: MutableList<DrawingPoint> = mutableListOf(),
    val color: Int = Color.RED,
    val width: Float = 5f,
    val isEraser: Boolean = false,
    val mode: String = "normal" // 'normal', 'dotted', 'neon', 'neon_dotted', 'rainbow'
) {
    fun toJson(): Map<String, Any> = mapOf(
        "points" to points.map { it.toJson() },
        "color" to color,
        "width" to width,
        "isEraser" to isEraser,
        "mode" to mode
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any>): DrawingStroke = DrawingStroke(
            points = (json["points"] as List<Map<String, Any>>)
                .map { DrawingPoint.fromJson(it) }.toMutableList(),
            color = (json["color"] as Number).toInt(),
            width = (json["width"] as Number).toFloat(),
            isEraser = json["isEraser"] as? Boolean ?: false,
            mode = json["mode"] as? String ?: "normal"
        )
    }
}

// ─────────────────────────────────────────────
// Frame Layer
// ─────────────────────────────────────────────

data class FrameLayer(
    val id: String,
    var name: String,
    var type: LayerType,

    // Position & size
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var rotation: Float = 0f,

    // Image layer
    var src: String? = null,
    var customImage: File? = null,
    var isPhotoSlot: Boolean = false,
    var photoScale: Float = 1f,
    var photoPanX: Float = 0f,
    var photoPanY: Float = 0f,
    var imageRatio: Float = 0f,

    // Text layer
    var text: String? = null,
    var font: String? = null,
    var justification: String = "center",
    var color: Int = Color.BLACK,
    var fontSize: Float = 16f,

    // Visibility
    var visible: Boolean = true,
    var isSelected: Boolean = false,
    var isBackground: Boolean = false,
    var flipH: Boolean = false,
    var flipV: Boolean = false,
    var isSticker: Boolean = false,
    var isLocked: Boolean = false,
    var filter: String = "none",

    // Styling
    var opacity: Float = 1f,
    var shadowColor: Int = Color.TRANSPARENT,
    var shadowBlur: Float = 0f,
    var shadowOffsetX: Float = 0f,
    var shadowOffsetY: Float = 0f,
    var strokeColor: Int = Color.TRANSPARENT,
    var strokeWidth: Float = 0f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var isStrikethrough: Boolean = false,
    var backgroundColor: Int = Color.TRANSPARENT,
    var textCase: String = "none", // 'none', 'lowercase', 'uppercase', 'titlecase'
    var textScript: String = "normal", // 'normal', 'subscript', 'superscript'
    var backgroundRadius: Float = 0f,
    var strokes: MutableList<DrawingStroke>? = null
) {
    // Original values for reset
    val origX: Float = x
    val origY: Float = y
    val origWidth: Float = width
    val origHeight: Float = height
    val origText: String? = text
    val origFontSize: Float = fontSize
    val origColor: Int = color

    fun resetToOriginal() {
        x = origX
        y = origY
        width = origWidth
        height = origHeight
        if (origText != null) text = origText
        fontSize = origFontSize
        color = origColor
        photoScale = 1f
        photoPanX = 0f
        photoPanY = 0f
        imageRatio = 0f
        opacity = 1f
        shadowColor = Color.TRANSPARENT
        shadowBlur = 0f
        shadowOffsetX = 0f
        shadowOffsetY = 0f
        strokeColor = Color.TRANSPARENT
        strokeWidth = 0f
        isBold = false
        isItalic = false
        isUnderline = false
        isStrikethrough = false
        backgroundColor = Color.TRANSPARENT
        textCase = "none"
        textScript = "normal"
        isLocked = false
        rotation = 0f
        flipH = false
        flipV = false
        filter = "none"
        backgroundRadius = 0f
    }

    fun toJson(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "type" to when (type) { LayerType.TEXT -> "text"; else -> "image" },
        "x" to x,
        "y" to y,
        "width" to width,
        "height" to height,
        "rotation" to rotation,
        "src" to src,
        "text" to text,
        "font" to font,
        "justification" to justification,
        "color" to "0x${Integer.toHexString(color).uppercase()}",
        "size" to fontSize,
        "is_bg" to isBackground,
        "photoScale" to photoScale,
        "photoPanX" to photoPanX,
        "photoPanY" to photoPanY,
        "imageRatio" to imageRatio,
        "is_sticker" to isSticker,
        "opacity" to opacity,
        "shadowColor" to shadowColor,
        "shadowBlur" to shadowBlur,
        "shadowOffsetX" to shadowOffsetX,
        "shadowOffsetY" to shadowOffsetY,
        "strokeColor" to strokeColor,
        "strokeWidth" to strokeWidth,
        "isBold" to isBold,
        "isItalic" to isItalic,
        "isUnderline" to isUnderline,
        "isStrikethrough" to isStrikethrough,
        "backgroundColor" to backgroundColor,
        "textCase" to textCase,
        "isLocked" to isLocked,
        "flipV" to flipV,
        "filter" to filter,
        "backgroundRadius" to backgroundRadius,
        "strokes" to strokes?.map { it.toJson() }
    )

    fun copyWith(
        name: String? = null,
        x: Float? = null,
        y: Float? = null,
        width: Float? = null,
        height: Float? = null,
        rotation: Float? = null,
        src: String? = null,
        customImage: File? = null,
        isPhotoSlot: Boolean? = null,
        text: String? = null,
        font: String? = null,
        justification: String? = null,
        color: Int? = null,
        fontSize: Float? = null,
        visible: Boolean? = null,
        isSelected: Boolean? = null,
        isBackground: Boolean? = null,
        isSticker: Boolean? = null,
        photoScale: Float? = null,
        photoPanX: Float? = null,
        photoPanY: Float? = null,
        imageRatio: Float? = null,
        opacity: Float? = null,
        shadowColor: Int? = null,
        shadowBlur: Float? = null,
        shadowOffsetX: Float? = null,
        shadowOffsetY: Float? = null,
        strokeColor: Int? = null,
        strokeWidth: Float? = null,
        isBold: Boolean? = null,
        isItalic: Boolean? = null,
        isUnderline: Boolean? = null,
        isStrikethrough: Boolean? = null,
        backgroundColor: Int? = null,
        textCase: String? = null,
        textScript: String? = null,
        isLocked: Boolean? = null,
        flipV: Boolean? = null,
        filter: String? = null,
        backgroundRadius: Float? = null,
        strokes: MutableList<DrawingStroke>? = null
    ): FrameLayer = FrameLayer(
        id = this.id,
        name = name ?: this.name,
        type = this.type,
        x = x ?: this.x,
        y = y ?: this.y,
        width = width ?: this.width,
        height = height ?: this.height,
        rotation = rotation ?: this.rotation,
        src = src ?: this.src,
        customImage = customImage ?: this.customImage,
        isPhotoSlot = isPhotoSlot ?: this.isPhotoSlot,
        text = text ?: this.text,
        font = font ?: this.font,
        justification = justification ?: this.justification,
        color = color ?: this.color,
        fontSize = fontSize ?: this.fontSize,
        visible = visible ?: this.visible,
        isSelected = isSelected ?: this.isSelected,
        isBackground = isBackground ?: this.isBackground,
        isSticker = isSticker ?: this.isSticker,
        photoScale = photoScale ?: this.photoScale,
        photoPanX = photoPanX ?: this.photoPanX,
        photoPanY = photoPanY ?: this.photoPanY,
        imageRatio = imageRatio ?: this.imageRatio,
        opacity = opacity ?: this.opacity,
        shadowColor = shadowColor ?: this.shadowColor,
        shadowBlur = shadowBlur ?: this.shadowBlur,
        shadowOffsetX = shadowOffsetX ?: this.shadowOffsetX,
        shadowOffsetY = shadowOffsetY ?: this.shadowOffsetY,
        strokeColor = strokeColor ?: this.strokeColor,
        strokeWidth = strokeWidth ?: this.strokeWidth,
        isBold = isBold ?: this.isBold,
        isItalic = isItalic ?: this.isItalic,
        isUnderline = isUnderline ?: this.isUnderline,
        isStrikethrough = isStrikethrough ?: this.isStrikethrough,
        backgroundColor = backgroundColor ?: this.backgroundColor,
        textCase = textCase ?: this.textCase,
        textScript = textScript ?: this.textScript,
        isLocked = isLocked ?: this.isLocked,
        flipV = flipV ?: this.flipV,
        filter = filter ?: this.filter,
        backgroundRadius = backgroundRadius ?: this.backgroundRadius,
        strokes = strokes ?: this.strokes
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): FrameLayer {
            val typeStr = json["type"] as? String ?: "image"
            val layerType = when (typeStr) {
                "text" -> LayerType.TEXT
                "drawing" -> LayerType.DRAWING
                else -> LayerType.IMAGE
            }

            var parsedColor = Color.BLACK
            val colorStr = json["color"] as? String
            if (colorStr != null) {
                try {
                    val hex = colorStr.replace("0x", "")
                    parsedColor = Color.parseColor("#FF$hex")
                } catch (_: Exception) {}
            }

            val name = json["name"] as? String ?: ""
            val isPhotoSlot = (json["is_profile"] == true) ||
                name.lowercase().contains("portrait") ||
                name.lowercase().contains("photo") ||
                name.lowercase().contains("user") ||
                name.lowercase().contains("picture") ||
                name.lowercase().contains("person") ||
                name.lowercase().contains("boy") ||
                name.lowercase().contains("girl") ||
                name.lowercase().contains("baby")

            val strokesJson = json["strokes"] as? List<Map<String, Any>>
            return FrameLayer(
                id = json["id"]?.toString() ?: java.util.UUID.randomUUID().toString(),
                name = name,
                type = layerType,
                x = (json["x"] as? Number)?.toFloat() ?: 0f,
                y = (json["y"] as? Number)?.toFloat() ?: 0f,
                width = (json["width"] as? Number)?.toFloat() ?: 100f,
                height = (json["height"] as? Number)?.toFloat() ?: 100f,
                rotation = (json["rotation"] as? Number)?.toFloat() ?: 0f,
                src = json["src"] as? String,
                isPhotoSlot = isPhotoSlot,
                text = json["text"] as? String,
                font = json["font"] as? String,
                justification = json["justification"] as? String ?: "center",
                color = parsedColor,
                fontSize = (json["size"] as? Number)?.toFloat() ?: 16f,
                visible = true,
                isBackground = json["is_bg"] as? Boolean ?: false,
                isSticker = json["is_sticker"] as? Boolean ?: false,
                photoScale = (json["photoScale"] as? Number)?.toFloat() ?: 1f,
                photoPanX = (json["photoPanX"] as? Number)?.toFloat() ?: 0f,
                photoPanY = (json["photoPanY"] as? Number)?.toFloat() ?: 0f,
                imageRatio = (json["imageRatio"] as? Number)?.toFloat() ?: 0f,
                opacity = (json["opacity"] as? Number)?.toFloat() ?: 1f,
                shadowColor = (json["shadowColor"] as? Number)?.toInt() ?: Color.TRANSPARENT,
                shadowBlur = (json["shadowBlur"] as? Number)?.toFloat() ?: 0f,
                shadowOffsetX = (json["shadowOffsetX"] as? Number)?.toFloat() ?: 0f,
                shadowOffsetY = (json["shadowOffsetY"] as? Number)?.toFloat() ?: 0f,
                strokeColor = (json["strokeColor"] as? Number)?.toInt() ?: Color.TRANSPARENT,
                strokeWidth = (json["strokeWidth"] as? Number)?.toFloat() ?: 0f,
                isBold = json["isBold"] as? Boolean ?: false,
                isItalic = json["isItalic"] as? Boolean ?: false,
                isUnderline = json["isUnderline"] as? Boolean ?: false,
                isStrikethrough = json["isStrikethrough"] as? Boolean ?: false,
                backgroundColor = (json["backgroundColor"] as? Number)?.toInt() ?: Color.TRANSPARENT,
                textCase = json["textCase"] as? String ?: "none",
                textScript = json["textScript"] as? String ?: "normal",
                isLocked = json["isLocked"] as? Boolean ?: false,
                flipV = json["flipV"] as? Boolean ?: false,
                filter = json["filter"] as? String ?: "none",
                backgroundRadius = (json["backgroundRadius"] as? Number)?.toFloat() ?: 0f,
                strokes = strokesJson?.map { DrawingStroke.fromJson(it) }?.toMutableList()
            )
        }
    }
}

// ─────────────────────────────────────────────
// Frame Info
// ─────────────────────────────────────────────

data class FrameInfo(
    val title: String,
    val description: String,
    val date: String,
    val file: String,
    val author: String
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): FrameInfo = FrameInfo(
            title = json["title"] as? String ?: "Untitled",
            description = json["description"] as? String ?: "",
            date = json["date"] as? String ?: "",
            file = json["file"] as? String ?: "",
            author = json["author"] as? String ?: ""
        )
    }
}

// ─────────────────────────────────────────────
// Frame Model
// ─────────────────────────────────────────────

data class FrameModel(
    val id: String,
    val name: String,
    val path: String,
    val info: FrameInfo,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val layers: MutableList<FrameLayer>,
    var zipPath: String? = null,
    var extractedDir: String? = null
) {
    val displayTitle: String get() = if (info.title.isNotEmpty()) info.title else name

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>, extractedDir: String? = null): FrameModel {
            val rawLayers = json["layers"] as? List<Map<String, Any?>> ?: emptyList()
            val layers = rawLayers.map { FrameLayer.fromJson(it) }.toMutableList()

            // Determine canvas size
            var w = (json["canvasWidth"] as? Number)?.toFloat()
                ?: (json["width"] as? Number)?.toFloat() ?: 0f
            var h = (json["canvasHeight"] as? Number)?.toFloat()
                ?: (json["height"] as? Number)?.toFloat() ?: 0f

            if (w <= 0 || h <= 0) {
                var bestArea = 0f
                for (layer in layers) {
                    if (layer.type == LayerType.IMAGE) {
                        val area = layer.width * layer.height
                        val atOrigin = Math.abs(layer.x) < 1 && Math.abs(layer.y) < 1
                        if (atOrigin && area > bestArea) {
                            bestArea = area
                            w = layer.width
                            h = layer.height
                        }
                    }
                }
            }

            if (w <= 0) w = 512f
            if (h <= 0) h = 720f

            // Auto-detect background layer
            for (layer in layers) {
                if (!layer.isBackground &&
                    layer.type == LayerType.IMAGE &&
                    Math.abs(layer.x) < 1 &&
                    Math.abs(layer.y) < 1 &&
                    Math.abs(layer.width - w) < 1 &&
                    Math.abs(layer.height - h) < 1) {
                    layer.isBackground = true
                }
            }

            val infoJson = json["info"] as? Map<String, Any?> ?: emptyMap()
            return FrameModel(
                id = json["name"]?.toString() ?: java.util.UUID.randomUUID().toString(),
                name = json["name"]?.toString() ?: "Frame",
                path = json["path"]?.toString() ?: "",
                info = FrameInfo.fromJson(infoJson),
                canvasWidth = w,
                canvasHeight = h,
                layers = layers,
                extractedDir = extractedDir
            )
        }
    }
}
