package com.example.android_export_kit_demo.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.LayerType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

private const val HANDLE_SIZE = 32f
private const val HANDLE_PAD = 16f

/**
 * CommonSelectionOverlay — Unified selection UI for all layer types.
 * Provides the dashed blue border and interactive corner handles.
 */
@Composable
fun CommonSelectionOverlay(
    layer: FrameLayer,
    scale: Float,
    localW: MutableFloatState,
    localH: MutableFloatState,
    localRot: MutableFloatState,
    localFS: MutableFloatState,
    onDelete: () -> Unit,
    onFlip: () -> Unit,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    val halfDp = HANDLE_SIZE / 2f
    val density = LocalDensity.current.density
    val layoutDensity = LocalDensity.current
    
    val dashEffect = remember(layoutDensity) {
        with(layoutDensity) {
            PathEffect.dashPathEffect(
                intervals = floatArrayOf(12.dp.toPx(), 6.dp.toPx()),
                phase = 0f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Dashed Selection Border
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 2.dp.toPx()
            // White halo for visibility on dark backgrounds
            drawRect(color = Color.White.copy(alpha = 0.5f), style = Stroke(width = strokePx + 2.dp.toPx()))
            // Dashed blue line
            drawRect(
                color = Color(0xFF2979FF),
                style = Stroke(width = strokePx, pathEffect = dashEffect)
            )
        }

        // 2. DELETE Handle (Top-Left)
        // Show for stickers, all text layers, or for photo slots that actually have an image
        val showDelete = layer.isSticker || 
                         layer.type == LayerType.TEXT || 
                         (layer.type == LayerType.IMAGE && layer.isPhotoSlot && layer.customImage != null)
        
        if (showDelete) {
            Box(modifier = Modifier.offset { IntOffset((-halfDp).dp.roundToPx(), (-halfDp).dp.roundToPx()) }) {
                OverlayHandle(icon = Icons.Default.Close, color = Color(0xFFFF4757), onClick = onDelete)
            }
        }

        // 3. FLIP Handle (Top-Right)
        if (layer.type != LayerType.IMAGE || layer.isSticker) {
            Box(modifier = Modifier.offset {
                IntOffset((localW.floatValue - halfDp).dp.roundToPx(), (-halfDp).dp.roundToPx())
            }) {
                OverlayHandle(icon = Icons.Default.Flip, color = Color(0xFF9C27B0), onClick = onFlip)
            }
        }

        // 4. ROTATE Handle (Bottom-Left)
        val currentLayer by rememberUpdatedState(layer)
        if (!layer.isLocked && (layer.type != LayerType.IMAGE || layer.isSticker)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((-halfDp).dp.roundToPx(), (localH.floatValue - halfDp).dp.roundToPx()) }
                    .pointerInput(layer.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cx = (localW.floatValue / 2f + HANDLE_PAD) * density
                            val cy = (HANDLE_PAD - localH.floatValue / 2f) * density
                            val initialLocalAngle = atan2((down.position.y - cy).toDouble(), (down.position.x - cx).toDouble())
                            var prevGlobalAngle = initialLocalAngle + (localRot.floatValue * PI / 180.0)
                            
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                val curLocalAngle = atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble())
                                val curGlobalAngle = curLocalAngle + (localRot.floatValue * PI / 180.0)
                                var dAngle = ((curGlobalAngle - prevGlobalAngle) * (180.0 / PI)).toFloat()
                                dAngle = if (dAngle > 180f) dAngle - 360f else if (dAngle < -180f) dAngle + 360f else dAngle
                                
                                if (abs(dAngle) > 0.01f) {
                                    localRot.floatValue += dAngle
                                    prevGlobalAngle = curGlobalAngle
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            onTransform(currentLayer, null, null, null, null, localRot.floatValue, null)
                        }
                    }
            ) {
                OverlayHandle(icon = Icons.Default.RotateRight, color = Color(0xFFFF9800))
            }
        }

        // 5. RESIZE Handle (Bottom-Right)
        if (!layer.isLocked && (layer.type != LayerType.IMAGE || layer.isSticker)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((localW.floatValue - halfDp).dp.roundToPx(), (localH.floatValue - halfDp).dp.roundToPx()) }
                    .pointerInput(layer.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val initW = localW.floatValue
                            val initH = localH.floatValue
                            val initFs = currentLayer.fontSize
                            var accumDx = 0f
                            var accumDy = 0f
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                val d = change.positionChange()
                                accumDx += d.x / density
                                accumDy += d.y / density
                                localW.floatValue = (initW + accumDx).coerceAtLeast(20f)
                                localH.floatValue = (initH + accumDy).coerceAtLeast(10f)
                                if (currentLayer.text != null && initH > 0) {
                                    localFS.floatValue = (initFs * (localH.floatValue / initH)).coerceIn(8f, 400f)
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })
                            onTransform(currentLayer, null, null, localW.floatValue / scale, localH.floatValue / scale, null, localFS.floatValue)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(HANDLE_SIZE.dp)
                        .background(Color.White, CircleShape)
                        .border(1.8.dp, Color(0xFF5C9EFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ZoomOutMap, null, tint = Color(0xFF5C9EFF), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OverlayHandle(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(HANDLE_SIZE.dp)
            .shadow(2.dp, CircleShape)
            .background(color, CircleShape)
            .then(if (onClick != null) Modifier.pointerInput(Unit) { detectTapGestures { onClick() } } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
