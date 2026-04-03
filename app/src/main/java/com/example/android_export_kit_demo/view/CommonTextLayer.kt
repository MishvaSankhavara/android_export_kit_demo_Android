package com.example.android_export_kit_demo.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.LayerType
import kotlin.math.*
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * CommonTextLayer — Unified text renderer for both template text and added stickers.
 * Handles styling, auto-expansion, font-shrinking, and inline editing.
 */
@Composable
fun CommonTextLayer(
    layer: FrameLayer,
    scale: Float,
    wState: MutableFloatState,
    hState: MutableFloatState,
    fontSize: Float,
    isEditingInline: Boolean = false,
    isEditing: Boolean = false, // New: Indicates if the text is being edited (bottom panel)
    isSelected: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    onTransform: (FrameLayer, Float?, Float?, Float?, Float?, Float?, Float?) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var fontScale by remember(layer.id, layer.backgroundImage) { mutableFloatStateOf(1f) }
    val scaledFontSize = (fontSize * scale * fontScale).coerceIn(4f, 400f)

    val textAlign = when (layer.justification.lowercase()) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }

    val fontFamily = when(layer.font?.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "arial", "verdana", "trebuchet", "roboto" -> FontFamily.SansSerif
        "monospace", "courier" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "georgia", "times" -> FontFamily.Serif
        "impact" -> FontFamily.SansSerif 
        else -> FontFamily.Default
    }

    val shadow = if (layer.shadowBlur > 0 || layer.shadowOffsetX != 0f || layer.shadowOffsetY != 0f) {
        Shadow(
            color = Color(layer.shadowColor),
            offset = androidx.compose.ui.geometry.Offset(layer.shadowOffsetX, layer.shadowOffsetY),
            blurRadius = layer.shadowBlur
        )
    } else null

    // Base Style (Fill)
    val style = TextStyle(
        color = Color(layer.color),
        fontFamily = fontFamily,
        fontWeight = if (layer.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (layer.isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = buildList {
            if (layer.isUnderline) add(TextDecoration.Underline)
            if (layer.isStrikethrough) add(TextDecoration.LineThrough)
        }.let { if (it.isEmpty()) TextDecoration.None else TextDecoration.combine(it) },
        textAlign = textAlign,
        fontSize = scaledFontSize.sp,
        shadow = shadow,
        letterSpacing = layer.letterSpacing.sp,
        lineHeight = if (layer.lineHeight != 0f) (scaledFontSize * (1 + layer.lineHeight)).sp else androidx.compose.ui.unit.TextUnit.Unspecified
    )

    // Stroke Style
    val hasStroke = layer.strokeWidth > 0 && layer.strokeColor != android.graphics.Color.TRANSPARENT
    val strokeStyle = if (hasStroke) {
        style.copy(
            color = Color(layer.strokeColor),
            drawStyle = Stroke(width = layer.strokeWidth * scale, miter = 10f),
            shadow = null, // don't double shadow
            background = Color.Unspecified
        )
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (layer.backgroundColor != android.graphics.Color.TRANSPARENT || layer.backgroundShape != null) {
                    val shape = when (layer.backgroundShape) {
                        "heart" -> HeartShape
                        "cloud" -> CloudShape
                        "burst" -> BurstShape
                        "bubble_left" -> SpeechBubbleShape(isLeft = true)
                        "bubble_right" -> SpeechBubbleShape(isLeft = false)
                        else -> RoundedCornerShape((layer.backgroundRadius * scale).dp)
                    }
                    val bgColor = if (layer.backgroundColor == android.graphics.Color.TRANSPARENT && layer.backgroundShape != null) {
                        Color.LightGray.copy(alpha = 0.1f)
                    } else {
                        Color(layer.backgroundColor).copy(alpha = layer.backgroundOpacity.coerceIn(0f, 1f))
                    }
                    Modifier
                        .background(bgColor, shape)
                        .border(0.5.dp * scale, Color.Black.copy(alpha = 0.15f), shape)
                } else Modifier
            )
            .then(
                if (isEditingInline && isSelected) Modifier.border(
                    1.dp, Color(0xFF5C9EFF).copy(alpha = 0.5f), RoundedCornerShape(4.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (layer.backgroundImage != null) {
            val model = if (layer.backgroundImage!!.startsWith("http") || layer.backgroundImage!!.startsWith("file")) layer.backgroundImage!!
                        else "file:///android_asset/${layer.backgroundImage!!}"
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        if (isEditingInline && isSelected && onTextChange != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = (layer.paddingHorizontal * scale).dp,
                        vertical = (layer.paddingVertical * scale).dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    maxLines = Int.MAX_VALUE,
                    value = layer.text ?: "",
                    onValueChange = onTextChange,
                    textStyle = style.copy(textAlign = textAlign),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    onTextLayout = { result ->
                        val padWPx = layer.paddingHorizontal * scale * density * 2f
                        val padHPx = layer.paddingVertical * scale * density * 2f
                        val curW = wState.floatValue * density
                        val curH = hState.floatValue * density
                        val reqW = result.size.width.toFloat() + padWPx
                        val reqH = result.size.height.toFloat() + padHPx

                        // Calculate if we should expand the layer or shrink the font
                        val isPreset = layer.presetId != null || layer.backgroundImage != null || layer.backgroundShape != null
                        val canExpand = isSelected && !isPreset

                        // 1. Expand layer if text + padding overflows and expansion is allowed
                        if (canExpand && (reqW > curW + 2f || reqH > curH + 2f)) {
                            val newW = max(curW, reqW) / density
                            val newH = max(curH, reqH) / density
                            wState.floatValue = newW
                            hState.floatValue = newH
                            onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                        } 
                        // 2. Shrink font if expansion is NOT allowed/possible and text overflows
                        else if (curW > 10f && curH > 10f && (result.hasVisualOverflow || reqW > curW || reqH > curH) && fontScale > 0.1f) {
                            val ratioW = (curW - padWPx) / result.size.width
                            val ratioH = (curH - padHPx) / result.size.height
                            val minRatio = min(ratioW, ratioH).coerceIn(0.1f, 0.99f)
                            fontScale = (fontScale * minRatio * 0.95f).coerceAtLeast(0.1f)
                        }
                        // 3. Grow font back if there is space and we are not editing (optional, keep for non-preset)
                        else if (!isEditingInline && curW > 10f && result.size.height < (curH - padHPx) * 0.6f && fontScale < 1.0f) {
                            fontScale = (fontScale * 1.05f).coerceAtMost(1.0f)
                        }
                    }
                )
            }
        } else {
            val display = when (layer.textCase) {
                "lowercase" -> (layer.text ?: "").lowercase()
                "uppercase" -> (layer.text ?: "").uppercase()
                "titlecase" -> (layer.text ?: "").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { it.uppercase() } }
                else -> layer.text ?: ""
            }
            
            if (layer.curve != 0f) {
                // Curved Text Rendering
                androidx.compose.foundation.Canvas(modifier = Modifier.wrapContentSize()) {
                    val w = size.width
                    val h = size.height
                    
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val nativePaint = android.graphics.Paint().apply {
                            color = layer.color
                            textSize = scaledFontSize * density
                            isAntiAlias = true
                            this.textAlign = when (layer.justification.lowercase()) {
                                "left" -> android.graphics.Paint.Align.LEFT
                                "right" -> android.graphics.Paint.Align.RIGHT
                                else -> android.graphics.Paint.Align.CENTER
                            }
                            typeface = when (layer.font?.lowercase()) {
                                "serif" -> Typeface.SERIF
                                "monospace" -> Typeface.MONOSPACE
                                else -> Typeface.DEFAULT
                            }
                            if (layer.isBold) isFakeBoldText = true
                        }

                        val path = Path()
                        if (layer.curveType == "wave") {
                            val amplitude = h * 0.2f * layer.curve
                            val freq = 2f * Math.PI.toFloat() / w
                            path.moveTo(0f, h / 2f)
                            var px = 0f
                            while (px <= w) {
                                val py = h / 2f + amplitude * sin(freq * px)
                                path.lineTo(px, py)
                                px += 5f
                            }
                        } else {
                            val arcRadius = w * 0.8f / (abs(layer.curve) + 0.1f)
                            val angle = 120f * layer.curve
                            val rectF = android.graphics.RectF(
                                w / 2f - arcRadius,
                                if (layer.curve > 0) h / 2f else h / 2f - arcRadius * 2f,
                                w / 2f + arcRadius,
                                if (layer.curve > 0) h / 2f + arcRadius * 2f else h / 2f
                            )
                            
                            if (layer.curve > 0) {
                                path.addArc(rectF, 180f + (180f - angle) / 2f, angle)
                            } else {
                                path.addArc(rectF, (180f - abs(angle)) / 2f, abs(angle))
                            }
                        }

                        // Draw Stroke first
                        if (hasStroke) {
                            val strokePaint = android.graphics.Paint(nativePaint).apply {
                                color = layer.strokeColor
                                this.style = android.graphics.Paint.Style.STROKE
                                strokeWidth = layer.strokeWidth * scale * density
                            }
                            nativeCanvas.drawTextOnPath(display, path, 0f, 0f, strokePaint)
                        }

                        // Draw Shadow if exists
                        if (shadow != null) {
                            nativePaint.setShadowLayer(shadow.blurRadius, shadow.offset.x, shadow.offset.y, shadow.color.toArgb())
                        }

                        nativeCanvas.drawTextOnPath(display, path, 0f, 0f, nativePaint)
                    }
                }
            } // ONLY showing the FIXED PART (replace inside your else block)

            else {
                var textBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = (layer.paddingHorizontal * scale).dp,
                            vertical = (layer.paddingVertical * scale).dp
                        )
                ) {
                    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight(), contentAlignment = Alignment.Center) {
                        if (isEditing && textBounds != null) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.matchParentSize()
                            ) {
                                val rect = textBounds!!
                                val pad = 2.dp.toPx()

                                drawRect(
                                    color = Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        rect.left - pad,
                                        rect.top - pad
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        rect.width + pad * 2,
                                        rect.height + pad * 2
                                    ),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = dashEffect
                                    )
                                )
                            }
                        }

                        if (strokeStyle != null) {
                            Text(
                                text = display,
                                style = strokeStyle,
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                textAlign = textAlign
                            )
                        }

                        Text(
                            text = display,
                            style = style,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            onTextLayout = { result ->
                                val padWPx = layer.paddingHorizontal * scale * density * 2f
                                val padHPx = layer.paddingVertical * scale * density * 2f
                                val curW = wState.floatValue * density
                                val curH = hState.floatValue * density
                                val reqW = result.size.width.toFloat() + padWPx
                                val reqH = result.size.height.toFloat() + padHPx

                                val maxLineW = (0 until result.lineCount)
                                    .map { i -> result.getLineRight(i) - result.getLineLeft(i) }
                                    .maxOrNull() ?: 0f
                                val textH = result.size.height.toFloat()

                                // Since the parent Box is wrapContentWidth, and Text is wrapContentWidth,
                                // the Text's top-left is effectively (0,0) relative to the Box.
                                // However, if there are multiple lines, the Text composable is as wide as the widest line.
                                // Alignments (Center/Right) are handled by the inner Box's own alignment or by the text layout.
                                val leftBase = when (textAlign) {
                                    TextAlign.Center -> (result.size.width - maxLineW) / 2f
                                    TextAlign.Right -> result.size.width - maxLineW
                                    else -> 0f
                                }

                                val newBounds = androidx.compose.ui.geometry.Rect(
                                    left = leftBase,
                                    top = 0f,
                                    right = leftBase + maxLineW,
                                    bottom = textH
                                )
                                // Only update textBounds if there is a real change (> 0.5 px)
                                if (textBounds == null || 
                                    kotlin.math.abs(textBounds!!.width - newBounds.width) > 1.5f ||
                                    kotlin.math.abs(textBounds!!.height - newBounds.height) > 1.5f ||
                                    kotlin.math.abs(textBounds!!.left - newBounds.left) > 1.5f) {
                                    textBounds = newBounds
                                }

                                val isPreset = layer.presetId != null || layer.backgroundImage != null || layer.backgroundShape != null
                                val canExpand = isSelected && !isPreset

                                if (canExpand && (reqW > curW + 12f || reqH > curH + 12f)) {
                                    val newW = max(curW, reqW) / density
                                    val newH = max(curH, reqH) / density
                                    wState.floatValue = newW
                                    hState.floatValue = newH
                                    onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                                }
                                else if (curW > 10f && curH > 10f && (result.hasVisualOverflow || reqW > curW + 5f || reqH > curH + 5f) && fontScale > 0.1f) {
                                    // Shrink with more precision and a larger safety margin (5px buffer)
                                    val availableH = curH - padHPx
                                    val availableW = curW - padWPx
                                    val hRatio = if (result.size.height > 0) availableH / result.size.height else 0.9f
                                    val wRatio = if (result.size.width > 0) availableW / result.size.width else 0.9f
                                    val targetRatio = min(hRatio, wRatio).coerceIn(0.1f, 0.98f)

                                    val newScale = (fontScale * targetRatio).coerceAtLeast(0.1f)

// 🔥 Only update if BIG change (avoid blinking)
                                    if (kotlin.math.abs(newScale - fontScale) > 0.02f) {
                                        fontScale = newScale
                                    }
                                }
                                else if (result.size.height < (curH - padHPx) * 0.5f && 
                                         result.size.width < (curW - padWPx) * 0.5f && 
                                         fontScale < 1.0f) {
                                    // Grow very conservatively only if there is massive extra space (> 50%)
                                    fontScale = (fontScale * 1.01f).coerceAtMost(1.0f)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

