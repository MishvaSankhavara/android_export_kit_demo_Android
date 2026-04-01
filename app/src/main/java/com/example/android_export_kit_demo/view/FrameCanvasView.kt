package com.example.android_export_kit_demo.view

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ColorMatrix as AndroidColorMatrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import com.example.android_export_kit_demo.model.DrawingStroke
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.AppColors
import java.io.File
import kotlin.math.*

private const val HANDLE_SIZE = 32f   // dp
private const val HANDLE_PAD  = 16f   // dp padding around selection border

// ─────────────────────────────────────────────────────────────────────────────
// FrameCanvasView
// Kotlin/Compose equivalent of Flutter's FrameCanvas widget.
// All layers are positioned from JSON x/y/w/h/rotation values scaled to the
// available canvas size.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FrameCanvasView(
    frame: FrameModel,
    selectedLayer: FrameLayer?,
    isDrawingMode: Boolean,
    currentDrawingStrokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    updateCount: Int = 0,
    keyboardShiftPx: Float = 0f,
    onBitmapCaptured: (Bitmap?) -> Unit,
    onLayerTap: (FrameLayer) -> Unit,
    onLayerTransform: (
        layer: FrameLayer,
        x: Float?, y: Float?,
        width: Float?, height: Float?,
        rotation: Float?, fontSize: Float?
    ) -> Unit,
    onPhotoTransform: (layer: FrameLayer, scale: Float, panX: Float, panY: Float) -> Unit,
    onRatioDetected: (layer: FrameLayer, ratio: Float) -> Unit,
    onStickerTextChange: (layer: FrameLayer, text: String) -> Unit,
    onDeleteLayer: (FrameLayer) -> Unit,
    onToggleFlip: (FrameLayer) -> Unit,
    onDrawingStart: (x: Float, y: Float) -> Unit,
    onDrawingUpdate: (x: Float, y: Float) -> Unit,
    onDrawingEnd: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val canvasAspect = frame.canvasWidth / frame.canvasHeight
        val availW = maxWidth.value
        val availH = maxHeight.value

        val (canvasW, canvasH) = if (availW / availH > canvasAspect) {
            Pair(availH * canvasAspect, availH)
        } else {
            Pair(availW, availW / canvasAspect)
        }

        // Scale factor: JSON logical pixels → rendered dp
        val scale = canvasW / frame.canvasWidth
        val density = LocalDensity.current.density
        val canvasScale = scale * density // pixels per logical unit

        Box(
            modifier = Modifier
                .size(canvasW.dp, canvasH.dp)
                .shadow(20.dp, clip = false)
                .background(Color.White)
                .clipToBounds()
        ) {
            // Deselect tap target (behind all layers)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedLayer) {
                        detectTapGestures {
                            if (selectedLayer != null) {
                                onLayerTap(
                                    FrameLayer(
                                        id = "__deselect__", name = "",
                                        type = LayerType.IMAGE,
                                        x = 0f, y = 0f, width = 0f, height = 0f
                                    )
                                )
                            }
                        }
                    }
            )

            // Render all layers.
            // StickerView handles:
            //   1. Explicit sticker layers (isSticker=true, non-drawing)
            //   2. Photo-slot layers that have an image assigned — giving them free
            //      drag / pinch-to-scale / rotate instead of the confined slot gesture.
            // LayerView handles everything else (backgrounds, empty slots, drawing stickers).
            frame.layers.filter { it.visible }.forEachIndexed { index, layer ->
                key(layer.id + "_" + index) {
                val isSelected = layer.isSelected
                val renderAsStickerView =
                    (layer.type == LayerType.TEXT) ||
                    (layer.isSticker && layer.type != LayerType.DRAWING) ||
                    (layer.isPhotoSlot && !layer.isBackground && layer.customImage != null)
                val layerShiftPx = if (isSelected && layer.type == LayerType.TEXT) keyboardShiftPx else 0f

                if (renderAsStickerView) {
                    StickerView(
                        layer        = layer,
                        scale        = scale,
                        isSelected   = isSelected,
                        updateCount  = updateCount,
                        shiftY       = layerShiftPx,
                        onTap        = { onLayerTap(layer) },
                        onTransform  = onLayerTransform,
                        onPhotoTransform = onPhotoTransform,
                        onRatioDetected  = { ratio -> onRatioDetected(layer, ratio) },
                        onTextChange = if (layer.type == LayerType.TEXT) {
                            { text -> onStickerTextChange(layer, text) }
                        } else null,
                        onDelete     = { onDeleteLayer(layer) },
                        onFlip       = { onToggleFlip(layer) }
                    )
                } else {
                    LayerView(
                        layer            = layer,
                        scale            = scale,
                        isSelected       = isSelected,
                        updateCount      = updateCount,
                        onTap            = { onLayerTap(layer) },
                        onTransform      = onLayerTransform,
                        onPhotoTransform = onPhotoTransform,
                        onDelete         = { onDeleteLayer(layer) },
                        onFlip           = { onToggleFlip(layer) }
                    )
                }
                } // key(layer.id)
            }

            // Drawing overlay
            if (isDrawingMode) {
                DrawingOverlay(
                    scale = canvasScale,
                    strokes = currentDrawingStrokes,
                    activeStroke = activeStroke,
                    onDrawingStart = onDrawingStart,
                    onDrawingUpdate = onDrawingUpdate,
                    onDrawingEnd = onDrawingEnd
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LayerView — renders one layer with correct position, size, rotation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayerView(
    layer: FrameLayer,
    scale: Float,
    isSelected: Boolean,
    updateCount: Int = 0,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onFlip: () -> Unit,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit,
    onPhotoTransform: (FrameLayer, Float, Float, Float) -> Unit
) {
    val density = LocalDensity.current.density

    val currentLayer by rememberUpdatedState(layer)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnPhotoTransform by rememberUpdatedState(onPhotoTransform)

    // Smooth interaction state (scoped to LayerView)
    val localX = remember(layer.id) { mutableFloatStateOf(layer.x * scale) }
    val localY = remember(layer.id) { mutableFloatStateOf(layer.y * scale) }
    val localW = remember(layer.id) { mutableFloatStateOf(layer.width * scale) }
    val localH = remember(layer.id) { mutableFloatStateOf(layer.height * scale) }
    val localRot = remember(layer.id) { mutableFloatStateOf(layer.rotation) }
    val localFS = remember(layer.id) { mutableFloatStateOf(layer.fontSize) }

    // Sync from ViewModel on external changes (undo/redo)
    // Using a stability threshold to prevent "blinking" caused by micro-rounding differences
    LaunchedEffect(layer.x, layer.y, layer.width, layer.height, layer.rotation, scale) {
        val targetX = layer.x * scale
        val targetY = layer.y * scale
        val targetW = layer.width * scale
        val targetH = layer.height * scale

        if (abs(localX.floatValue - targetX) > 0.5f) localX.floatValue = targetX
        if (abs(localY.floatValue - targetY) > 0.5f) localY.floatValue = targetY
        if (abs(localW.floatValue - targetW) > 0.5f) localW.floatValue = targetW
        if (abs(localH.floatValue - targetH) > 0.5f) localH.floatValue = targetH

        localRot.floatValue = layer.rotation
        localFS.floatValue = layer.fontSize
    }

    val modifier = Modifier
        .offset { IntOffset(localX.floatValue.dp.roundToPx(), localY.floatValue.dp.roundToPx()) }
        // Layout-phase size — avoids recomposition when size changes during gestures.
        .layout { measurable, _ ->
            val wPx = localW.floatValue.dp.roundToPx()
            val hPx = localH.floatValue.dp.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
            layout(wPx, hPx) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            rotationZ = localRot.floatValue
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            scaleX = if (layer.flipH) -1f else 1f
            scaleY = if (layer.flipV) -1f else 1f
        }

    val isDrawing = layer.type == LayerType.DRAWING
    val gestureModifier = if (!layer.isBackground && !layer.isLocked && !isDrawing && (!layer.isPhotoSlot || layer.isSticker) && isSelected) {
        Modifier.pointerInput(isSelected, layer.id, layer.isLocked) {
            awaitEachGesture {
                if (layer.isLocked) return@awaitEachGesture
                val firstDown = awaitFirstDown(requireUnconsumed = false)

                // Skip if touch started in handle corner (let SelectionOverlay handles handle it)
                val pos = firstDown.position
                val wPx = localW.floatValue * density
                val hPx = localH.floatValue * density
                val cornerPx = HANDLE_PAD * density
                val inHandleCorner =
                    (pos.x <= cornerPx && pos.y <= cornerPx) ||
                    (pos.x >= wPx - cornerPx && pos.y <= cornerPx) ||
                    (pos.x <= cornerPx && pos.y >= hPx - cornerPx) ||
                    (pos.x >= wPx - cornerPx && pos.y >= hPx - cornerPx)

                if (!inHandleCorner) {
                    var didTransform = false
                    var didMove = false
                    var didScale = false
                    var didRotate = false

                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val rot = event.calculateRotation()

                        if (pan != Offset.Zero || zoom != 1f || rot != 0f) {
                            didTransform = true
                            if (pan != Offset.Zero) {
                                localX.floatValue += pan.x / density
                                localY.floatValue += pan.y / density
                                didMove = true
                            }
                            if (zoom != 1f) {
                                localW.floatValue = (localW.floatValue * zoom).coerceAtLeast(20f)
                                localH.floatValue = (localH.floatValue * zoom).coerceAtLeast(20f)
                                didScale = true
                            }
                            if (rot != 0f) {
                                localRot.floatValue += rot
                                didRotate = true
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })

                    if (didTransform) {
                        currentOnTransform(
                            currentLayer,
                            if (didMove) localX.floatValue / scale else null,
                            if (didMove) localY.floatValue / scale else null,
                            if (didScale) localW.floatValue / scale else null,
                            if (didScale) localH.floatValue / scale else null,
                            if (didRotate) localRot.floatValue else null,
                            null
                        )
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .then(gestureModifier)
            .then(
                if (!layer.isBackground && !isDrawing) {
                    Modifier.pointerInput(layer.id) {
                        detectTapGestures { onTap() }
                    }
                } else Modifier
            )
    ) {
        // Render visual content (apply opacity here so selection overlay stays opaque)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (layer.backgroundColor != android.graphics.Color.TRANSPARENT)
                        Modifier.background(Color(layer.backgroundColor), RoundedCornerShape((layer.backgroundRadius * scale).dp)) else Modifier
                )
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = layer.opacity.coerceIn(0f, 1f) }) {
                LayerContent(layer = layer, wState = localW, hState = localH, fontSize = localFS.floatValue, scale = scale, updateCount = updateCount, onTransform = onTransform)
            }
        }

        // Selection overlay (border + handles)
        // Only show if it's NOT an empty photo slot (tap-to-add UI)
        val isEmptyPhotoSlot = layer.isPhotoSlot && layer.customImage == null
        if (isSelected && !isEmptyPhotoSlot && !isDrawing) {
            SelectionOverlay(
                layer = layer,
                scale = scale,
                localRot = localRot,
                localW = localW,
                localH = localH,
                localFS = localFS,
                onDelete = onDelete,
                onFlip = onFlip,
                onTransform = onTransform
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LayerContent — pure visual rendering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayerContent(
    layer: FrameLayer,
    wState: MutableFloatState,
    hState: MutableFloatState,
    fontSize: Float,
    scale: Float,
    updateCount: Int = 0,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    val w = wState.floatValue
    val h = hState.floatValue
    when (layer.type) {
        LayerType.IMAGE -> ImageLayerContent(layer, w, h, scale, updateCount)
        LayerType.DRAWING -> DrawingLayerContent(layer, w, h, scale * LocalDensity.current.density, updateCount)
        else -> {}
    }
}

@Composable
private fun ImageLayerContent(layer: FrameLayer, w: Float, h: Float, scale: Float, updateCount: Int = 0) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    val imageModel: Any? = when {
        layer.customImage != null -> layer.customImage
        layer.isPhotoSlot && !layer.isBackground -> null // Placeholder
        layer.src != null -> {
            val file = File(layer.src!!)
            if (file.exists()) file else null
        }
        else -> null
    }

    // Compute the full content dimensions so the image covers the slot without distortion,
    // matching the ratio-aware clamp formula used in StickerImageContent and clampPhoto().
    val (imgW, imgH) = if (layer.isPhotoSlot && layer.imageRatio > 0f) {
        val slotRatio = w / h
        if (layer.imageRatio > slotRatio) Pair(h * layer.imageRatio, h) else Pair(w, w / layer.imageRatio)
    } else {
        Pair(w, h)
    }
    val imgContentScale = if (layer.isPhotoSlot && layer.imageRatio > 0f) ContentScale.FillBounds else ContentScale.Crop

    Box(modifier = Modifier.size(w.dp, h.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = layer.name,
                contentScale = imgContentScale,
                colorFilter = getCombinedColorFilter(layer),
                modifier = Modifier
                    .size(imgW.dp, imgH.dp)
                    .graphicsLayer {
                        translationX = layer.photoPanX * scale * density
                        translationY = layer.photoPanY * scale * density
                        scaleX = layer.photoScale
                        scaleY = layer.photoScale
                    }
            )
        } else if (layer.isPhotoSlot && !layer.isBackground) {
            // Photo slot placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = 0.15f))
                    .border(1.5.dp, Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size((w * 0.2f).coerceIn(16f, 48f).dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Text(
                        "Tap to add photo",
                        color = Color.Gray,
                        fontSize = (w * 0.08f).coerceIn(9f, 14f).sp
                    )
                }
            }
        } else {
            // Error placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BrokenImage, tint = Color.LightGray, contentDescription = null)
            }
        }
    }
}


@Composable
private fun DrawingLayerContent(layer: FrameLayer, w: Float, h: Float, scale: Float, updateCount: Int = 0) {
    Canvas(modifier = Modifier.size(w.dp, h.dp).graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        drawStrokes(layer.strokes ?: emptyList(), null, scale)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Selection Overlay — blue border + corner handles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionOverlay(
    layer: FrameLayer,
    scale: Float,
    localRot: MutableFloatState,
    localW: MutableFloatState,
    localH: MutableFloatState,
    localFS: MutableFloatState,
    onDelete: () -> Unit,
    onFlip: () -> Unit,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    CommonSelectionOverlay(
        layer = layer,
        scale = scale,
        localW = localW,
        localH = localH,
        localRot = localRot,
        localFS = localFS,
        onDelete = onDelete,
        onFlip = onFlip,
        onTransform = onTransform
    )
}

@Composable
private fun HandleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(HANDLE_SIZE.dp)
            .shadow(2.dp, CircleShape) // Reduced shadow for performance
            .background(color, CircleShape)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawingOverlay(
    scale: Float,
    strokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    onDrawingStart: (Float, Float) -> Unit,
    onDrawingUpdate: (Float, Float) -> Unit,
    onDrawingEnd: () -> Unit
) {
    // We use a combination of parameters as the key to ensure the gesture detector 
    // restarts if we toggle mode or scale changes.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDrawingStart(offset.x / scale, offset.y / scale)
                    },
                    onDragEnd = {
                        onDrawingEnd()
                    },
                    onDragCancel = {
                        onDrawingEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrawingUpdate(change.position.x / scale, change.position.y / scale)
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            drawStrokes(strokes, activeStroke, scale)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope extension: render all strokes
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawStrokes(
    strokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    canvasScale: Float
) {
    val allStrokes = if (activeStroke != null) strokes + activeStroke else strokes

    allStrokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach

        val isEraser = stroke.isEraser
        val blendMode = if (isEraser) BlendMode.Clear else BlendMode.SrcOver

        when {
            stroke.mode == "neon" && !isEraser -> drawNeonStroke(stroke, canvasScale)
            stroke.mode == "dotted" && !isEraser -> drawDottedStroke(stroke, canvasScale)
            stroke.mode == "neon_dotted" && !isEraser -> drawNeonDottedStroke(stroke, canvasScale)
            stroke.mode == "rainbow" && !isEraser -> drawRainbowStroke(stroke, canvasScale)
            else -> {
                val path = Path()
                path.moveTo(stroke.points[0].x * canvasScale, stroke.points[0].y * canvasScale)
                stroke.points.drop(1).forEach { p ->
                    path.lineTo(p.x * canvasScale, p.y * canvasScale)
                }
                drawPath(
                    path = path,
                    color = if (isEraser) Color.Transparent else Color(stroke.color),
                    style = Stroke(width = stroke.width * canvasScale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    blendMode = blendMode
                )
            }
        }
    }
}

private fun DrawScope.drawNeonStroke(stroke: DrawingStroke, canvasScale: Float) {
    val path = Path()
    path.moveTo(stroke.points[0].x * canvasScale, stroke.points[0].y * canvasScale)
    stroke.points.drop(1).forEach { p -> path.lineTo(p.x * canvasScale, p.y * canvasScale) }

    // Glow
    drawPath(path, Color(stroke.color).copy(alpha = 0.3f),
        style = Stroke(
            width = stroke.width * canvasScale * 2.5f,
            cap = StrokeCap.Round
        )
    )
    // White center highlight
    drawPath(path, Color.White, style = Stroke(width = stroke.width * canvasScale * 0.4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawDottedStroke(stroke: DrawingStroke, canvasScale: Float) {
    val spacing = stroke.width * canvasScale * 2.0f
    for (i in 0 until stroke.points.size - 1) {
        val p1 = Offset(stroke.points[i].x * canvasScale, stroke.points[i].y * canvasScale)
        val p2 = Offset(stroke.points[i + 1].x * canvasScale, stroke.points[i + 1].y * canvasScale)
        val direction = p2 - p1
        val distance = direction.getDistance()
        if (distance == 0f) continue
        val unit = direction / distance
        var currentPos = spacing
        while (currentPos <= distance) {
            val dotCenter = p1 + unit * currentPos
            drawCircle(Color(stroke.color), radius = stroke.width * canvasScale * 0.5f, center = dotCenter)
            currentPos += spacing
        }
    }
}

private fun DrawScope.drawNeonDottedStroke(stroke: DrawingStroke, canvasScale: Float) {
    val spacing = stroke.width * canvasScale * 2.5f
    for (i in 0 until stroke.points.size - 1) {
        val p1 = Offset(stroke.points[i].x * canvasScale, stroke.points[i].y * canvasScale)
        val p2 = Offset(stroke.points[i + 1].x * canvasScale, stroke.points[i + 1].y * canvasScale)
        val direction = p2 - p1
        val distance = direction.getDistance()
        if (distance == 0f) continue
        val unit = direction / distance
        var currentPos = spacing
        while (currentPos <= distance) {
            val dotCenter = p1 + unit * currentPos
            drawCircle(Color(stroke.color), radius = stroke.width * canvasScale * 0.7f, center = dotCenter)
            drawCircle(Color.White, radius = stroke.width * canvasScale * 0.3f, center = dotCenter)
            currentPos += spacing
        }
    }
}

private fun DrawScope.drawRainbowStroke(stroke: DrawingStroke, canvasScale: Float) {
    stroke.points.forEachIndexed { i, point ->
        if (i == 0) return@forEachIndexed
        val hue = (i * 10f) % 360f
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val p1 = Offset(stroke.points[i - 1].x * canvasScale, stroke.points[i - 1].y * canvasScale)
        val p2 = Offset(point.x * canvasScale, point.y * canvasScale)
        drawLine(
            color = Color(color),
            start = p1, end = p2,
            strokeWidth = stroke.width * canvasScale,
            cap = StrokeCap.Round
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color filter helper (maps Flutter filter names to Compose ColorFilter)
// ─────────────────────────────────────────────────────────────────────────────

fun getCombinedColorFilter(layer: FrameLayer): ColorFilter? {
    val matrix = AndroidColorMatrix()
    
    // 1. Preset Filter
    when (layer.filter) {
        "grayscale" -> {
            val m = AndroidColorMatrix()
            m.setSaturation(0f)
            matrix.postConcat(m)
        }
        "sepia" -> {
            val m = AndroidColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            ))
            matrix.postConcat(m)
        }
        "invert" -> {
            val m = AndroidColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                 0f,-1f, 0f, 0f, 255f,
                 0f, 0f,-1f, 0f, 255f,
                 0f, 0f, 0f, 1f,   0f
            ))
            matrix.postConcat(m)
        }
        "warm" -> {
            val m = AndroidColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 0f,
                 0f,  1f, 0f, 0f, 0f,
                 0f,  0f, 0.8f, 0f, 0f,
                 0f,  0f, 0f, 1f, 0f
            ))
            matrix.postConcat(m)
        }
        "cool" -> {
            val m = AndroidColorMatrix(floatArrayOf(
                0.8f, 0f, 0f, 0f, 0f,
                0f,   1f, 0f, 0f, 0f,
                0f,   0f, 1.2f, 0f, 0f,
                0f,   0f, 0f,   1f, 0f
            ))
            matrix.postConcat(m)
        }
        "bright" -> {
            val b = 0.15f * 255f
            val m = AndroidColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(m)
        }
        "story" -> {
            val m = AndroidColorMatrix()
            m.setSaturation(0.8f)
            matrix.postConcat(m)
            val contrast = 1.15f
            val t = (1.0f - contrast) / 2.0f * 255.0f
            val mc = AndroidColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(mc)
        }
    }

    // 2. Brightness
    if (layer.brightness != 0f) {
        val b = layer.brightness * 255f
        val m = AndroidColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(m)
    }

    // 3. Contrast
    if (layer.contrast != 1f) {
        val c = layer.contrast
        val t = (1.0f - c) / 2.0f * 255.0f
        val m = AndroidColorMatrix(floatArrayOf(
            c,  0f, 0f, 0f, t,
            0f, c,  0f, 0f, t,
            0f, 0f, c,  0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(m)
    }

    // 4. Saturation
    if (layer.saturation != 1f) {
        val m = AndroidColorMatrix()
        m.setSaturation(layer.saturation)
        matrix.postConcat(m)
    }

    // 5. Warmth (Temperature)
    if (layer.warmth != 0f) {
        val w = layer.warmth * 0.15f
        val m = AndroidColorMatrix(floatArrayOf(
            1f + w, 0f,     0f, 0f, 0f,
            0f,     1f,     0f, 0f, 0f,
            0f,     0f,     1f - w, 0f, 0f,
            0f,     0f,     0f, 1f, 0f
        ))
        matrix.postConcat(m)
    }

    // 5. Fade (lifts shadows)
    if (layer.fade > 0f) {
        val f = layer.fade
        val fadeMatrix = AndroidColorMatrix(floatArrayOf(
            1f - f, 0f, 0f, 0f, f * 255f,
            0f, 1f - f, 0f, 0f, f * 255f,
            0f, 0f, 1f - f, 0f, f * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(fadeMatrix)
    }

    // 6. Highlights (gain)
    if (layer.highlights != 1f) {
        val h = layer.highlights
        val highlightsMatrix = AndroidColorMatrix(floatArrayOf(
            h, 0f, 0f, 0f, 0f,
            0f, h, 0f, 0f, 0f,
            0f, 0f, h, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(highlightsMatrix)
    }

    // 7. Shadows (offset)
    if (layer.shadows != 1f) {
        val s = (layer.shadows - 1f) * 255f
        val shadowsMatrix = AndroidColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, s,
            0f, 1f, 0f, 0f, s,
            0f, 0f, 1f, 0f, s,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(shadowsMatrix)
    }

    return ColorFilter.colorMatrix(ColorMatrix(matrix.array))
}
