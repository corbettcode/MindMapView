package com.corbettcode.mindmapview.library.layout

import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.data.withCenter
import com.corbettcode.mindmapview.library.halfHeight
import com.corbettcode.mindmapview.library.halfWidth
import com.corbettcode.mindmapview.library.util.Dp

/** The space inside a cell of a matrix, around what is in it. */
internal const val MATRIX_PADDING = 10f

// The space between the topics stacked in a cell.
private const val CELL_GAP = 10f

// The least width and height of a column and a row of a matrix.
private const val MIN_CELL = 40f

/**
 * The grid of a matrix laid out from [ownerId]: the left of each column and the top of each row, one more of each than
 * there are columns and rows, in dp. The corner cell holds the owner; [groupIds] are its children, the rows of a
 * matrix by rows or the columns of one by columns, each in the first cell of its row or column; [labels] are the
 * headers of the other way, in order, null for the one with no header, of the topics without a label; [cells] holds
 * the topics of each cell, by the index of their group and of their label; and [summaries] the summaries of each
 * cell's topics, in the cell of the first topic each is about. A group with a structure of its own has no cells: its
 * branch is in its header's cell.
 */
internal class MatrixGrid(
    val ownerId: String
    , val isRows: Boolean
    , val columnEdges: List<Float>
    , val rowEdges: List<Float>
    , val groupIds: List<String>
    , val labels: List<String?>
    , val cells: Map<Pair<Int, Int>, List<String>>
    , val summaries: Map<Pair<Int, Int>, List<String>> = emptyMap()
) {
    /** The column and row of the cell of [group] and [label], by their indexes, the header row and column being 0. */
    fun cellOf(
        group: Int
        , label: Int
    ): Pair<Int, Int> = if (isRows) label + 1 to group + 1 else group + 1 to label + 1

    /** The column and row of the header of [label]. */
    fun headerOf(label: Int): Pair<Int, Int> = if (isRows) label + 1 to 0 else 0 to label + 1

    /** The column and row of the header topic of [group]. */
    fun groupHeaderOf(group: Int): Pair<Int, Int> = if (isRows) 0 to group + 1 else group + 1 to 0

    /** The center of the cell at [column] and [row]. */
    fun centerOf(
        column: Int
        , row: Int
    ): Pair<Float, Float> =
        (columnEdges[column] + columnEdges[column + 1]) / 2 to (rowEdges[row] + rowEdges[row + 1]) / 2

    /** The table this grid is drawn as: every cell, with heavier rules past the headers. */
    fun toCells(): TableCells {
        val cells = columnEdges.zipWithNext().flatMap { (left, right) ->
            rowEdges.zipWithNext().map { (top, bottom) -> CellRect(left, top, right, bottom) }
        }
        val rules = listOf(
            TableLine(columnEdges[1], rowEdges.first(), columnEdges[1], rowEdges.last())
            , TableLine(columnEdges.first(), rowEdges[1], columnEdges.last(), rowEdges[1])
        )
        return TableCells.of(cells, rules)
    }
}

/**
 * How far a topic's branch reaches from its center, left, right, up and down, in dp: what it takes in a cell.
 */
internal class Reach(
    val left: Float
    , val right: Float
    , val top: Float
    , val bottom: Float
)

/**
 * Returns the grid of the matrix laid out from [owner], its corner cell centered on it, from the sizes of its topics,
 * of its headers by [headerSize] and of the branches of the topics in its cells by [cellReach]. A matrix by rows when
 * [isRows], and by columns otherwise.
 */
internal fun matrixGrid(
    tree: Tree<*>
    , owner: NodeData<*>
    , isRows: Boolean
    , headerSize: (String) -> Pair<Float, Float>
    , cellReach: (String) -> Reach
): MatrixGrid {
    val groupIds = matrixGroupIds(tree, owner)
    val labelsOf = groupIds.associateWith { id ->
        if (tree.branchStructureOf(id) != null) emptyList()
        else tree.visibleChildren(tree.getNode(id)).filterNot { tree.isSummary(it) }.map { it to tree.labelOf(it) }
    }
    val all = labelsOf.values.flatten()
    // The labels in the order they are first met, then the column with no header when a topic has no label.
    val labels: List<String?> =
        all.mapNotNull { it.second }.distinct() + if (all.any { it.second == null }) listOf(null) else emptyList()
    val cells = mutableMapOf<Pair<Int, Int>, List<String>>()
    groupIds.forEachIndexed { group, id ->
        labelsOf.getValue(id).groupBy({ it.second }, { it.first }).forEach { (label, ids) ->
            cells[group to labels.indexOf(label)] = ids
        }
    }
    val summaries = mutableMapOf<Pair<Int, Int>, List<String>>()
    groupIds.forEachIndexed { group, id ->
        if (tree.branchStructureOf(id) != null) return@forEachIndexed
        tree.visibleChildren(tree.getNode(id)).filter { tree.isSummary(it) }.forEach { summaryId ->
            val firstId = tree.summaryChildIds(summaryId).firstOrNull { tree.isVisible(it) } ?: return@forEach
            val key = group to labels.indexOf(tree.labelOf(firstId))
            summaries[key] = summaries[key].orEmpty() + summaryId
        }
    }
    val grid = MatrixGrid(owner.id, isRows, emptyList(), emptyList(), groupIds, labels, cells, summaries)
    val columns = (if (isRows) labels.size else groupIds.size) + 1
    val rows = (if (isRows) groupIds.size else labels.size) + 1
    val widths = FloatArray(columns) { MIN_CELL }
    val heights = FloatArray(rows) { MIN_CELL }
    fun fit(cell: Pair<Int, Int>, width: Float, height: Float) {
        widths[cell.first] = maxOf(widths[cell.first], width + 2 * MATRIX_PADDING)
        heights[cell.second] = maxOf(heights[cell.second], height + 2 * MATRIX_PADDING)
    }
    fit(0 to 0, halfWidth(owner).dpVal * 2, halfHeight(owner).dpVal * 2)
    groupIds.forEachIndexed { group, id ->
        val node = tree.getNode(id)
        if (tree.branchStructureOf(id) != null) {
            val reach = cellReach(id)
            fit(grid.groupHeaderOf(group), reach.left + reach.right, reach.top + reach.bottom)
        }
        else {
            fit(grid.groupHeaderOf(group), halfWidth(node).dpVal * 2, halfHeight(node).dpVal * 2)
        }
    }
    labels.forEachIndexed { label, text ->
        val (width, height) = text?.let(headerSize) ?: (0f to 0f)
        fit(grid.headerOf(label), width, height)
    }
    cells.forEach { (key, ids) ->
        val reaches = ids.map(cellReach)
        // A summary is past the topics of its cell, so it widens the cell by itself and its gap.
        val summaryReaches = summaries[key].orEmpty().map(cellReach)
        val summaryWidth = summaryReaches.maxOfOrNull { SUMMARY_OFFSET.value + it.left + it.right } ?: 0f
        val width = reaches.maxOf { it.left + it.right } + summaryWidth
        val stacked = reaches.sumOf { (it.top + it.bottom).toDouble() }.toFloat() + CELL_GAP * (ids.size - 1)
        val height = maxOf(stacked, summaryReaches.maxOfOrNull { it.top + it.bottom } ?: 0f)
        fit(grid.cellOf(key.first, key.second), width, height)
    }
    val left = owner.path.centerX.dpVal - widths[0] / 2
    val top = owner.path.centerY.dpVal - heights[0] / 2
    val columnEdges = widths.runningFold(left) { edge, width -> edge + width }
    val rowEdges = heights.runningFold(top) { edge, height -> edge + height }
    return MatrixGrid(owner.id, isRows, columnEdges, rowEdges, groupIds, labels, cells, summaries)
}

/** Returns how far the branch of the node [id] reaches from its center, as it is laid out now. */
internal fun reachOf(
    tree: Tree<*>
    , id: String
): Reach {
    val node = tree.getNode(id)
    val centerX = node.path.centerX.dpVal
    val centerY = node.path.centerY.dpVal
    var left = 0f
    var right = 0f
    var top = 0f
    var bottom = 0f
    tree.doVisiblePreorderTraversal(node) { each ->
        val x = each.path.centerX.dpVal
        val y = each.path.centerY.dpVal
        left = maxOf(left, centerX - x + halfWidth(each).dpVal)
        right = maxOf(right, x - centerX + halfWidth(each).dpVal)
        top = maxOf(top, centerY - y + halfHeight(each).dpVal)
        bottom = maxOf(bottom, y - centerY + halfHeight(each).dpVal)
    }
    return Reach(left, right, top, bottom)
}

/**
 * The children of [owner] that are the rows or columns of its matrix: its shown ones, but floating topics and
 * summaries.
 */
internal fun matrixGroupIds(
    tree: Tree<*>
    , owner: NodeData<*>
): List<String> {
    val ids = if (owner.parentId == null) owner.children else tree.visibleChildren(owner)
    return ids.filterNot { tree.isFloating(it) || tree.isSummary(it) }
}

/**
 * Lays a branch out as a matrix, a table: its topic in the corner, its children as rows when [isRows] (as columns
 * otherwise) each in the first cell of its own, and their children in the cells of their rows by their labels, a
 * column for each label in the order they are first met and one with no header for those without, the other way.
 * The topics of a cell are stacked in it, each with its branch laid out by [right], growing right. A summary of the
 * rows is past the table, below it for rows and right of it for columns, its branch laid out by [across].
 */
internal class MatrixLayout(
    private val isRows: Boolean
    , private val right: MindMapLayoutManager
    , private val across: MindMapLayoutManager
) {
    /** Measures the headers of a matrix, in dp; set by the manager when it can measure text. */
    var headerSize: (String) -> Pair<Float, Float> = { text -> text.length * 8f to 20f }

    /** Places the children of [owner] and the topics below them in a table around it. */
    fun arrange(
        owner: NodeData<*>
        , tree: Tree<*>
    ) {
        // Each topic of a cell is laid out where it is first, to know how far its branch reaches.
        val reaches = mutableMapOf<String, Reach>()
        val grid = matrixGrid(tree, owner, isRows, headerSize) { id ->
            reaches.getOrPut(id) { layOutAndReach(tree.getNode(id), tree) }
        }
        grid.groupIds.forEachIndexed { group, id ->
            val (x, y) = grid.groupHeaderOf(group).let { grid.centerOf(it.first, it.second) }
            val node = tree.getNode(id)
            if (tree.branchStructureOf(id) != null) {
                // Its branch, in its own structure, is centered in the header's cell.
                val reach = reaches.getValue(id)
                val dx = x + (reach.left - reach.right) / 2 - node.path.centerX.dpVal
                val dy = y + (reach.top - reach.bottom) / 2 - node.path.centerY.dpVal
                shift(id, dx, dy, tree)
            }
            else {
                tree.setNode(id, node.withCenter(Dp(x), Dp(y)))
            }
        }
        grid.cells.forEach { (key, ids) ->
            val (column, row) = grid.cellOf(key.first, key.second)
            val cellLeft = grid.columnEdges[column] + MATRIX_PADDING
            var y = grid.rowEdges[row] + MATRIX_PADDING
            ids.forEach { id ->
                val reach = reaches.getValue(id)
                val node = tree.getNode(id)
                shift(
                    id
                    , cellLeft + reach.left - node.path.centerX.dpVal
                    , y + reach.top - node.path.centerY.dpVal
                    , tree
                )
                y += reach.top + reach.bottom + CELL_GAP
            }
            grid.summaries[key].orEmpty().forEach { summaryId -> placeCellSummary(summaryId, tree) }
        }
        placeSummaries(owner, grid, tree)
    }

    // A summary of rows is past the whole table, centered on it, as the rows are the table's width.
    private fun placeSummaries(
        owner: NodeData<*>
        , grid: MatrixGrid
        , tree: Tree<*>
    ) {
        val ids = if (owner.parentId == null) owner.children else tree.visibleChildren(owner)
        var offset = SUMMARY_OFFSET.value
        ids.filter { tree.isSummary(it) && !tree.isFloating(it) }.forEach { summaryId ->
            val summary = tree.getNode(summaryId)
            val placed = if (isRows) {
                val centerX = (grid.columnEdges.first() + grid.columnEdges.last()) / 2
                summary.withCenter(Dp(centerX), Dp(grid.rowEdges.last() + offset + halfHeight(summary).dpVal))
            }
            else {
                val centerY = (grid.rowEdges.first() + grid.rowEdges.last()) / 2
                summary.withCenter(Dp(grid.columnEdges.last() + offset + halfWidth(summary).dpVal), Dp(centerY))
            }
            tree.setNode(summaryId, placed)
            across.arrangeBelow(placed, tree)
            offset += (if (isRows) halfHeight(summary) else halfWidth(summary)).dpVal * 2 + CELL_GAP
        }
    }

    // A summary of topics in a cell is past them, centered on them, as in a logic chart.
    private fun placeCellSummary(
        summaryId: String
        , tree: Tree<*>
    ) {
        val rangeIds = tree.summaryChildIds(summaryId).filter { tree.isVisible(it) }
        if (rangeIds.isEmpty()) return
        val reaches = rangeIds.associateWith { reachOf(tree, it) }
        val rightEdge = rangeIds.maxOf { tree.getNode(it).path.centerX.dpVal + reaches.getValue(it).right }
        val top = rangeIds.minOf { tree.getNode(it).path.centerY.dpVal - reaches.getValue(it).top }
        val bottom = rangeIds.maxOf { tree.getNode(it).path.centerY.dpVal + reaches.getValue(it).bottom }
        val summary = tree.getNode(summaryId)
        val centerX = rightEdge + SUMMARY_OFFSET.value + right.backReach(summary, tree).dpVal
        val placed = summary.withCenter(Dp(centerX), Dp((top + bottom) / 2))
        tree.setNode(summaryId, placed)
        right.arrangeBelow(placed, tree)
    }

    /** Returns the grid of the matrix of [owner] as its topics are laid out now, as it is drawn. */
    fun gridOf(
        owner: NodeData<*>
        , tree: Tree<*>
    ): MatrixGrid = matrixGrid(tree, owner, isRows, headerSize) { reachOf(tree, it) }

    // Lays out the branch of [node] around it where it is, and returns how far it reaches from its center.
    private fun layOutAndReach(
        node: NodeData<*>
        , tree: Tree<*>
    ): Reach {
        right.arrangeBelow(node, tree)
        return reachOf(tree, node.id)
    }

    // Moves the node and the shown nodes below it by ([dx], [dy]).
    private fun shift(
        id: String
        , dx: Float
        , dy: Float
        , tree: Tree<*>
    ) {
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            tree.setNode(node.id, node.withCenter(node.path.centerX + dx, node.path.centerY + dy))
        }
    }
}
