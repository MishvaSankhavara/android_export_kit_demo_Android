package com.example.android_export_kit_demo.view

import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.*

val HeartShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            moveTo(width / 2f, height * 0.25f)
            cubicTo(width * 0.2f, 0f, 0f, height * 0.35f, 0f, height * 0.6f)
            cubicTo(0f, height * 0.85f, width * 0.3f, height, width / 2f, height)
            cubicTo(width * 0.7f, height, width, height * 0.85f, width, height * 0.6f)
            cubicTo(width, height * 0.35f, width * 0.8f, 0f, width / 2f, height * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}

val CloudShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            moveTo(w * 0.2f, h * 0.5f)
            cubicTo(w * 0.05f, h * 0.4f, w * 0.05f, h * 0.15f, w * 0.25f, h * 0.15f)
            cubicTo(w * 0.3f, 0f, w * 0.6f, 0f, w * 0.7f, h * 0.15f)
            cubicTo(w * 0.95f, h * 0.15f, w * 0.95f, h * 0.4f, w * 0.8f, h * 0.5f)
            cubicTo(w * 0.95f, h * 0.6f, w * 0.95f, h * 0.9f, w * 0.75f, h * 0.9f)
            cubicTo(w * 0.65f, h, w * 0.35f, h, w * 0.25f, h * 0.9f)
            cubicTo(w * 0.05f, h * 0.9f, w * 0.05f, h * 0.6f, w * 0.2f, h * 0.5f)
            close()
        }
        return Outline.Generic(path)
    }
}

val BurstShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val points = 12
            val innerRadius = minOf(w, h) * 0.35f
            val outerRadius = minOf(w, h) * 0.5f
            val centerX = w / 2f
            val centerY = h / 2f
            for (i in 0 until points * 2) {
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val angle = PI * i / points
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

class SpeechBubbleShape(val isLeft: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val r = 8f * density.density
            val tailSize = 10f * density.density
            
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f, right = w, bottom = h - tailSize,
                    cornerRadius = CornerRadius(r)
                )
            )
            
            if (isLeft) {
                moveTo(r * 2f, h - tailSize)
                lineTo(r, h)
                lineTo(r * 3f, h - tailSize)
            } else {
                moveTo(w - r * 2f, h - tailSize)
                lineTo(w - r, h)
                lineTo(w - r * 3f, h - tailSize)
            }
        }
        return Outline.Generic(path)
    }
}
