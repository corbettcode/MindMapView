package com.mindsync.library

import androidx.compose.ui.geometry.Offset
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundaryGeometryTest {
    // One pixel a dp, so a rectangle in pixels can be told from the dp of the nodes.
    private val density = Density(1f)
    private val padding = BOUNDARY_PADDING.value

    // The root at the origin with "p" on its right and "q" on its left; "b", "c" and "d" below "p", "e" below
    // "b", and "f" below "q". Each node is where it is put, as a layout would put it.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
        addNode("e", "b", "E")
        addNode("f", "q", "F")
        place("p", 200f, 0f)
        place("q", -200f, 0f)
        place("b", 400f, -100f)
        place("c", 400f, 0f)
        place("d", 400f, 100f)
        place("e", 600f, -100f)
        place("f", -400f, 0f)
    }

    private fun Tree<Any>.place(
        id: String
        , x: Float
        , y: Float
    ) {
        val node = getNode(id)
        updateNode(id, node.description, node.children, Dp(x), Dp(y))
    }

    // Where the node is, in pixels, which are dp here.
    private fun Tree<Any>.rectOf(id: String): Rect {
        val node = getNode(id)
        val center = Offset(node.path.centerX.dpVal, node.path.centerY.dpVal)
        val half = Offset(halfWidth(node).dpVal, halfHeight(node).dpVal)
        return Rect(center - half, center + half)
    }

    private fun Tree<Any>.rectOf(boundary: Boundary): Rect? = with(density) { boundaryRect(this@rectOf, boundary) }

    private fun assertRect(
        expected: Rect
        , actual: Rect?
    ) {
        assertNotNull(actual)
        listOf(
            expected.left to actual.left
            , expected.top to actual.top
            , expected.right to actual.right
            , expected.bottom to actual.bottom
        ).forEach { (e, a) -> assertEquals(e, a, 0.01f, "$actual is not $expected") }
    }

    private fun Rect.contains(other: Rect, margin: Float): Boolean =
        left <= other.left - margin && top <= other.top - margin && right >= other.right + margin &&
            bottom >= other.bottom + margin

    @Test
    fun boundaryOfOneNodeIsAroundIt() {
        val tree = tree()
        val boundary = Boundary("x", "p", setOf("c"))

        assertRect(tree.rectOf("c").inflate(padding), tree.rectOf(boundary))
    }

    @Test
    fun boundaryIsAroundTheNodesBelowItsChildren() {
        val tree = tree()
        val boundary = Boundary("x", "p", setOf("b"))

        val expected = Rect(
            tree.rectOf("b").left
            , tree.rectOf("b").top
            , tree.rectOf("e").right
            , tree.rectOf("b").bottom
        )
        assertRect(expected.inflate(padding), tree.rectOf(boundary))
    }

    @Test
    fun boundaryIsAroundTheChildrenBetweenItsOwn() {
        val tree = tree()
        tree.addBoundary(Boundary("x", "p", setOf("b", "d")))

        val rect = assertNotNull(tree.rectOf(tree.boundaries.single()))

        assertTrue(rect.contains(tree.rectOf("c"), padding))
        assertTrue(rect.contains(tree.rectOf("e"), padding))
    }

    @Test
    fun nodesHiddenInAFoldedNodeAreLeftOut() {
        val tree = tree()
        tree.setFolded("b", true)
        val boundary = Boundary("x", "p", setOf("b"))

        assertRect(tree.rectOf("b").inflate(padding), tree.rectOf(boundary))
    }

    @Test
    fun boundaryOfAFoldedParentIsNotDrawn() {
        val tree = tree()
        tree.setFolded("p", true)

        assertNull(tree.rectOf(Boundary("x", "p", setOf("c"))))
    }

    @Test
    fun boundaryOfAParentHiddenInAFoldedNodeIsNotDrawn() {
        val tree = tree()
        tree.setFolded("b", true)

        assertNull(tree.rectOf(Boundary("x", "b", setOf("e"))))
    }

    @Test
    fun boundaryWithinAnotherIsInsideIt() {
        val tree = tree()
        tree.addBoundary(Boundary("inner", "b", setOf("e")))
        tree.addBoundary(Boundary("outer", "p", setOf("b")))

        val inner = assertNotNull(tree.rectOf(tree.boundaries.first { it.id == "inner" }))
        val outer = assertNotNull(tree.rectOf(tree.boundaries.first { it.id == "outer" }))

        assertRect(tree.rectOf("e").inflate(padding), inner)
        assertTrue(outer.contains(inner, padding - 0.01f), "$outer is not around $inner")
    }

    @Test
    fun boundaryOnTheLeftOfTheRootIsAroundItsNodes() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        val boundary = Boundary("x", rootId, setOf("q"))

        val expected = Rect(
            tree.rectOf("f").left
            , tree.rectOf("q").top
            , tree.rectOf("q").right
            , tree.rectOf("q").bottom
        )
        assertRect(expected.inflate(padding), tree.rectOf(boundary))
    }
}
