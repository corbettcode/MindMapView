package com.mindsync.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mindsync.library.layout.MeasureTextSize
import org.jetbrains.skia.DynamicMemoryWStream
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas

internal actual fun mapSvg(
    state: MindMapState
    , density: Density
    , measureTextSize: MeasureTextSize
): String? {
    val bounds = density.exportBounds(state.tree)
    val scale = 1f / density.density
    val width = bounds.width * scale
    val height = bounds.height * scale
    val stream = DynamicMemoryWStream()
    // Text is kept as text, not converted to paths, so it stays searchable; the XML is laid out to be read.
    val canvas = SVGCanvas.make(Rect.makeWH(width, height), stream, false, true)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas.asComposeCanvas(), Size(width, height)) {
        scale(scale, Offset.Zero) {
            drawExportedMap(state, bounds, measureTextSize)
        }
    }
    // The document is only ended when its canvas is closed.
    canvas.close()
    val bytes = ByteArray(stream.bytesWritten())
    stream.read(bytes, 0, bytes.size)
    stream.close()
    return bytes.decodeToString()
}
