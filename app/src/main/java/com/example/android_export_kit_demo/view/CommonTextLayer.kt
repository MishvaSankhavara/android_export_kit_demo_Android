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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android_export_kit_demo.model.FrameLayer
import kotlin.math.max

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
    var fontScale by remember(layer.id, layer.text, fontSize, wState.floatValue) { mutableFloatStateOf(1f) }
    val scaledFontSize = (fontSize * scale * fontScale).coerceIn(4f, 400f)

    val textAlign = when (layer.justification.lowercase()) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }

    // Shadow calculation
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
        shadow = shadow
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
                if (isEditingInline && isSelected) Modifier.border(
                    1.dp, Color(0xFF5C9EFF).copy(alpha = 0.5f), RoundedCornerShape(4.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isEditingInline && isSelected && onTextChange != null) {
            BasicTextField(
                value = layer.text ?: "",
                onValueChange = onTextChange,
                textStyle = style,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                onTextLayout = { result ->
                    val curW = wState.floatValue * density
                    val curH = hState.floatValue * density
                    val reqW = result.size.width.toFloat()
                    val reqH = result.size.height.toFloat()

                    if ((reqW > curW + 2f || reqH > curH + 2f) && isSelected) {
                        val newW = max(curW, reqW) / density
                        val newH = max(curH, reqH) / density
                        wState.floatValue = newW
                        hState.floatValue = newH
                        onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                    } else if (fontScale > 0.1f) {
                        val targetH = curH
                        if ((result.hasVisualOverflow || reqH > targetH + 1f)) {
                            val ratio = targetH / reqH
                            if (ratio < 0.98f) {
                                fontScale *= (ratio * 0.95f)
                            }
                        }
                    }
                }
            )
        } else {
            val display = when (layer.textCase) {
                "lowercase" -> (layer.text ?: "").lowercase()
                "uppercase" -> (layer.text ?: "").uppercase()
                "titlecase" -> (layer.text ?: "").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                else -> layer.text ?: ""
            }
            
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (strokeStyle != null) {
                    Text(
                        text = display,
                        style = strokeStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = display,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { result ->
                        val curW = wState.floatValue * density
                        val curH = hState.floatValue * density
                        val reqW = result.size.width.toFloat()
                        val reqH = result.size.height.toFloat()

                        if ((reqW > curW + 2f || reqH > curH + 2f) && isSelected) {
                            val newW = max(curW, reqW) / density
                            val newH = max(curH, reqH) / density
                            wState.floatValue = newW
                            hState.floatValue = newH
                            onTransform(layer, null, null, newW / scale, newH / scale, null, null)
                        } else {
                            if ((result.hasVisualOverflow || result.size.height > curH) && fontScale > 0.1f) {
                                val ratio = curH / result.size.height
                                if (ratio < 0.99f) {
                                    fontScale *= (ratio * 0.95f)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

