package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.withCenter
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.Dp

// The space inside a cell of a tree table, around its topic, as in a matrix's.
private const val TREE_TABLE_PADDING = MATRIX_PADDING

// The least width and height of a column and a row of a tree table.
private const val TREE_TABLE_MIN_CELL = 40f

/** Where a topic of a tree table is in it: its column, by its depth below the owner from 1, and its rows. */
internal class TreeTableSpan(
    val depth: Int
    , val firstRow: Int
    , val lastRow: Int
    , val isLeaf: Boolean
)

/**
 * The grid of a tree table laid out from [ownerId], in dp. The owner's header is across the top, from [headerTop] to
 * the first of [rowEdges]; below it each topic's cell is in the column of its depth and spans the rows of its leaves,
 * a leaf's reaching to the table's right. [columnEdges] are the left of each column and the right of the last, and
 * [rowEdges] the top of each row and the bottom of the last. [spans] holds each topic of the table, in the order they
 * are drawn, top down and parents first.
 */
internal class TreeTableGrid(
    val ownerId: String
    , val headerTop: Float
    , val columnEdges: List<Float>
    , val rowEdges: List<Float>
    , val spans: Map<String, TreeTableSpan>
) {
    val left: Float get() = columnEdges.first()

    val right: Float get() = columnEdges.last()

    /** The cell of the topic [id] of the table. */
    fun cellOf(id: String): CellRect {
        val span = spans.getValue(id)
        val cellRight = if (span.isLeaf) right else columnEdges[span.depth]
        return CellRect(columnEdges[span.depth - 1], rowEdges[span.firstRow], cellRight, rowEdges[span.lastRow + 1])
    }

    /** The table this grid is drawn as: the header and each topic's cell, with a heavier rule under the header. */
    fun toCells(): TableCells {
        val header = CellRect(left, headerTop, right, rowEdges.first())
        val rules = if (spans.isEmpty()) emptyList() else listOf(TableLine(left, header.bottom, right, header.bottom))
        return TableCells.of(listOf(header) + spans.keys.map(::cellOf), rules)
    }
}

/**
 * Returns the grid of the tree table laid out from [owner], its header centered on it, from the sizes of its topics
 * and of the branches of those with a structure of their own by [ownedReach].
 */
internal fun treeTableGrid(
    tree: Tree<*>
    , owner: NodeData<*>
    , ownedReach: (String) -> Reach
): TreeTableGrid {
    val order = mutableListOf<Pair<String, Int>>()
    fun visit(id: String, depth: Int) {
        order.add(id to depth)
        // A topic with a structure of its own is a leaf of the table, its branch in its cell.
        if (tree.branchStructureOf(id) != null) return
        treeTableChildren(tree, tree.getNode(id)).forEach { visit(it, depth + 1) }
    }
    treeTableChildren(tree, owner).forEach { visit(it, 1) }
    val leafIds = order.filter { (id, _) -> order.none { tree.getNode(it.first).parentId == id } }.map { it.first }
    val reaches = order.associate { (id, _) -> id to reachIn(tree, id, ownedReach) }
    val depths = order.toMap()
    val columns = leafIds.maxOfOrNull { depths.getValue(it) } ?: 0
    val widths = FloatArray(columns + 1) { if (it == 0) 0f else TREE_TABLE_MIN_CELL }
    fun widthOf(id: String) = reaches.getValue(id).let { it.left + it.right } + 2 * TREE_TABLE_PADDING
    order.filter { (id, depth) -> id !in leafIds || depth == columns }.forEach { (id, depth) ->
        widths[depth] = maxOf(widths[depth], widthOf(id))
    }
    // A leaf above the deepest reaches across to the last column, which widens for it when they are too narrow.
    leafIds.forEach { id ->
        val depth = depths.getValue(id)
        val excess = widthOf(id) - (depth..columns).sumOf { widths[it].toDouble() }.toFloat()
        if (excess > 0) widths[columns] += excess
    }
    val headerWidth = maxOf(TREE_TABLE_MIN_CELL, halfWidth(owner).dpVal * 2 + 2 * TREE_TABLE_PADDING)
    val tableWidth = widths.sum()
    if (headerWidth > tableWidth) widths[columns] += headerWidth - tableWidth
    val leafRows = leafIds.withIndex().associate { (row, id) -> id to row }
    val spans = mutableMapOf<String, TreeTableSpan>()
    order.asReversed().forEach { (id, depth) ->
        val childSpans = tree.getNode(id).children.mapNotNull { spans[it] }
        val span = if (childSpans.isEmpty()) {
            TreeTableSpan(depth, leafRows.getValue(id), leafRows.getValue(id), isLeaf = true)
        }
        else {
            TreeTableSpan(depth, childSpans.minOf { it.firstRow }, childSpans.maxOf { it.lastRow }, isLeaf = false)
        }
        spans[id] = span
    }
    fun heightOf(id: String) = reaches.getValue(id).let { it.top + it.bottom } + 2 * TREE_TABLE_PADDING
    val heights = FloatArray(leafIds.size) { row -> maxOf(TREE_TABLE_MIN_CELL, heightOf(leafIds[row])) }
    // A topic taller than the rows of its leaves makes its last row taller; the deepest first, as they are inside.
    order.sortedByDescending { it.second }.forEach { (id, _) ->
        val span = spans.getValue(id)
        val rowsHeight = (span.firstRow..span.lastRow).sumOf { heights[it].toDouble() }.toFloat()
        val excess = heightOf(id) - rowsHeight
        if (excess > 0) heights[span.lastRow] += excess
    }
    val headerHeight = maxOf(TREE_TABLE_MIN_CELL, halfHeight(owner).dpVal * 2 + 2 * TREE_TABLE_PADDING)
    // A table of only its header has one column, the header's.
    val columnWidths = if (columns == 0) listOf(widths[0]) else widths.drop(1)
    val left = tableLeftFor(tree, owner, columnWidths.sum(), TREE_TABLE_PADDING)
    val headerTop = owner.path.centerY.dpVal - headerHeight / 2
    val columnEdges = columnWidths.runningFold(left) { edge, width -> edge + width }
    val rowEdges = heights.toList().runningFold(headerTop + headerHeight) { edge, height -> edge + height }
    val orderedSpans = order.associate { (id, _) -> id to spans.getValue(id) }
    return TreeTableGrid(owner.id, headerTop, columnEdges, rowEdges, orderedSpans)
}

/**
 * The children of [node] that are in its tree table, below it: its shown ones but the floating topics and the
 * summaries.
 */
internal fun treeTableChildren(
    tree: Tree<*>
    , node: NodeData<*>
): List<String> {
    val ids = if (node.parentId == null) node.children else tree.visibleChildren(node)
    return ids.filterNot { tree.isFloating(it) || tree.isSummary(it) }
}

// How far the topic [id] reaches from its center in its cell: its branch's box when it has a structure of its own,
// and itself otherwise.
private fun reachIn(
    tree: Tree<*>
    , id: String
    , ownedReach: (String) -> Reach
): Reach {
    if (tree.branchStructureOf(id) != null) return ownedReach(id)
    val node = tree.getNode(id)
    val halfWidth = halfWidth(node).dpVal
    val halfHeight = halfHeight(node).dpVal
    return Reach(halfWidth, halfWidth, halfHeight, halfHeight)
}

/**
 * Lays a branch out as a tree table, nested blocks: its topic in a header across the top, and below it each topic in
 * a cell left of its children's cells, as tall as they are together, a column for each depth. The leaves are rows,
 * their cells reaching to the table's right, each topic at the right of its cell, so that they line up for comparing;
 * the other topics are at the top left of theirs. A topic with a structure of its own is a leaf, its branch laid out
 * in its cell. A summary is past the table's right, centered on the rows it is about, its branch laid out by [right].
 */
internal class TreeTableLayout(
    private val right: MindMapLayoutManager
) {
    /** What lays out the branches of the topics with a structure of their own. */
    var ownedBranches: OwnedBranches? = null

    /** Places the topics below [owner] in the cells of a tree table around it. */
    fun arrange(
        owner: NodeData<*>
        , tree: Tree<*>
    ) {
        val branches = ownedBranches
        // A branch with a structure of its own is measured once, by laying it out where its topic is.
        val owned = mutableMapOf<String, Reach>()
        val ownedReach = { id: String ->
            owned.getOrPut(id) {
                branches?.boxOf(tree.getNode(id), tree)
                    ?.let { Reach(it.left.dpVal, it.right.dpVal, it.top.dpVal, it.bottom.dpVal) }
                    ?: reachOf(tree, id)
            }
        }
        val grid = treeTableGrid(tree, owner, ownedReach)
        grid.spans.forEach { (id, span) ->
            val reach = reachIn(tree, id, ownedReach)
            val cell = grid.cellOf(id)
            val x = if (span.isLeaf) cell.right - TREE_TABLE_PADDING - reach.right
            else cell.left + TREE_TABLE_PADDING + reach.left
            val y = cell.top + TREE_TABLE_PADDING + reach.top
            val placed = tree.getNode(id).withCenter(Dp(x), Dp(y))
            tree.setNode(id, placed)
            if (branches != null && tree.branchStructureOf(id) != null) branches.arrangeBelow(placed, tree)
        }
        placeSummaries(owner, grid, tree)
    }

    /** Returns the grid of the tree table laid out from [owner] as its topics are laid out now, as it is drawn. */
    fun gridOf(
        owner: NodeData<*>
        , tree: Tree<*>
    ): TreeTableGrid = treeTableGrid(tree, owner) { reachOf(tree, it) }

    // A summary is past the table's right, centered on the rows of the topics it is about, and past the summaries
    // placed before it that are beside some of the same rows.
    private fun placeSummaries(
        owner: NodeData<*>
        , grid: TreeTableGrid
        , tree: Tree<*>
    ) {
        // The rows each summary placed is beside, by their first and last, and how far right its branch reaches.
        val placedSummaries = mutableListOf<Triple<Int, Int, Float>>()
        (listOf(owner.id) + grid.spans.keys).forEach { parentId ->
            val parent = tree.getNode(parentId)
            val ids = if (parent.parentId == null) parent.children else tree.visibleChildren(parent)
            ids.filter { tree.isSummary(it) && !tree.isFloating(it) }.forEach { summaryId ->
                val rangeSpans = tree.summaryChildIds(summaryId).mapNotNull { grid.spans[it] }
                if (rangeSpans.isEmpty()) return@forEach
                val firstRow = rangeSpans.minOf { it.firstRow }
                val lastRow = rangeSpans.maxOf { it.lastRow }
                val x = placedSummaries.filter { (first, last, _) -> first <= lastRow && firstRow <= last }
                    .maxOfOrNull { it.third } ?: grid.right
                val summary = tree.getNode(summaryId)
                val centerY = (grid.rowEdges[firstRow] + grid.rowEdges[lastRow + 1]) / 2
                val centerX = x + SUMMARY_OFFSET.value + right.backReach(summary, tree).dpVal
                val placed = summary.withCenter(Dp(centerX), Dp(centerY))
                tree.setNode(summaryId, placed)
                right.arrangeBelow(placed, tree)
                placedSummaries.add(Triple(firstRow, lastRow, reachOf(tree, summaryId).right + centerX))
            }
        }
    }
}
