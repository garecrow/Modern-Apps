package com.vayunmathur.photos.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class DrawingTool { Pen, Highlighter, Eraser, Text }

@Serializable
data class Drawing(
    val points: List<SerializableOffset>,
    val tool: DrawingTool,
    val color: Int,
    val strokeWidth: Float,
    val opacity: Float
)

@Serializable
data class TextElement(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val rotation: Float,
    val color: Int,
    val fontSize: Float
)

@Serializable
data class SerializableOffset(val x: Float, val y: Float)

fun Offset.toSerializable() = SerializableOffset(x, y)
fun SerializableOffset.toOffset() = Offset(x, y)
