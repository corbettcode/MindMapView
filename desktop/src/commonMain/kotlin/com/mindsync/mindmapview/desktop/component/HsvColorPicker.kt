package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val SATURATION_VALUE_SIZE = 220.dp
private val HUE_BAR_WIDTH = 24.dp
private const val HUE_STEPS = 12

/**
 * Picks a color by its hue on a bar and its saturation and brightness on a square, as the color choosers
 * of most drawing programs do.
 */
@Composable
fun HsvColorPicker(
    color: Color
    , onColorChange: (Color) -> Unit
    , modifier: Modifier = Modifier
) {
    // The hue is kept apart from the color, as a gray or black color has none and dragging through one
    // would otherwise turn the square red.
    var hsv by remember { mutableStateOf(color.toHsv()) }
    // The gestures are set up once, so they reach the color that is selected now through this.
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    if (Color.hsv(hsv[0], hsv[1], hsv[2]).toArgb() != color.toArgb()) hsv = color.toHsv()

    fun change(hue: Float = hsv[0], saturation: Float = hsv[1], value: Float = hsv[2]) {
        hsv = floatArrayOf(hue, saturation, value)
        currentOnColorChange(Color.hsv(hue, saturation, value))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
        , modifier = modifier
    ) {
        val pickSaturationValue = { point: Offset, size: IntSize ->
            change(
                saturation = (point.x / size.width).coerceIn(0f, 1f)
                , value = 1f - (point.y / size.height).coerceIn(0f, 1f)
            )
        }
        Canvas(
            modifier = Modifier
                .size(SATURATION_VALUE_SIZE)
                .pointerInput(Unit) { detectTapGestures { pickSaturationValue(it, size) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> pickSaturationValue(change.position, size) } }
        ) {
            val corner = CornerRadius(8.dp.toPx())
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv[0], 1f, 1f)))
                , cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                , cornerRadius = corner
            )
            val marker = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = marker, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, radius = 7.dp.toPx(), center = marker, style = Stroke(1.5.dp.toPx()))
        }

        val pickHue = { y: Float, height: Int ->
            change(hue = (y / height).coerceIn(0f, 1f) * 360f)
        }
        Canvas(
            modifier = Modifier
                .size(HUE_BAR_WIDTH, SATURATION_VALUE_SIZE)
                .pointerInput(Unit) { detectTapGestures { pickHue(it.y, size.height) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> pickHue(change.position.y, size.height) } }
        ) {
            drawRoundRect(
                brush = Brush.verticalGradient((0..HUE_STEPS).map { Color.hsv(it * 360f / HUE_STEPS, 1f, 1f) })
                , cornerRadius = CornerRadius(4.dp.toPx())
            )
            val y = hsv[0] / 360f * size.height
            val handle = 3.dp.toPx()
            drawRect(
                Color.Black
                , topLeft = Offset(0f, y - handle)
                , size = Size(size.width, handle * 2)
                , style = Stroke(1.5.dp.toPx())
            )
        }
    }
}

// The hue in degrees, and the saturation and value from 0 to 1.
private fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val chroma = max - minOf(red, green, blue)
    val hue = when {
        chroma == 0f -> 0f
        max == red -> 60f * (((green - blue) / chroma).mod(6f))
        max == green -> 60f * ((blue - red) / chroma + 2f)
        else -> 60f * ((red - green) / chroma + 4f)
    }
    return floatArrayOf(hue, if (max == 0f) 0f else chroma / max, max)
}
