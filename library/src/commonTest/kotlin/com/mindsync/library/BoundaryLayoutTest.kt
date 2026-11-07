package com.mindsync.library

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.BOUNDARY_PADDING
import com.mindsync.library.layout.boundaryRect
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoundaryLayoutTest {
    // One pixel a dp, so the rectangles of boundaries are in the dp the nodes are placed in.
    private val density = Density(1f)
    private val padding = BOUNDARY_PADDING.value

    // The space the layout leaves between siblings, and between their branches.
    private val spacing = 50f

    // The root with "p" and "s" on its right and "q" on its left; "b", "c" and "d" below "p"; "e" below "b",
    // and "g" below "e".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("s", rootId, "S")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
        addNode("e", "b", "E")
        addNode("g", "e", "G")
    }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<Any>.top(id: String): Float = getNode(id).let { it.path.centerY.dpVal - halfHeight(it).dpVal }

    private fun Tree<Any>.bottom(id: String): Float =
        getNode(id).let { it.path.centerY.dpVal + halfHeight(it).dpVal }

    private fun Tree<Any>.rectOf(id: String): Rect =
        assertNotNull(with(density) { boundaryRect(this@rectOf, boundaries.first { it.id == id }) })

    @Test
    fun withoutBoundariesSiblingsAreTheSpacingApart() {
        val tree = tree().arranged()

        assertEquals(spacing, tree.top("d") - tree.bottom("c"), 0.01f)
    }

    @Test
    fun boundaryMovesItsNeighboursAwayByItsPadding() {
        val tree = tree()
        tree.addBoundary(Boundary("x", "p", setOf("c")))
        tree.arranged()

        assertEquals(spacing + padding, tree.top("d") - tree.bottom("c"), 0.01f)
        assertEquals(spacing + padding, tree.top("c") - tree.bottom("g"), 0.01f)
    }

    @Test
    fun boundariesOfNeighboursAreTheSpacingApart() {
        val tree = tree()
        tree.addBoundary(Boundary("one", "p", setOf("c")))
        tree.addBoundary(Boundary("two", "p", setOf("d")))
        tree.arranged()

        assertEquals(spacing, tree.rectOf("two").top - tree.rectOf("one").bottom, 0.01f)
    }

    @Test
    fun boundariesWithinEachOtherStayClearOfTheNextBranch() {
        val tree = tree()
        tree.addBoundary(Boundary("inner", "e", setOf("g")))
        tree.addBoundary(Boundary("middle", "b", setOf("e")))
        tree.addBoundary(Boundary("outer", "p", setOf("b")))
        tree.addBoundary(Boundary("next", "p", setOf("c")))
        tree.arranged()

        val outer = tree.rectOf("outer")
        val next = tree.rectOf("next")
        assertTrue(next.top - outer.bottom >= spacing - 0.01f, "$outer and $next are too close")
    }

    @Test
    fun boundaryOfAChildOfTheRootMovesTheOtherChildrenOnItsSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addBoundary(Boundary("x", rootId, setOf("s")))
        tree.arranged()

        val pBranchBottom = maxOf(tree.bottom("p"), tree.bottom("d"))
        assertEquals(spacing + padding, tree.top("s") - pBranchBottom, 0.01f)
    }

    @Test
    fun boundaryInTheMiddleOfItsSiblingsKeepsThemCenteredOnTheirParent() {
        val tree = tree()
        tree.addBoundary(Boundary("x", "p", setOf("c")))
        tree.arranged()

        // "b" reaches down to "g" below it, so the group is measured from the top of "b" to the bottom of "d".
        val middle = (tree.top("b") + tree.bottom("d")) / 2
        assertEquals(tree.getNode("p").path.centerY.dpVal, middle, 0.01f)
    }
}
