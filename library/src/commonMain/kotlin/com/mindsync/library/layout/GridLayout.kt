package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.withCenter
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.Dp

// The space inside a cell of a grid, around what is in it, as in a matrix's.
private const val GRID_PADDING = MATRIX_PADDING

// The space between the summaries below a grid.
private const val GRID_GAP = 10f

// The least width and height of a cell of a grid.
private const val GRID_MIN_CELL = 40f

/** How many columns a grid has when none is set, if it has that many topics. */
internal const val GRID_DEFAULT_COLUMNS = 4

/**
 * The grid laid out from [ownerId], in dp: its header across the top, from [headerTop] to the first of [rowEdges], and
 * below it the cells of [mainIds], [columns] to a row in reading order. [columnEdges] are the left of each column and
 * the right of the last. Each row of cells has the top of its main topics' part, the top of its children's part, and
 * so on, in [rowEdges], the bottom of the last row last: two edges a row, or one for a row whose topics have no
 * children, its cells one part each.
 */
internal class GridCells(
    val ownerId: String
    , val headerTop: Float
    , val columns: Int
    , val columnEdges: List<Float>
    , val rowEdges: List<Float>
    , val mainIds: List<String>
    // For each row of cells, the index in [rowEdges] of its top and whether it has a part for children.
    , private val rows: List<Pair<Int, Boolean>>
) {
    val left: Float get() = columnEdges.first()

    val right: Float get() = columnEdges.last()

    val bottom: Float get() = rowEdges.last()

    /** The column and the row of the cell of the main topic at [index]. */
    fun placeOf(index: Int): Pair<Int, Int> = index % columns to index / columns

    /** The part of the cell of the main topic at [index] it is in. */
    fun headOf(index: Int): CellRect {
        val (column, row) = placeOf(index)
        val top = rows[row].first
        return CellRect(columnEdges[column], rowEdges[top], columnEdges[column + 1], rowEdges[top + 1])
    }

    /** The part of the cell of the main topic at [index] its children are in, or null for a row with none. */
    fun bodyOf(index: Int): CellRect? {
        val (column, row) = placeOf(index)
        val (top, hasBody) = rows[row]
        if (!hasBody) return null
        return CellRect(columnEdges[column], rowEdges[top + 1], columnEdges[column + 1], rowEdges[top + 2])
    }

    /** The table this grid is drawn as: the header, and each cell's parts, with a heavier rule under the header. */
    fun toCells(): TableCells {
        val header = CellRect(left, headerTop, right, rowEdges.first())
        val cells = mainIds.indices.flatMap { listOfNotNull(headOf(it), bodyOf(it)) }
        val rules = if (mainIds.isEmpty()) emptyList() else listOf(TableLine(left, header.bottom, right, header.bottom))
        return TableCells.of(listOf(header) + cells, rules)
    }
}

/**
 * The children of [owner] that are the main topics of its grid: its shown ones but the floating topics and the
 * summaries.
 */
internal fun gridMainIds(
    tree: Tree<*>
    , owner: NodeData<*>
): List<String> {
    val ids = if (owner.parentId == null) owner.children else tree.visibleChildren(owner)
    return ids.filterNot { tree.isFloating(it) || tree.isSummary(it) }
}

/** Whether the main topic [mainId] has topics below it in the lower part of its cell. */
private fun hasBody(
    tree: Tree<*>
    , mainId: String
): Boolean = tree.branchStructureOf(mainId) == null && tree.visibleChildren(tree.getNode(mainId)).isNotEmpty()

/**
 * The edges of the topics below the main topic [mainId] as they are laid out now, left, top, right and bottom, in
 * dp: the block that goes in the lower part of its cell.
 */
private fun bodyBlockOf(
    tree: Tree<*>
    , mainId: String
): CellRect {
    val main = tree.getNode(mainId)
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    tree.visibleChildren(main).forEach { childId ->
        tree.doVisiblePreorderTraversal(tree.getNode(childId)) { node ->
            left = minOf(left, (node.path.centerX - halfWidth(node)).dpVal)
            top = minOf(top, (node.path.centerY - halfHeight(node)).dpVal)
            right = maxOf(right, (node.path.centerX + halfWidth(node)).dpVal)
            bottom = maxOf(bottom, (node.path.centerY + halfHeight(node)).dpVal)
        }
    }
    return CellRect(left, top, right, bottom)
}

/**
 * Returns the cells of the grid laid out from [owner], its header centered on it, from the sizes of its topics, of
 * the branches of main topics with a structure of their own by [ownedReach], and of the blocks of the topics below
 * the others by [bodyBlock].
 */
internal fun gridCells(
    tree: Tree<*>
    , owner: NodeData<*>
    , ownedReach: (String) -> Reach
    , bodyBlock: (String) -> CellRect
): GridCells {
    val mainIds = gridMainIds(tree, owner)
    val columns = (tree.gridColumnsOf(owner.id) ?: GRID_DEFAULT_COLUMNS).coerceAtMost(mainIds.size).coerceAtLeast(1)
    fun headReach(id: String): Reach = if (tree.branchStructureOf(id) != null) ownedReach(id) else {
        val node = tree.getNode(id)
        Reach(halfWidth(node).dpVal, halfWidth(node).dpVal, halfHeight(node).dpVal, halfHeight(node).dpVal)
    }
    val heads = mainIds.map { headReach(it) }
    val bodies = mainIds.map { id -> if (hasBody(tree, id)) bodyBlock(id) else null }
    var width = GRID_MIN_CELL
    mainIds.indices.forEach { index ->
        width = maxOf(width, heads[index].left + heads[index].right + 2 * GRID_PADDING)
        bodies[index]?.let { width = maxOf(width, it.right - it.left + 2 * GRID_PADDING) }
    }
    val headerWidth = halfWidth(owner).dpVal * 2 + 2 * GRID_PADDING
    if (headerWidth > width * columns) width = headerWidth / columns
    val headerHeight = maxOf(GRID_MIN_CELL, halfHeight(owner).dpVal * 2 + 2 * GRID_PADDING)
    val left = tableLeftFor(tree, owner, width * columns, GRID_PADDING)
    val headerTop = owner.path.centerY.dpVal - headerHeight / 2
    val rowEdges = mutableListOf(headerTop + headerHeight)
    val rows = mainIds.indices.chunked(columns).map { indexes ->
        val top = rowEdges.size - 1
        val headHeight = indexes.maxOf { heads[it].top + heads[it].bottom + 2 * GRID_PADDING }
        rowEdges.add(rowEdges.last() + maxOf(GRID_MIN_CELL, headHeight))
        val bodyHeight = indexes.maxOf { index -> bodies[index]?.let { it.bottom - it.top } ?: 0f }
        val hasBody = bodyHeight > 0f
        if (hasBody) rowEdges.add(rowEdges.last() + maxOf(GRID_MIN_CELL, bodyHeight + 2 * GRID_PADDING))
        top to hasBody
    }
    val columnEdges = (0..columns).map { left + it * width }
    return GridCells(owner.id, headerTop, columns, columnEdges, rowEdges, mainIds, rows)
}

/**
 * Lays a branch out as a grid: its topic in a header across the top, and below it its children's cells, a set number
 * to a row ([Tree.gridColumnsOf], or as many as fit in a row of [GRID_DEFAULT_COLUMNS]), all as wide as the widest.
 * Each main topic is centered in the top part of its cell, and the topics below it are in the other part, laid out by
 * [right] as a logic chart of its children would be, each growing right or in a structure of its own. A main topic
 * with a structure of its own has its branch in its cell. A summary of the main topics is below the grid, its branch
 * laid out by [down].
 */
internal class GridLayout(
    private val right: MindMapLayoutManager
    , private val down: MindMapLayoutManager
) {
    /** Places the topics below [owner] in the cells of a grid around it. */
    fun arrange(
        owner: NodeData<*>
        , tree: Tree<*>
    ) {
        // What is in each cell is laid out where it is first, to know how big it is.
        val reaches = mutableMapOf<String, Reach>()
        val ownedReach = { id: String ->
            reaches.getOrPut(id) {
                right.arrangeBelow(tree.getNode(id), tree)
                reachOf(tree, id)
            }
        }
        val bodyBlock = { id: String ->
            right.arrangeBelow(tree.getNode(id), tree)
            bodyBlockOf(tree, id)
        }
        val grid = gridCells(tree, owner, ownedReach, bodyBlock)
        grid.mainIds.forEachIndexed { index, mainId ->
            val head = grid.headOf(index)
            val main = tree.getNode(mainId)
            if (tree.branchStructureOf(mainId) != null) {
                val mainReach = ownedReach(mainId)
                val x = (head.left + head.right) / 2 + (mainReach.left - mainReach.right) / 2
                val y = (head.top + head.bottom) / 2 + (mainReach.top - mainReach.bottom) / 2
                shift(mainId, x - main.path.centerX.dpVal, y - main.path.centerY.dpVal, tree)
                return@forEachIndexed
            }
            val placed = main.withCenter(Dp((head.left + head.right) / 2), Dp((head.top + head.bottom) / 2))
            tree.setNode(mainId, placed)
            val body = grid.bodyOf(index)
            if (body == null || !hasBody(tree, mainId)) return@forEachIndexed
            // The block is laid out from where the main topic is now, then moved to the top left of its part.
            right.arrangeBelow(placed, tree)
            val block = bodyBlockOf(tree, mainId)
            tree.visibleChildren(placed).forEach { childId ->
                shift(childId, body.left + GRID_PADDING - block.left, body.top + GRID_PADDING - block.top, tree)
            }
        }
        placeSummaries(owner, grid, tree)
    }

    /** Returns the cells of the grid of [owner] as its topics are laid out now, as it is drawn. */
    fun cellsOf(
        owner: NodeData<*>
        , tree: Tree<*>
    ): GridCells = gridCells(tree, owner, { reachOf(tree, it) }, { bodyBlockOf(tree, it) })

    // A summary of the main topics is below the whole grid, centered on it, the later ones below the earlier.
    private fun placeSummaries(
        owner: NodeData<*>
        , grid: GridCells
        , tree: Tree<*>
    ) {
        val ids = if (owner.parentId == null) owner.children else tree.visibleChildren(owner)
        var top = grid.bottom + SUMMARY_OFFSET.value
        ids.filter { tree.isSummary(it) && !tree.isFloating(it) }.forEach { summaryId ->
            val summary = tree.getNode(summaryId)
            val placed = summary.withCenter(Dp((grid.left + grid.right) / 2), Dp(top + halfHeight(summary).dpVal))
            tree.setNode(summaryId, placed)
            down.arrangeBelow(placed, tree)
            top = maxOf(top + halfHeight(summary).dpVal * 2, top + reachOf(tree, summaryId).let { it.top + it.bottom })
            top += GRID_GAP
        }
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
