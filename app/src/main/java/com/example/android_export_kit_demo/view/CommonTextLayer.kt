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
            BasicTextField(
                value = layer.text ?: "",
                onValueChange = onTextChange,
                textStyle = style,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = (layer.paddingHorizontal * scale).dp,
                        vertical = (layer.paddingVertical * scale).dp
                    ),
                onTextLayout = { result ->
                    val padWPx = layer.paddingHorizontal * scale * density * 2f
                    val padHPx = layer.paddingVertical * scale * density * 2f
                    val curW = wState.floatValue * density
                    val curH = hState.floatValue * density
                    val reqW = result.size.width.toFloat() + padWPx
                    val reqH = result.size.height.toFloat() + padHPx

                    // 1. Expand layer if text + padding overflows while typing
                    if (reqW > curW + 8f || reqH > curH + 8f) {
                        val newW = max(curW, reqW) / density
                        val newH = max(curH, reqH) / density
                        wState.floatValue = newW
                        hState.floatValue = newH
                        onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                    } 
                    // 2. Shrink font if it still overflows (respecting padding)
                    else if ((result.hasVisualOverflow || reqH > curH - 2f) && fontScale > 0.1f) {
                        val availableH = curH - padHPx
                        val ratio = if (availableH > 0) availableH / result.size.height else 0.5f
                        if (ratio < 0.99f) {
                            fontScale = (fontScale * ratio * 0.95f).coerceAtLeast(0.1f)
                        }
                    }
                    // 3. Grow font back if there is space
                    else if (result.size.height < (curH - padHPx) * 0.7f && fontScale < 1.0f) {
                        fontScale = (fontScale * 1.05f).coerceAtMost(1.0f)
                    }
                }
            )
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
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
            } else {
                // Static Fill/Stroke Overlay
                Box(
                    contentAlignment = Alignment.Center, 
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = (layer.paddingHorizontal * scale).dp,
                            vertical = (layer.paddingVertical * scale).dp
                        )
                ) {
                    if (strokeStyle != null) {
                        Text(
                            text = display,
                            style = strokeStyle,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = textAlign
                        )
                    }
                    Text(
                        text = display,
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                        onTextLayout = { result ->
                            val padWPx = layer.paddingHorizontal * scale * density * 2f
                            val padHPx = layer.paddingVertical * scale * density * 2f
                            val curW = wState.floatValue * density
                            val curH = hState.floatValue * density
                            val reqW = result.size.width.toFloat() + padWPx
                            val reqH = result.size.height.toFloat() + padHPx

                            // 1. Expand layer if text + padding overflows
                            if (isSelected && (reqW > curW + 8f || reqH > curH + 8f)) {
                                val newW = max(curW, reqW) / density
                                val newH = max(curH, reqH) / density
                                wState.floatValue = newW
                                hState.floatValue = newH
                                onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                            } 
                            // 2. Shrink font if it still overflows (respecting padding)
                            else if ((result.hasVisualOverflow || (reqH > curH - 2f && !isSelected)) && fontScale > 0.1f) {
                                val availableH = curH - padHPx
                                val ratio = if (availableH > 0) availableH / result.size.height else 0.5f
                                if (ratio < 0.99f) {
                                    fontScale = (fontScale * ratio * 0.95f).coerceAtLeast(0.1f)
                                }
                            }
                            // 3. Grow font back if there is significant extra space
                            else if (result.size.height < (curH - padHPx) * 0.7f && fontScale < 1.0f) {
                                fontScale = (fontScale * 1.05f).coerceAtMost(1.0f)
                            }
                        }
                    )
                }
            }
        }
    }
}

