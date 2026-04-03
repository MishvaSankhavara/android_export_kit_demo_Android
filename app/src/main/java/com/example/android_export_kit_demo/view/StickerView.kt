package com.example.android_export_kit_demo.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.LayerType
import java.io.File
import kotlin.math.*

private const val HANDLE_SIZE_DP = 32f
private const val HANDLE_PAD_DP  = 16f

/**
 * StickerView — High-performance sticker editing composable.
 *
 * Performance design:
 *  - All mid-gesture transforms are stored in LOCAL MutableFloatState (not ViewModel).
 *    This scopes recomposition to just the sticker box, not the whole editor.
 *  - Position uses `offset { IntOffset }` (layout-phase read — avoids composition-phase reads).
 *  - Rotation, flip, and opacity use `graphicsLayer` (render-phase — GPU-accelerated, no layout pass).
 *  - ViewModel is only updated ONCE on gesture end, not on every pointer event.
 *  - LaunchedEffect syncs external ViewModel changes (undo/redo) back to local state.
 *
 * Supports:
 *  - Image stickers: AsyncImage with ContentScale.Fit
 *  - Text stickers: inline BasicTextField on double-tap while selected
 *
 * Handles:
 *  - One-finger drag: move
 *  - Two-finger pinch/rotate: scale + rotate simultaneously
 *  - Top-left: Delete  |  Top-right: Flip  |  Top-center (text): Edit/Done toggle
 *  - Bottom-left: Rotate drag  |  Bottom-right: Resize drag
 */
@Composable
fun StickerView(
    layer: FrameLayer,
    scale: Float,
    isSelected: Boolean,
    isEditing: Boolean = false,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onFlip: () -> Unit,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit,
    onPhotoTransform: ((FrameLayer, Float, Float, Float) -> Unit)? = null,
    onRatioDetected: ((Float) -> Unit)? = null,
    onTextChange: ((String) -> Unit)? = null,
    updateCount: Int = 0,
    shiftY: Float = 0f
) {
    var isEditingText by remember(layer.id) { mutableStateOf(false) }

    val currentLayer by rememberUpdatedState(layer)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnPhotoTransform by rememberUpdatedState(onPhotoTransform)

    // ── Local transform state ──────────────────────────────────────────────────
    // These are the source of truth during a gesture. Reads are scoped only to
    // StickerView — the parent FrameCanvasView is never re-composed mid-gesture.
    val localX      = remember(layer.id) { mutableFloatStateOf(layer.x      * scale) }
    val localY      = remember(layer.id) { mutableFloatStateOf(layer.y      * scale) }
    val localWidth  = remember(layer.id) { mutableFloatStateOf(layer.width  * scale) }
    val localHeight = remember(layer.id) { mutableFloatStateOf(layer.height * scale) }
    val localRot    = remember(layer.id) { mutableFloatStateOf(layer.rotation) }
    val localFS     = remember(layer.id) { mutableFloatStateOf(layer.fontSize) }

    val localPhotoScale = remember(layer.id) { mutableFloatStateOf(layer.photoScale) }
    val localPhotoPanX  = remember(layer.id) { mutableFloatStateOf(layer.photoPanX) }
    val localPhotoPanY  = remember(layer.id) { mutableFloatStateOf(layer.photoPanY) }

    // Sync from ViewModel on external changes (undo/redo/reset)
    // Using a stability threshold to prevent "blinking" caused by micro-rounding differences
    LaunchedEffect(layer.x, layer.y, layer.width, layer.height, layer.rotation, scale, layer.fontSize, layer.photoScale, layer.photoPanX, layer.photoPanY, 
        layer.backgroundColor, layer.backgroundOpacity, layer.backgroundRadius, layer.shadowColor, layer.shadowBlur, layer.shadowOffsetX, layer.shadowOffsetY, updateCount) {
        val targetX = layer.x * scale
        val targetY = layer.y * scale
        val targetW = layer.width * scale
        val targetH = layer.height * scale
        
        if (abs(localX.floatValue - targetX) > 0.5f) localX.floatValue = targetX
        if (abs(localY.floatValue - targetY) > 0.5f) localY.floatValue = targetY
        if (abs(localWidth.floatValue - targetW) > 0.5f) localWidth.floatValue = targetW
        if (abs(localHeight.floatValue - targetH) > 0.5f) localHeight.floatValue = targetH
        
        localRot.floatValue = layer.rotation
        localFS.floatValue = layer.fontSize
        localPhotoScale.floatValue = layer.photoScale
        localPhotoPanX.floatValue = layer.photoPanX
        localPhotoPanY.floatValue = layer.photoPanY
    }

    Box(
        modifier = Modifier
            // Layout-phase offset — reads happen in the layout pass, NOT the composition pass.
            // Changing localX/localY skips recomposition and only triggers a layout update.
            .offset {
                IntOffset(
                    localX.floatValue.dp.roundToPx(),
                    localY.floatValue.dp.roundToPx()
                )
            }
            // Layout-phase size — reads localWidth/Height in layout pass only, avoiding
            // full recomposition when size changes during a two-finger zoom+rotate gesture.
            .layout { measurable, _ ->
                val wPx = localWidth.floatValue.dp.roundToPx()
                val hPx = localHeight.floatValue.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                layout(wPx, hPx) { placeable.place(0, 0) }
            }
            // Rotation + opacity only — flip is applied to content wrapper below,
            // so selection handles always stay at the correct visual corners.
            .graphicsLayer {
                rotationZ       = localRot.floatValue
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                translationY    = shiftY
            }
            // ── Main gesture handler ─────────────────────────────────────────────
            // Uses awaitEachGesture to get a proper "gesture ended" callback so the
            // ViewModel is called exactly ONCE per gesture, not on every pointer event.
            .then(if (!layer.isLocked && isSelected) {
                Modifier.pointerInput(layer.id, isSelected, layer.isLocked) {
                    awaitEachGesture {
                        if (layer.isLocked) return@awaitEachGesture
                        val firstDown = awaitFirstDown(requireUnconsumed = false)

                        // If the touch started inside a handle corner zone, let the handle's
                        // own pointerInput handle it — don't start a drag here.
                        // Handles are 32dp circles centered on each corner; they overlap the
                        // sticker body by half their size (16dp) on each edge.
                        val pos      = firstDown.position
                        val wPx      = localWidth.floatValue  * density
                        val hPx      = localHeight.floatValue * density
                        val cornerPx = HANDLE_PAD_DP * density   // 16dp in pixels
                        val inHandleCorner =
                            (pos.x <= cornerPx  && pos.y <= cornerPx) ||            // top-left  (Delete)
                            (pos.x >= wPx - cornerPx && pos.y <= cornerPx) ||       // top-right (Flip)
                            (pos.x <= cornerPx  && pos.y >= hPx - cornerPx) ||      // bot-left  (Rotate)
                            (pos.x >= wPx - cornerPx && pos.y >= hPx - cornerPx)    // bot-right (Resize)

                        if (!inHandleCorner) {
                            var gx = localX.floatValue
                            var gy = localY.floatValue
                            var gw = localWidth.floatValue
                            var gh = localHeight.floatValue
                            var gr = localRot.floatValue
                            var gfs = localFS.floatValue

                            var pScale = localPhotoScale.floatValue
                            var pPanX  = localPhotoPanX.floatValue
                            var pPanY  = localPhotoPanY.floatValue

                            var didMoveLayer = false
                            var didMovePhoto = false
                            var didZoom      = false
                            var didRotate    = false

                            do {
                                val event = awaitPointerEvent()

                                // calculatePan/Zoom/Rotation handle both 1-finger and 2-finger.
                                // For 1 finger: zoom=1.0, rotation=0.0 (only pan applies).
                                val pan  = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rot  = event.calculateRotation()

                                if (pan != Offset.Zero || zoom != 1f || rot != 0f) {
                                    if (layer.isPhotoSlot && !layer.isSticker && layer.type == LayerType.IMAGE) {
                                        pScale = (pScale * zoom).coerceAtLeast(1f)
                                        // pan is in px; divide by density to get dp, then by scale to get canvas units
                                        pPanX += pan.x / scale / density
                                        pPanY += pan.y / scale / density

                                        // Real-time clamping using detected or stored ratio
                                        val ratio = layer.imageRatio
                                        val slotW = layer.width
                                        val slotH = layer.height

                                        val (maxX, maxY) = if (ratio > 0f) {
                                            val slotRatio = slotW / slotH
                                            val (contentW, contentH) = if (ratio > slotRatio) {
                                                Pair(slotH * ratio, slotH)
                                            } else {
                                                Pair(slotW, slotW / ratio)
                                            }
                                            Pair(
                                                (pScale * contentW - slotW) / 2f,
                                                (pScale * contentH - slotH) / 2f
                                            )
                                        } else {
                                            Pair(
                                                (pScale - 1) * slotW / 2f,
                                                (pScale - 1) * slotH / 2f
                                            )
                                        }

                                        pPanX = if (pScale > 1.01f) pPanX.coerceIn(-max(0f, maxX), max(0f, maxX)) else 0f
                                        pPanY = if (pScale > 1.01f) pPanY.coerceIn(-max(0f, maxY), max(0f, maxY)) else 0f

                                        localPhotoScale.floatValue = pScale
                                        localPhotoPanX.floatValue  = pPanX
                                        localPhotoPanY.floatValue  = pPanY
                                        didMovePhoto = true
                                    } else {
                                        // pan is in px; divide by density to get dp (localX/Y are in dp)
                                        gx += pan.x / density
                                        gy += pan.y / density
                                        if (zoom != 1f) {
                                            gw = (gw * zoom).coerceIn(20f, 1000f)
                                            gh = (gh * zoom).coerceIn(20f, 1000f)
                                            localWidth.floatValue  = gw
                                            if (layer.text != null && localHeight.floatValue > 0) {
                                                gfs = (gfs * zoom).coerceIn(8f, 300f)
                                                localFS.floatValue = gfs
                                            }
                                            didZoom = true
                                        }
                                        if (rot != 0f) {
                                            gr += rot
                                            localRot.floatValue = gr
                                            didRotate = true
                                        }

                                        // Write to local state — only re-draws this sticker
                                        localX.floatValue      = gx
                                        localY.floatValue      = gy
                                        didMoveLayer = true
                                    }
                                }

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.any { it.pressed })

                            // Single ViewModel call at gesture end
                            if (didMoveLayer) {
                                currentOnTransform(
                                    currentLayer,
                                    gx / scale, gy / scale,
                                    if (didZoom) gw / scale else null,
                                    if (didZoom) gh / scale else null,
                                    if (didRotate) gr else null, 
                                    if (didZoom && currentLayer.text != null) gfs else null
                                )
                            }
                            if (didMovePhoto) {
                                currentOnPhotoTransform?.invoke(currentLayer, pScale, pPanX, pPanY)
                            }
                        }
                        // inHandleCorner: block exits here; awaitEachGesture waits for all
                        // pointers up before restarting, so the handle's pointerInput runs cleanly.
                    }
                }
            } else Modifier)
            // Tap: select, or toggle inline text editing for text stickers
            .pointerInput(layer.id, isSelected, layer.isLocked) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (layer.type == LayerType.TEXT) onTap() }
                )
            }
    ) {
        // ── Visual content (flip applied here only, not to handles) ──────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Only apply standard rounded background for image stickers.
                    // Text stickers handle their specialized backgrounds (hearts, bubbles, etc) inside CommonTextLayer.
                    if (layer.type == LayerType.IMAGE && layer.backgroundColor != android.graphics.Color.TRANSPARENT)
                        Modifier.background(Color(layer.backgroundColor).copy(alpha = layer.backgroundOpacity.coerceIn(0f, 1f)), RoundedCornerShape((layer.backgroundRadius * scale).dp)) else Modifier
                )
                .graphicsLayer {
                    scaleX = if (layer.flipH) -1f else 1f
                    scaleY = if (layer.flipV) -1f else 1f
                }
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.opacity.coerceIn(0f, 1f) }) {
                if (layer.text != null) {
                    StickerTextContent(
                        layer,
                        scale,
                        localWidth,
                        localHeight,
                        localFS.floatValue,
                        isEditingText,
                        isEditing || isEditingText,
                        isSelected,
                        updateCount,
                        onTextChange,
                        onTransform
                    )
                } else if (layer.type == LayerType.IMAGE) {
                    StickerImageContent(
                        layer = layer,
                        scale = scale,
                        localPhotoScale = localPhotoScale.floatValue,
                        localPhotoPanX = localPhotoPanX.floatValue,
                        localPhotoPanY = localPhotoPanY.floatValue,
                        onRatioDetected = onRatioDetected,
                        updateCount = updateCount
                    )
                }
            }
        }

        // ── Selection handles — outside the flip wrapper, always at true corners ──
        if (isSelected) {
            StickerSelectionOverlay(
                layer       = layer,
                scale       = scale,
                localWidth  = localWidth,
                localHeight = localHeight,
                localRot    = localRot,
                localFS     = localFS,
                onDelete    = onDelete,
                onFlip      = onFlip,
                onTransform = onTransform
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Image sticker content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerImageContent(
    layer: FrameLayer,
    scale: Float,
    localPhotoScale: Float,
    localPhotoPanX: Float,
    localPhotoPanY: Float,
    onRatioDetected: ((Float) -> Unit)? = null,
    updateCount: Int = 0
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val imageModel: Any? = when {
        layer.customImage != null -> layer.customImage
        layer.src != null         -> File(layer.src!!).takeIf { it.exists() }
        else                      -> null
    }

    // Determine the base dimensions for the image so it covers the slot without distortion.
    // If ratio is detected, we size the composable to the full image size (relative to slot).
    val (imgW, imgH) = if (layer.isPhotoSlot && !layer.isSticker && layer.imageRatio > 0f) {
        val slotRatio = layer.width / layer.height
        if (layer.imageRatio > slotRatio) {
            Pair(layer.height * layer.imageRatio, layer.height)
        } else {
            Pair(layer.width, layer.width / layer.imageRatio)
        }
    } else {
        Pair(layer.width, layer.height)
    }

    // Photo-slot images use Crop (initially) or our custom FullBounds size.
    // Pure sticker images (isSticker=true) use Fit so the whole asset is visible.
    val contentScale = if (layer.isPhotoSlot) {
        if (!layer.isSticker && layer.imageRatio > 0f) ContentScale.FillBounds else ContentScale.Crop
    } else {
        ContentScale.Fit
    }

    if (imageModel != null) {
        // Load the mask image (the original placeholder) if this is a photo slot with a custom image
        val maskPainter = if (layer.isPhotoSlot && !layer.isSticker && layer.customImage != null && layer.src != null) {
            rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(File(layer.src!!))
                    .build()
            )
        } else null

        // Enclose in center-aligned Box so the overflowing AsyncImage is centered by default.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(if (maskPainter != null) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply { blendMode = BlendMode.DstIn }
                                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                                with(maskPainter) {
                                    draw(size)
                                }
                                canvas.restore()
                            }
                        }
                } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = layer.name,
                contentScale       = contentScale,
                colorFilter        = getCombinedColorFilter(layer),
                modifier           = if (layer.isPhotoSlot && !layer.isSticker) {
                    Modifier
                        .requiredSize((imgW * scale).dp, (imgH * scale).dp)
                        .graphicsLayer {
                            translationX = localPhotoPanX * scale * density
                            translationY = localPhotoPanY * scale * density
                            scaleX       = localPhotoScale
                            scaleY       = localPhotoScale
                        }
                } else {
                    Modifier.fillMaxSize()
                },
                onState = { state ->
                    if (state is coil.compose.AsyncImagePainter.State.Success) {
                        val intrinsicSize = state.painter.intrinsicSize
                        if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                            onRatioDetected?.invoke(intrinsicSize.width / intrinsicSize.height)
                        }
                    }
                }
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Text sticker content — static display + inline editing
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerTextContent(
    layer: FrameLayer,
    scale: Float,
    wState: MutableFloatState,
    hState: MutableFloatState,
    fontSize: Float,
    isEditingInline: Boolean,
    isEditing: Boolean,
    isSelected: Boolean,
    updateCount: Int = 0,
    onTextChange: ((String) -> Unit)?,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    CommonTextLayer(
        layer = layer,
        scale = scale,
        wState = wState,
        hState = hState,
        fontSize = fontSize,
        isEditingInline = isEditingInline,
        isEditing = isEditing,
        isSelected = isSelected,
        onTextChange = onTextChange,
        onTransform = onTransform
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Selection overlay — border + handles
// Rotate and resize handles also update local state during drag and commit to
// ViewModel only on gesture end, for the same performance reason as the main gesture.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerSelectionOverlay(
    layer: FrameLayer,
    scale: Float,
    localWidth: MutableFloatState,
    localHeight: MutableFloatState,
    localRot: MutableFloatState,
    localFS: MutableFloatState,
    onDelete: () -> Unit,
    onFlip: () -> Unit,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    CommonSelectionOverlay(
        layer = layer,
        scale = scale,
        localW = localWidth,
        localH = localHeight,
        localRot = localRot,
        localFS = localFS,
        onDelete = onDelete,
        onFlip = onFlip,
        onTransform = onTransform
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Handle button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerHandle(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(HANDLE_SIZE_DP.dp)
            .shadow(4.dp, CircleShape)
            .background(color, CircleShape)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun max(a: Float, b: Float): Float = if (a > b) a else b
