package com.mindsync.library

import androidx.compose.ui.unit.Density
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimelineTest {
    // The root with "a", "b", "c" and "d" below it; "a1" and "a2" below "a", "b1" below "b", "c1" and "c2" below "c".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        listOf("a", "b", "c", "d").forEach { addNode(it, rootId, it.uppercase()) }
        listOf("a1", "a2").forEach { addNode(it, "a", "Long topic ${it.uppercase()}") }
        addNode("b1", "b", "B1")
        listOf("c1", "c2").forEach { addNode(it, "c", "Long topic ${it.uppercase()}") }
        structure = MapStructure.TIMELINE
    }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.left(id: String) = getNode(id).let { x(id) - halfWidth(it).dpVal }
    private fun Tree<*>.right(id: String) = getNode(id).let { x(id) + halfWidth(it).dpVal }
    private fun Tree<*>.top(id: String) = getNode(id).let { y(id) - halfHeight(it).dpVal }
    private fun Tree<*>.bottom(id: String) = getNode(id).let { y(id) + halfHeight(it).dpVal }
    private fun Tree<*>.rootId() = getRootNode().id

    @Test
    fun topicsAreOnTheAxisToTheRightOfTheRootInOrder() {
        val tree = tree().arranged()

        listOf("a", "b", "c", "d").forEach { assertEquals(tree.y(tree.rootId()), tree.y(it), 0.01f, it) }
        assertTrue(tree.left("a") > tree.right(tree.rootId()))
        listOf("a", "b", "c", "d").zipWithNext().forEach { (one, next) ->
            assertTrue(tree.right(one) < tree.left(next), "$one before $next")
        }
    }

    @Test
    fun branchesGrowUpAndDownByTurns() {
        val tree = tree().arranged()

        assertTrue(tree.bottom("a1") < tree.top("a"), "the first goes up")
        assertTrue(tree.top("b1") > tree.bottom("b"), "the second down")
        assertTrue(tree.bottom("c1") < tree.top("c"), "the third up")
    }

    @Test
    fun branchesOnOneSideAreClearOfEachOther() {
        val tree = tree().arranged()

        assertTrue(tree.right("a2") < tree.left("c1"))
    }

    @Test
    fun theAxisRunsFromTheRootToTheLastOfItsTopics() {
        val tree = tree().arranged()

        val (start, end) = assertNotNull(with(Density(1f)) { timelineAxis(tree) })

        assertEquals(tree.right(tree.rootId()), start.x, 0.01f)
        assertEquals(tree.x("d"), end.x, 0.01f)
        assertEquals(tree.y(tree.rootId()), start.y, 0.01f)
    }

    @Test
    fun summaryIsBelowTheTopicsItSumsUp() {
        val tree = tree().apply {
            addNode("s", getRootNode().id, "S", index = 2)
            setSummary("s", setOf("a", "b"))
        }.arranged()

        assertTrue(tree.top("s") > tree.bottom("b1"))
        assertTrue(tree.x("s") > tree.left("a1") && tree.x("s") < tree.x("c"))
        assertTrue(tree.bottom("c1") < tree.top("c"), "the summary takes no turn")
    }

    @Test
    fun mapIsKeptOnTheMap() {
        val tree = tree().arranged()

        tree.nodes.values.filter { tree.isVisible(it.id) }.forEach { node ->
            assertTrue(node.path.centerY.dpVal - halfHeight(node).dpVal >= 0f, node.id)
            assertTrue(node.path.centerX.dpVal - halfWidth(node).dpVal >= 0f, node.id)
        }
    }
}
