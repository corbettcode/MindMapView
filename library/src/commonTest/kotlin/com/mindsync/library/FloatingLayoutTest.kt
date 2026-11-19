package com.mindsync.library

import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatingLayoutTest {
    // The root with "p" and "f" on its right and "q" on its left; "b" and "c" below "p", "g" and "h" below "f".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("f", rootId, "F")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c").forEach { addNode(it, "p", it.uppercase()) }
        listOf("g", "h").forEach { addNode(it, "f", it.uppercase()) }
    }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<*>.centerX(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.centerY(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.left(id: String) = getNode(id).let { it.path.centerX.dpVal - halfWidth(it).dpVal }
    private fun Tree<*>.top(id: String) = getNode(id).let { it.path.centerY.dpVal - halfHeight(it).dpVal }
    private fun Tree<*>.rootId() = getRootNode().id

    private val offset = DpOffset(Dp(300f), Dp(250f))

    @Test
    fun floatingTopicIsAtItsOffsetFromTheRoot() {
        val tree = tree().apply { setFloating("f", offset) }.arranged()

        assertEquals(tree.centerX(tree.rootId()) + 300f, tree.centerX("f"), 0.01f)
        assertEquals(tree.centerY(tree.rootId()) + 250f, tree.centerY("f"), 0.01f)
    }

    @Test
    fun nodesBelowItAreToItsRight() {
        val tree = tree().apply { setFloating("f", offset) }.arranged()

        assertTrue(tree.left("g") > tree.centerX("f"))
        assertTrue(tree.left("h") > tree.centerX("f"))
        assertEquals((tree.centerY("g") + tree.centerY("h")) / 2, tree.centerY("f"), 0.01f)
    }

    @Test
    fun attachedTopicsAreLaidOutAsIfItWereNotThere() {
        val withFloating = tree().apply { setFloating("f", offset) }.arranged()
        val without = tree().apply { removeNode("f") }.arranged()

        listOf("p", "b", "c", "q").forEach { id ->
            assertEquals(without.centerY(id) - without.centerY(without.rootId()), withFloating.centerY(id) -
                withFloating.centerY(withFloating.rootId()), 0.01f, id)
        }
    }

    @Test
    fun itStaysWhereItIsWhenTheMapChanges() {
        val tree = tree().apply { setFloating("f", offset) }.arranged()
        val before = tree.centerY("f") - tree.centerY(tree.rootId())

        tree.addNode("d", "p", "D")
        tree.addNode("e", "p", "E")
        tree.arranged()

        assertEquals(before, tree.centerY("f") - tree.centerY(tree.rootId()), 0.01f)
    }

    @Test
    fun itMovesAlongWithADraggedRoot() {
        val tree = tree().apply { setFloating("f", offset) }.arranged()
        tree.moveRoot(Dp(900f), Dp(700f))

        tree.arranged()

        assertEquals(1200f, tree.centerX("f"), 0.01f)
        assertEquals(950f, tree.centerY("f"), 0.01f)
    }

    @Test
    fun oneAboveAndLeftOfEverythingIsOnTheMap() {
        val tree = tree().apply { setFloating("f", DpOffset(Dp(-2000f), Dp(-1500f))) }.arranged()

        val nodes = tree.nodes.keys.filter { it == tree.rootId() || tree.getNode(it).parentId != null }
        assertTrue(nodes.all { tree.left(it) >= 0f }, "all are right of the left edge")
        assertTrue(nodes.all { tree.top(it) >= 0f }, "all are below the top")
        assertEquals(tree.centerX(tree.rootId()) - 2000f, tree.centerX("f"), 0.01f)
    }
}
