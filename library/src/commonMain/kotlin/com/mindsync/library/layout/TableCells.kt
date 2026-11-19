package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.halfWidth
import com.mindsync.library.model.GrowDirection
import kotlin.math.abs

/**
 * Returns the left of a table [width] wide whose header holds [owner], with [padding] around it, in dp. A branch's
 * topic is at the end of the header nearest its parent, so the line to it does not cross the table; the root's, and a
 * topic placed below or above its parent, in the middle.
 */
internal fun tableLeftFor(
    tree: Tree<*>
    , owner: NodeData<*>
    , width: Float
    , padding: Float
): Float {
    val centerX = owner.path.centerX.dpVal
    val halfWidth = halfWidth(owner).dpVal
    if (owner.parentId == null) return centerX - width / 2
    return when (tree.placedDirectionOf(owner.id)) {
        GrowDirection.RIGHT -> centerX - halfWidth - padding
        GrowDirection.LEFT -> centerX + halfWidth + padding - width
        else -> centerX - width / 2
    }
}

/** A cell of a table, by its edges, in dp. */
internal class CellRect(
    val left: Float
    , val top: Float
    , val right: Float
    , val bottom: Float
)

/** A straight line of a table, from ([x1], [y1]) to ([x2], [y2]) in dp: across or down, heavier when [isHeavy]. */
internal data class TableLine(
    val x1: Float
    , val y1: Float
    , val x2: Float
    , val y2: Float
    , val isHeavy: Boolean = false
)

/**
 * What a table is drawn with: the [lines] between and around its cells, and its [bounds], which take in every cell.
 */
internal class TableCells(
    val lines: List<TableLine>
    , val bounds: CellRect
) {
    companion object {
        /**
         * Returns the table of [cells], with [rules] drawn heavier over their edges: each edge of the cells is drawn
         * once, the edges along one line joined into one, and none where a rule is drawn instead.
         */
        fun of(
            cells: List<CellRect>
            , rules: List<TableLine> = emptyList()
        ): TableCells {
            require(cells.isNotEmpty()) { "A table has at least one cell" }
            val across = cells.flatMap { listOf(it.top to (it.left to it.right), it.bottom to (it.left to it.right)) }
            val down = cells.flatMap { listOf(it.left to (it.top to it.bottom), it.right to (it.top to it.bottom)) }
            val acrossRules = rules.filter { it.y1 == it.y2 }.map { it.y1 to (it.x1 to it.x2) }
            val downRules = rules.filter { it.x1 == it.x2 }.map { it.x1 to (it.y1 to it.y2) }
            val lines = joined(down).filterNot { it.isUnder(downRules) }.map { (x, span) ->
                TableLine(x, span.first, x, span.second)
            } + joined(across).filterNot { it.isUnder(acrossRules) }.map { (y, span) ->
                TableLine(span.first, y, span.second, y)
            }
            val bounds = CellRect(
                cells.minOf { it.left }
                , cells.minOf { it.top }
                , cells.maxOf { it.right }
                , cells.maxOf { it.bottom }
            )
            return TableCells(lines + rules.map { it.copy(isHeavy = true) }, bounds)
        }

        // The spans along each line, by where the line is across them, joined where they touch or overlap, in the
        // order of the lines and along each.
        private fun joined(edges: List<Pair<Float, Pair<Float, Float>>>): List<Pair<Float, Pair<Float, Float>>> =
            edges.groupBy({ it.first }, { it.second }).toList().sortedBy { it.first }.flatMap { (at, spans) ->
                val joined = mutableListOf<Pair<Float, Float>>()
                spans.sortedBy { it.first }.forEach { span ->
                    val last = joined.lastOrNull()
                    if (last != null && span.first <= last.second + JOIN_TOLERANCE) {
                        joined[joined.size - 1] = last.first to maxOf(last.second, span.second)
                    }
                    else {
                        joined.add(span)
                    }
                }
                joined.map { at to it }
            }

        // Whether this span lies along one of [rules], which is drawn in its place.
        private fun Pair<Float, Pair<Float, Float>>.isUnder(rules: List<Pair<Float, Pair<Float, Float>>>): Boolean =
            rules.any { (at, span) ->
                abs(at - first) <= JOIN_TOLERANCE && span.first <= second.first + JOIN_TOLERANCE &&
                    second.second <= span.second + JOIN_TOLERANCE
            }

        // Edges this close, in dp, are taken as meeting: they come from sums of the same widths.
        private const val JOIN_TOLERANCE = 0.01f
    }
}
