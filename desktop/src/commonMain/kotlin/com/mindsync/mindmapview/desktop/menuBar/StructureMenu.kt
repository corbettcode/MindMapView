package com.mindsync.mindmapview.desktop.menuBar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mindsync.library.model.MapStructure

/** How a structure is named in the menus and the preferences. */
val MapStructure.label: String
    get() = when (this) {
        MapStructure.MIND_MAP -> "Mind Map"
        MapStructure.LOGIC_RIGHT -> "Logic Chart →"
        MapStructure.LOGIC_LEFT -> "Logic Chart ←"
        MapStructure.ORG_DOWN -> "Org Chart ↓"
        MapStructure.ORG_UP -> "Org Chart ↑"
        MapStructure.TREE_RIGHT -> "Tree Chart →"
        MapStructure.TREE_LEFT -> "Tree Chart ←"
        MapStructure.BRACE_RIGHT -> "Brace Map"
        MapStructure.TIMELINE -> "Timeline"
        MapStructure.FISHBONE_LEFT -> "Fishbone, Head Left"
        MapStructure.FISHBONE_RIGHT -> "Fishbone, Head Right"
        MapStructure.MATRIX_ROWS -> "Matrix, Rows"
        MapStructure.MATRIX_COLUMNS -> "Matrix, Columns"
    }

/**
 * A "Structure" item of a menu, which opens a menu of the structures, each with a small picture of it and a tick at
 * [structure], the map's. Each choice closes the menus, with [onClose], before it is made.
 */
@Composable
fun StructureSubmenu(
    structure: MapStructure
    , onStructure: (MapStructure) -> Unit
    , onClose: () -> Unit
) {
    StructureChoices(
        title = "Structure"
        , choices = MapStructure.entries
        , ticked = setOf(structure)
        , isEnabled = true
        , onChoice = { it?.let(onStructure) }
        , onClose = onClose
    )
}

/**
 * A "Branch Structure" item of a menu, for the selected topics, which opens a menu of "Same as Parent" and the
 * structures a branch can have: all but the mind map, which only the root can be laid out in. [structures] are those
 * of the selected topics but the root, null for one laid out as its parent; the one they share is ticked, and none
 * when they differ. Without any, as with only the root selected, it is disabled.
 */
@Composable
fun BranchStructureSubmenu(
    structures: Set<MapStructure?>
    , onStructure: (MapStructure?) -> Unit
    , onClose: () -> Unit
) {
    StructureChoices(
        title = "Branch Structure"
        , choices = listOf(null) + MapStructure.entries.filter { it != MapStructure.MIND_MAP }
        , ticked = structures.takeIf { it.size == 1 }.orEmpty()
        , isEnabled = structures.isNotEmpty()
        , onChoice = onStructure
        , onClose = onClose
    )
}

// A submenu of [choices], null being "Same as Parent", each with a small picture of it, and a tick at those [ticked].
@Composable
private fun StructureChoices(
    title: String
    , choices: List<MapStructure?>
    , ticked: Set<MapStructure?>
    , isEnabled: Boolean
    , onChoice: (MapStructure?) -> Unit
    , onClose: () -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        DropdownMenuItem(
            text = { Text(title) }
            , trailingIcon = { Text("▸") }
            , onClick = { isOpen = true }
            , enabled = isEnabled
        )
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (choice == null) Spacer(Modifier.size(width = 32.dp, height = 22.dp))
                            else StructurePicture(choice, MaterialTheme.colorScheme.onSurface)
                            Text(choice?.label ?: "Same as Parent", modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    , leadingIcon = { Text(if (choice in ticked) "✓" else "") }
                    , onClick = {
                        isOpen = false
                        onClose()
                        onChoice(choice)
                    }
                )
            }
        }
    }
}

/** A small drawing of how [structure] lays a map out: a root, its branches and the lines between them. */
@Composable
fun StructurePicture(
    structure: MapStructure
    , color: Color
) {
    Canvas(modifier = Modifier.size(width = 32.dp, height = 22.dp)) {
        val stroke = 1.2.dp.toPx()
        val w = size.width
        val h = size.height
        fun box(x: Float, y: Float, isRoot: Boolean = false) {
            val boxSize = if (isRoot) Size(w * 0.28f, h * 0.24f) else Size(w * 0.18f, h * 0.16f)
            val topLeft = Offset(x - boxSize.width / 2, y - boxSize.height / 2)
            if (isRoot) drawRect(color, topLeft, boxSize) else drawRect(color, topLeft, boxSize, style = Stroke(stroke))
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke)
        drawStructure(structure, w, h, ::box, ::line)
    }
}

// The picture of each structure, in the [w] by [h] of its canvas.
private fun DrawScope.drawStructure(
    structure: MapStructure
    , w: Float
    , h: Float
    , box: (Float, Float, Boolean) -> Unit
    , line: (Float, Float, Float, Float) -> Unit
) {
    val rows = listOf(0.2f, 0.5f, 0.8f).map { it * h }
    val columns = listOf(0.2f, 0.5f, 0.8f).map { it * w }
    when (structure) {
        MapStructure.MIND_MAP -> {
            box(w / 2, h / 2, true)
            listOf(0.12f, 0.88f).forEach { x -> listOf(0.25f, 0.75f).forEach { y ->
                line(w / 2, h / 2, x * w, y * h)
                box(x * w, y * h, false)
            } }
        }
        MapStructure.LOGIC_RIGHT, MapStructure.LOGIC_LEFT, MapStructure.BRACE_RIGHT -> {
            val isLeft = structure == MapStructure.LOGIC_LEFT
            val rootX = if (isLeft) w * 0.82f else w * 0.18f
            val childX = if (isLeft) w * 0.2f else w * 0.8f
            box(rootX, h / 2, true)
            if (structure == MapStructure.BRACE_RIGHT) {
                line(w * 0.55f, rows.first(), w * 0.55f, rows.last())
                line(rootX, h / 2, w * 0.55f, h / 2)
                rows.forEach { y -> box(childX, y, false) }
            }
            else {
                rows.forEach { y ->
                    line(rootX, h / 2, childX, y)
                    box(childX, y, false)
                }
            }
        }
        MapStructure.ORG_DOWN, MapStructure.ORG_UP -> {
            val isUp = structure == MapStructure.ORG_UP
            val rootY = if (isUp) h * 0.8f else h * 0.2f
            val childY = if (isUp) h * 0.2f else h * 0.8f
            box(w / 2, rootY, true)
            columns.forEach { x ->
                line(w / 2, rootY, x, childY)
                box(x, childY, false)
            }
        }
        MapStructure.TREE_RIGHT, MapStructure.TREE_LEFT -> {
            val sign = if (structure == MapStructure.TREE_RIGHT) 1f else -1f
            val rootX = w / 2 - sign * w * 0.25f
            box(rootX, h * 0.18f, true)
            val spineX = rootX - sign * w * 0.06f
            line(spineX, h * 0.3f, spineX, h * 0.85f)
            listOf(0.55f, 0.85f).forEach { y ->
                line(spineX, y * h, spineX + sign * w * 0.2f, y * h)
                box(spineX + sign * w * 0.3f, y * h, false)
            }
        }
        MapStructure.TIMELINE -> {
            box(w * 0.12f, h / 2, true)
            line(w * 0.12f, h / 2, w * 0.95f, h / 2)
            listOf(0.4f to 0.18f, 0.62f to 0.82f, 0.84f to 0.18f).forEach { (x, y) ->
                line(x * w, h / 2, x * w, y * h)
                box(x * w, y * h, false)
            }
        }
        MapStructure.FISHBONE_LEFT, MapStructure.FISHBONE_RIGHT -> {
            // Drawn head left, and mirrored for head right.
            val isLeft = structure == MapStructure.FISHBONE_LEFT
            fun x(fraction: Float) = (if (isLeft) fraction else 1f - fraction) * w
            box(x(0.14f), h / 2, true)
            line(x(0.14f), h / 2, x(0.97f), h / 2)
            // Each cause is further from the head than where its bone meets the spine, as the bones slant back.
            listOf(0.42f to 0.15f, 0.55f to 0.85f, 0.72f to 0.15f, 0.85f to 0.85f).forEach { (along, y) ->
                line(x(along - 0.13f), h / 2, x(along), y * h)
                box(x(along), y * h, false)
            }
        }
        MapStructure.MATRIX_ROWS, MapStructure.MATRIX_COLUMNS -> {
            // A grid of three by three cells, the corner filled; the headers' side is all that differs.
            val edges = listOf(0.04f, 0.36f, 0.68f, 0.96f)
            edges.forEach { line(it * w, edges.first() * h, it * w, edges.last() * h) }
            edges.forEach { line(edges.first() * w, it * h, edges.last() * w, it * h) }
            box(w * 0.2f, h * 0.2f, true)
            val isRows = structure == MapStructure.MATRIX_ROWS
            listOf(0.52f, 0.84f).forEach { along ->
                if (isRows) box(w * 0.2f, along * h, false) else box(along * w, h * 0.2f, false)
            }
        }
    }
}
