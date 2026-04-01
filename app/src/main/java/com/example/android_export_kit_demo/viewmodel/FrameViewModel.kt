package com.example.android_export_kit_demo.viewmodel

import android.content.Context
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.android_export_kit_demo.model.DrawingPoint
import com.example.android_export_kit_demo.model.DrawingStroke
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.service.FrameService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────

data class FrameUiState(
    val frames: List<FrameModel> = emptyList(),
    val selectedFrame: FrameModel? = null,
    val selectedLayer: FrameLayer? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDrawingMode: Boolean = false,
    val currentDrawingStrokes: List<DrawingStroke> = emptyList(),
    val activeStroke: DrawingStroke? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val updateCount: Int = 0,
    val drawingColor: Int = Color.RED,
    val drawingWidth: Float = 5f,
    val isEraser: Boolean = false,
    val drawingMode: String = "normal"
)

// ─────────────────────────────────────────────
// ViewModel (equivalent to Flutter FrameProvider)
// ─────────────────────────────────────────────

class FrameViewModel(private val service: FrameService) : ViewModel() {

    private val _uiState = MutableStateFlow(FrameUiState())
    val uiState: StateFlow<FrameUiState> = _uiState.asStateFlow()

    // ─────────────────────────────────────────────
    // Drawing Settings
    // ─────────────────────────────────────────────

    fun setDrawingColor(color: Int) {
        _uiState.update { it.copy(drawingColor = color) }
    }

    fun setDrawingWidth(width: Float) {
        _uiState.update { it.copy(drawingWidth = width) }
    }

    fun setDrawingEraser(enabled: Boolean) {
        _uiState.update { it.copy(isEraser = enabled) }
    }

    fun setDrawingMode(mode: String) {
        _uiState.update { it.copy(drawingMode = mode) }
    }

    // Drawing settings (compatibility for existing startDrawing calls)
    private val drawingColor: Int get() = _uiState.value.drawingColor
    private val drawingWidth: Float get() = _uiState.value.drawingWidth
    private val isEraser: Boolean get() = _uiState.value.isEraser
    private val drawingMode: String get() = _uiState.value.drawingMode

    // Undo/redo stacks (JSON snapshots)
    private val undoStack = ArrayDeque<List<Map<String, Any?>>>()
    private val redoStack = ArrayDeque<List<Map<String, Any?>>>()

    // ─────────────────────────────────────────────
    // Drawing
    // ─────────────────────────────────────────────

    fun startDrawing(x: Float, y: Float) {
        val stroke = DrawingStroke(
            points = mutableListOf(DrawingPoint(x, y)),
            color = if (isEraser) Color.WHITE else drawingColor,
            width = drawingWidth,
            isEraser = isEraser,
            mode = drawingMode
        )
        _uiState.update { it.copy(activeStroke = stroke) }
    }

    fun updateDrawing(x: Float, y: Float) {
        val active = _uiState.value.activeStroke ?: return
        // We append to the existing mutable list for efficiency during the drag, 
        // but we emit a new state object to trigger recomposition.
        active.points.add(DrawingPoint(x, y))
        _uiState.update { it.copy(updateCount = it.updateCount + 1) }
    }

    fun endDrawing() {
        val active = _uiState.value.activeStroke ?: return
        val updated = if (active.points.size > 1) {
            _uiState.value.currentDrawingStrokes + active
        } else {
            _uiState.value.currentDrawingStrokes
        }
        _uiState.update { it.copy(activeStroke = null, currentDrawingStrokes = updated) }
    }

    fun clearDrawing() {
        _uiState.update { it.copy(currentDrawingStrokes = emptyList(), activeStroke = null) }
    }

    fun undoDrawingStroke() {
        val strokes = _uiState.value.currentDrawingStrokes
        if (strokes.isNotEmpty()) {
            _uiState.update { it.copy(currentDrawingStrokes = strokes.dropLast(1)) }
        }
    }

    fun addDrawingLayer() {
        val strokes = _uiState.value.currentDrawingStrokes
        if (strokes.isEmpty()) {
            _uiState.update { it.copy(isDrawingMode = false) }
            return
        }

        pushUndo()

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        strokes.forEach { s ->
            s.points.forEach { p ->
                if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
            }
        }
        minX -= drawingWidth; minY -= drawingWidth
        maxX += drawingWidth; maxY += drawingWidth

        val normalized = strokes.map { s ->
            DrawingStroke(
                points = s.points.map { DrawingPoint(it.x - minX, it.y - minY) }.toMutableList(),
                color = s.color, width = s.width, isEraser = s.isEraser, mode = s.mode
            )
        }

        val newLayer = FrameLayer(
            id = System.currentTimeMillis().toString(),
            name = "Drawing",
            type = LayerType.DRAWING,
            x = minX, y = minY,
            width = maxX - minX, height = maxY - minY,
            strokes = normalized.toMutableList(),
            isSticker = true
        )

        _uiState.value.selectedFrame?.layers?.add(newLayer)
        _uiState.update { it.copy(isDrawingMode = false, currentDrawingStrokes = emptyList()) }
        notifyLayersChanged()
    }

    fun setDrawingMode(value: Boolean) {
        _uiState.update { it.copy(isDrawingMode = value) }
    }

    // ─────────────────────────────────────────────
    // Loading frames
    // ─────────────────────────────────────────────

    fun loadFramesFromZip(bytes: ByteArray, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val loaded = service.loadZipBytes(bytes, name)
                _uiState.update { state ->
                    state.copy(
                        frames = state.frames + loaded,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load zip: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadFrameFromZipPath(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val loaded = service.loadZipFile(path)
                _uiState.update { state ->
                    state.copy(frames = state.frames + loaded, errorMessage = null)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load zip: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectFrame(frame: FrameModel) {
        undoStack.clear(); redoStack.clear()
        _uiState.update {
            it.copy(selectedFrame = frame, selectedLayer = null, canUndo = false, canRedo = false)
        }
    }

    fun clearFrames() {
        service.clearCache()
        _uiState.update { FrameUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ─────────────────────────────────────────────
    // Layer selection
    // ─────────────────────────────────────────────

    fun selectLayer(layer: FrameLayer?) {
        val frame = _uiState.value.selectedFrame ?: return
        frame.layers.forEach { it.isSelected = false }
        layer?.isSelected = true
        _uiState.update { it.copy(selectedLayer = layer) }
    }

    fun deselectAll() = selectLayer(null)

    // ─────────────────────────────────────────────
    // Layer manipulation
    // ─────────────────────────────────────────────

    fun updateLayerPosition(layer: FrameLayer, dx: Float, dy: Float, skipUndo: Boolean = false) {
        if (layer.isLocked) return
        if (!skipUndo) pushUndo()
        layer.x += dx; layer.y += dy
        notifyLayersChanged()
    }

    fun setLayerPosition(layer: FrameLayer, x: Float, y: Float, skipUndo: Boolean = false) {
        if (layer.isLocked) return
        if (!skipUndo) pushUndo()
        layer.x = x; layer.y = y
        notifyLayersChanged()
    }

    fun setLayerSize(layer: FrameLayer, width: Float, height: Float, skipUndo: Boolean = false) {
        if (layer.isLocked) return
        if (!skipUndo) pushUndo()
        layer.width = width; layer.height = height
        notifyLayersChanged()
    }

    fun setLayerRotation(layer: FrameLayer, rotation: Float, skipUndo: Boolean = false) {
        if (layer.isLocked) return
        if (!skipUndo) pushUndo()
        layer.rotation = rotation
        notifyLayersChanged()
    }

    fun updateLayerText(layer: FrameLayer, text: String) {
        pushUndo(); layer.text = text; notifyLayersChanged()
    }

    fun updateLayerFontSize(layer: FrameLayer, size: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.fontSize = size; notifyLayersChanged()
    }

    fun updateLayerColor(layer: FrameLayer, color: Int, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.color = color; notifyLayersChanged()
    }

    fun updateLayerOpacity(layer: FrameLayer, opacity: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.opacity = opacity; notifyLayersChanged()
    }

    fun updateLayerShadow(
        layer: FrameLayer,
        color: Int? = null, blur: Float? = null,
        offsetX: Float? = null, offsetY: Float? = null,
        skipUndo: Boolean = false
    ) {
        if (!skipUndo) pushUndo()
        color?.let { layer.shadowColor = it }
        blur?.let { layer.shadowBlur = it }
        offsetX?.let { layer.shadowOffsetX = it }
        offsetY?.let { layer.shadowOffsetY = it }
        notifyLayersChanged()
    }

    fun updateLayerStroke(
        layer: FrameLayer,
        color: Int? = null, width: Float? = null,
        skipUndo: Boolean = false
    ) {
        if (!skipUndo) pushUndo()
        color?.let { layer.strokeColor = it }
        width?.let { layer.strokeWidth = it }
        notifyLayersChanged()
    }

    fun toggleLayerBold(layer: FrameLayer) { pushUndo(); layer.isBold = !layer.isBold; notifyLayersChanged() }
    fun toggleLayerItalic(layer: FrameLayer) { pushUndo(); layer.isItalic = !layer.isItalic; notifyLayersChanged() }
    fun toggleLayerUnderline(layer: FrameLayer) { pushUndo(); layer.isUnderline = !layer.isUnderline; notifyLayersChanged() }
    fun toggleLayerStrikethrough(layer: FrameLayer) { pushUndo(); layer.isStrikethrough = !layer.isStrikethrough; notifyLayersChanged() }

    fun updateLayerBackgroundColor(layer: FrameLayer, color: Int, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.backgroundColor = color; notifyLayersChanged()
    }

    fun updateLayerBackgroundRadius(layer: FrameLayer, radius: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.backgroundRadius = radius; notifyLayersChanged()
    }

    fun updateLayerBackgroundOpacity(layer: FrameLayer, opacity: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.backgroundOpacity = opacity; notifyLayersChanged()
    }

    fun updateLayerTextCase(layer: FrameLayer, textCase: String) {
        pushUndo(); layer.textCase = textCase; notifyLayersChanged()
    }

    fun updateLayerFont(layer: FrameLayer, font: String, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.font = font; notifyLayersChanged()
    }

    fun updateLayerCurve(layer: FrameLayer, curve: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.curve = curve; notifyLayersChanged()
    }

    fun applyTextPreset(layer: FrameLayer, presetId: String) {
        pushUndo()
        when (presetId) {
            "Modern" -> {
                layer.font = "Roboto"
                layer.color = Color.BLACK
                layer.isBold = true
                layer.strokeWidth = 0f
                layer.shadowBlur = 0f
            }
            "Elegant" -> {
                layer.font = "Serif"
                layer.color = Color.parseColor("#444444")
                layer.isBold = false
                layer.isItalic = true
                layer.shadowBlur = 4f
                layer.shadowColor = Color.LTGRAY
            }
            "Neon" -> {
                layer.font = "Cursive"
                layer.color = Color.WHITE
                layer.strokeColor = Color.CYAN
                layer.strokeWidth = 2f
                layer.shadowColor = Color.CYAN
                layer.shadowBlur = 10f
            }
            "Outline" -> {
                layer.color = Color.TRANSPARENT
                layer.strokeColor = Color.BLACK
                layer.strokeWidth = 1.5f
                layer.shadowBlur = 0f
            }
        }
        notifyLayersChanged()
    }

    fun updateLayerJustification(layer: FrameLayer, align: String) {
        pushUndo(); layer.justification = align; notifyLayersChanged()
    }

    fun updateLayerLetterSpacing(layer: FrameLayer, spacing: Float) {
        pushUndo(); layer.letterSpacing = spacing; notifyLayersChanged()
    }

    fun updateLayerLineHeight(layer: FrameLayer, height: Float) {
        pushUndo(); layer.lineHeight = height; notifyLayersChanged()
    }

    fun toggleLayerVisibility(layer: FrameLayer) {
        layer.visible = !layer.visible; notifyLayersChanged()
    }


    fun updateLayerTextScript(layer: FrameLayer, script: String) {
        pushUndo(); layer.textScript = script; notifyLayersChanged()
    }

    fun clearFormatting(layer: FrameLayer) {
        pushUndo()
        layer.isBold = false; layer.isItalic = false
        layer.isUnderline = false; layer.isStrikethrough = false
        layer.backgroundColor = Color.TRANSPARENT
        layer.textCase = "none"; layer.textScript = "normal"
        notifyLayersChanged()
    }

    fun panPhoto(layer: FrameLayer, dx: Float, dy: Float) {
        if (layer.isLocked) return
        if (dx == 0f && dy == 0f) return
        pushUndo()
        layer.photoPanX += dx; layer.photoPanY += dy
        clampPhoto(layer); notifyLayersChanged()
    }

    fun updateLayerRatio(layer: FrameLayer, ratio: Float) {
        if (Math.abs(layer.imageRatio - ratio) < 0.001f) return
        layer.imageRatio = ratio
        clampPhoto(layer)
        notifyLayersChanged()
    }

    fun updatePhotoTransform(
        layer: FrameLayer, 
        scale: Float, 
        panX: Float, 
        panY: Float
    ) {
        var changed = false
        if (Math.abs(layer.photoScale - scale) > 0.001f) {
            layer.photoScale = scale
            changed = true
        }
        if (Math.abs(layer.photoPanX - panX) > 0.001f || Math.abs(layer.photoPanY - panY) > 0.001f) {
            layer.photoPanX = panX
            layer.photoPanY = panY
            changed = true
        }
        
        if (changed) {
            clampPhoto(layer)
            notifyLayersChanged()
        }
    }

    fun setPhotoScale(layer: FrameLayer, scale: Float, skipUndo: Boolean = false) {
        if (Math.abs(layer.photoScale - scale) < 0.001f) return
        if (!skipUndo) pushUndo()
        layer.photoScale = scale; clampPhoto(layer); notifyLayersChanged()
    }

    private fun clampPhoto(layer: FrameLayer) {
        if (layer.photoScale < 1f) layer.photoScale = 1f

        val ratio = layer.imageRatio
        if (ratio <= 0f) {
            // Fallback to simple clamping if ratio is unknown
            val maxX = (layer.photoScale - 1) * layer.width / 2f
            val maxY = (layer.photoScale - 1) * layer.height / 2f
            layer.photoPanX = layer.photoPanX.coerceIn(-maxX, maxX)
            layer.photoPanY = layer.photoPanY.coerceIn(-maxY, maxY)
            return
        }

        // Improved clamping taking image aspect ratio into account.
        // We ensure the image always covers the slot (layer.width x layer.height).
        val slotW = layer.width
        val slotH = layer.height
        val slotRatio = slotW / slotH

        val (contentW, contentH) = if (ratio > slotRatio) {
            // Image is wider than slot (constrained by height)
            Pair(slotH * ratio, slotH)
        } else {
            // Image is taller than or equal to slot (constrained by width)
            Pair(slotW, slotW / ratio)
        }

        val maxX = (layer.photoScale * contentW - slotW) / 2f
        val maxY = (layer.photoScale * contentH - slotH) / 2f

        layer.photoPanX = if (layer.photoScale > 1.01f) layer.photoPanX.coerceIn(-max(0f, maxX), max(0f, maxX)) else 0f
        layer.photoPanY = if (layer.photoScale > 1.01f) layer.photoPanY.coerceIn(-max(0f, maxY), max(0f, maxY)) else 0f
    }

    private fun max(a: Float, b: Float): Float = if (a > b) a else b

    fun setLayerCustomImage(layer: FrameLayer, image: File?) {
        if (layer.isLocked) return
        pushUndo()
        layer.customImage = image
        if (image == null) { layer.photoScale = 1f; layer.photoPanX = 0f; layer.photoPanY = 0f }
        notifyLayersChanged()
    }

    fun assignImagesToFrame(frame: FrameModel, images: List<File>) {
        pushUndo()
        val slots = frame.layers.filter { it.isPhotoSlot }
        images.forEachIndexed { i, file -> if (i < slots.size) slots[i].customImage = file }
        notifyLayersChanged()
    }

    fun markAsPhotoSlot(layer: FrameLayer) {
        layer.isPhotoSlot = !layer.isPhotoSlot; notifyLayersChanged()
    }

    fun reorderLayer(oldIndex: Int, newIndex: Int) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val item = frame.layers.removeAt(oldIndex)
        frame.layers.add(newIndex, item)
        notifyLayersChanged()
    }

    fun duplicateLayer(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val idx = frame.layers.indexOf(layer)
        val copy = layer.copyWith(x = layer.x + 10f, y = layer.y + 10f)
        frame.layers.add(idx + 1, copy)
        notifyLayersChanged()
    }

    fun deleteLayer(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.remove(layer)
        val newSelected = if (_uiState.value.selectedLayer == layer) null else _uiState.value.selectedLayer
        _uiState.update { it.copy(selectedLayer = newSelected) }
        notifyLayersChanged()
    }

    fun toggleLayerFlip(layer: FrameLayer) { pushUndo(); layer.flipH = !layer.flipH; notifyLayersChanged() }
    fun toggleLayerFlipV(layer: FrameLayer) { pushUndo(); layer.flipV = !layer.flipV; notifyLayersChanged() }
    fun toggleLayerLock(layer: FrameLayer) { pushUndo(); layer.isLocked = !layer.isLocked; notifyLayersChanged() }

    fun updateLayerFilter(layer: FrameLayer, filter: String, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.filter = filter; notifyLayersChanged()
    }

    fun updateLayerBrightness(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.brightness = value; notifyLayersChanged()
    }

    fun updateLayerContrast(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.contrast = value; notifyLayersChanged()
    }

    fun updateLayerSaturation(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.saturation = value; notifyLayersChanged()
    }

    fun updateLayerWarmth(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.warmth = value; notifyLayersChanged()
    }

    fun updateLayerFade(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.fade = value; notifyLayersChanged()
    }

    fun updateLayerHighlights(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.highlights = value; notifyLayersChanged()
    }

    fun updateLayerShadows(layer: FrameLayer, value: Float, skipUndo: Boolean = false) {
        if (!skipUndo) pushUndo()
        layer.shadows = value; notifyLayersChanged()
    }

    fun applyGlobalFilter(filter: String) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.filter = filter
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalBrightness(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.brightness = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalContrast(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.contrast = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalSaturation(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.saturation = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalWarmth(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.warmth = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalFade(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.fade = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalHighlights(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.highlights = value
            }
        }
        notifyLayersChanged()
    }

    fun applyGlobalShadows(value: Float) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.shadows = value
            }
        }
        notifyLayersChanged()
    }

    fun resetGlobalFiltersAndAdjustments() {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { layer ->
            if (layer.type == LayerType.IMAGE && layer.isPhotoSlot && !layer.isBackground && layer.customImage != null) {
                layer.filter = "none"
                layer.brightness = 0f
                layer.contrast = 1f
                layer.saturation = 1f
                layer.warmth = 0f
                layer.fade = 0f
                layer.highlights = 1f
                layer.shadows = 1f
            }
        }
        notifyLayersChanged()
    }


    fun scaleLayer(layer: FrameLayer, factor: Float) {
        if (layer.isLocked) return
        pushUndo()
        if (layer.type == LayerType.TEXT) {
            layer.fontSize = (layer.fontSize * factor).coerceIn(8f, 200f)
        } else if (layer.isPhotoSlot && !layer.isSticker) {
            layer.photoScale = (layer.photoScale * factor).coerceAtLeast(1f)
            clampPhoto(layer)
        } else {
            layer.width = (layer.width * factor).coerceAtLeast(20f)
            layer.height = (layer.height * factor).coerceAtLeast(20f)
        }
        notifyLayersChanged()
    }

    fun resetLayerToOriginal(layer: FrameLayer) {
        pushUndo(); layer.resetToOriginal(); notifyLayersChanged()
    }

    fun addTextLayer(text: String) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val w = 500f
        val h = 100f
        val newLayer = FrameLayer(
            id = "text_${System.currentTimeMillis()}",
            name = "Text Layer",
            type = LayerType.TEXT,
            x = (frame.canvasWidth - w) / 2,
            y = (frame.canvasHeight - h) / 3,
            width = w, height = h,
            text = text, fontSize = 84f, color = Color.BLACK,
            isSticker = true
        )
        frame.layers.add(newLayer)
        selectLayer(newLayer)
        notifyLayersChanged()
    }

    fun addStickerLayer(assetPath: String) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val newLayer = FrameLayer(
            id = "sticker_${System.currentTimeMillis()}",
            name = "Sticker",
            type = LayerType.IMAGE,
            x = (frame.canvasWidth - 120) / 2,
            y = (frame.canvasHeight - 120) / 2,
            width = 120f, height = 120f,
            src = assetPath, isSticker = true
        )
        frame.layers.add(newLayer)
        selectLayer(newLayer)
        notifyLayersChanged()
    }

    fun addTextStickerLayer(text: String) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val newLayer = FrameLayer(
            id        = "text_sticker_${System.currentTimeMillis()}",
            name      = "Text Sticker",
            type      = LayerType.IMAGE,
            x         = (frame.canvasWidth - 200) / 2,
            y         = frame.canvasHeight / 3,
            width     = 200f,
            height    = 80f,
            text      = text,
            fontSize  = 48f,
            color     = Color.BLACK,
            isSticker = true
        )
        frame.layers.add(newLayer)
        selectLayer(newLayer)
        notifyLayersChanged()
    }

    fun addImageStickerFromFile(file: File) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        val newLayer = FrameLayer(
            id          = "sticker_${System.currentTimeMillis()}",
            name        = "Sticker",
            type        = LayerType.IMAGE,
            x           = (frame.canvasWidth - 150) / 2,
            y           = (frame.canvasHeight - 150) / 2,
            width       = 150f,
            height      = 150f,
            customImage = file,
            isSticker   = true
        )
        frame.layers.add(newLayer)
        selectLayer(newLayer)
        notifyLayersChanged()
    }

    fun bringLayerToFront(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.remove(layer); frame.layers.add(layer)
        notifyLayersChanged()
    }

    fun bringForward(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        val idx = frame.layers.indexOf(layer)
        if (idx != -1 && idx < frame.layers.size - 1) {
            pushUndo()
            frame.layers.removeAt(idx); frame.layers.add(idx + 1, layer)
            notifyLayersChanged()
        }
    }

    fun sendLayerToBack(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        val idx = frame.layers.indexOf(layer)
        if (idx > 0) {
            pushUndo()
            frame.layers.removeAt(idx)
            frame.layers.add(0, layer)
            notifyLayersChanged()
        }
    }

    fun sendBackward(layer: FrameLayer) {
        val frame = _uiState.value.selectedFrame ?: return
        val idx = frame.layers.indexOf(layer)
        if (idx > 0) {
            pushUndo()
            frame.layers.removeAt(idx); frame.layers.add(idx - 1, layer)
            notifyLayersChanged()
        }
    }

    fun resetAllLayers() {
        val frame = _uiState.value.selectedFrame ?: return
        pushUndo()
        frame.layers.forEach { it.resetToOriginal(); it.customImage = null }
        notifyLayersChanged()
    }

    // ─────────────────────────────────────────────
    // Undo / Redo
    // ─────────────────────────────────────────────

    fun pushUndo() {
        val frame = _uiState.value.selectedFrame ?: return
        val snapshot = frame.layers.map { it.toJson() }
        undoStack.addLast(snapshot)
        redoStack.clear()
        if (undoStack.size > 20) undoStack.removeFirst()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val frame = _uiState.value.selectedFrame ?: return
        val current = frame.layers.map { it.toJson() }
        redoStack.addLast(current)
        restoreSnapshot(undoStack.removeLast())
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val frame = _uiState.value.selectedFrame ?: return
        val current = frame.layers.map { it.toJson() }
        undoStack.addLast(current)
        restoreSnapshot(redoStack.removeLast())
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreSnapshot(snapshot: List<Map<String, Any?>>) {
        val frame = _uiState.value.selectedFrame ?: return
        snapshot.forEachIndexed { i, s ->
            if (i < frame.layers.size) {
                val layer = frame.layers[i]
                layer.x = (s["x"] as Number).toFloat()
                layer.y = (s["y"] as Number).toFloat()
                layer.width = (s["width"] as Number).toFloat()
                layer.height = (s["height"] as Number).toFloat()
                layer.rotation = (s["rotation"] as? Number)?.toFloat() ?: 0f
                s["text"]?.let { layer.text = it as String }
                s["size"]?.let { layer.fontSize = (it as Number).toFloat() }
                layer.photoScale = (s["photoScale"] as? Number)?.toFloat() ?: 1f
                layer.photoPanX = (s["photoPanX"] as? Number)?.toFloat() ?: 0f
                layer.photoPanY = (s["photoPanY"] as? Number)?.toFloat() ?: 0f
                layer.imageRatio = (s["imageRatio"] as? Number)?.toFloat() ?: 0f
                layer.opacity = (s["opacity"] as? Number)?.toFloat() ?: 1f
                s["font"]?.let { layer.font = it as String }
                s["shadowColor"]?.let { layer.shadowColor = (it as Number).toInt() }
                layer.shadowBlur = (s["shadowBlur"] as? Number)?.toFloat() ?: 0f
                layer.shadowOffsetX = (s["shadowOffsetX"] as? Number)?.toFloat() ?: 0f
                layer.shadowOffsetY = (s["shadowOffsetY"] as? Number)?.toFloat() ?: 0f
                s["strokeColor"]?.let { layer.strokeColor = (it as Number).toInt() }
                layer.strokeWidth = (s["strokeWidth"] as? Number)?.toFloat() ?: 0f
                layer.backgroundRadius = (s["backgroundRadius"] as? Number)?.toFloat() ?: 0f
                layer.isLocked = (s["isLocked"] as? Boolean) ?: false
            }
        }
        notifyLayersChanged()
    }

    // Force state recomposition by emitting a new copy
    private fun notifyLayersChanged() {
        _uiState.update { it.copy(updateCount = it.updateCount + 1) }
    }
}

// ─────────────────────────────────────────────
// ViewModelFactory
// ─────────────────────────────────────────────

class FrameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FrameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FrameViewModel(FrameService.getInstance(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
