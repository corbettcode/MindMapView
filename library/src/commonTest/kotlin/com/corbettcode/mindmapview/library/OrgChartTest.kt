package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.Boundary
import com.corbettcode.mindmapview.library.data.DpOffset
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrgChartTest {
    // The root with "a" on its right, "b" on its left and "c" on its right; "d" and "e" below "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(600f), Dp(100f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B", side = LayoutSide.LEFT)
        addNode("c", rootId, "C")
        addNode("d", "b", "D")
        addNode("e", "b", "E")
        structure = MapStructure.ORG_DOWN
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
    fun branchesAreInARowBelowTheRootWhateverTheirSide() {
        val tree = tree().arranged()

        listOf("a", "b", "c").forEach { assertTrue(tree.top(it) > tree.bottom(tree.rootId()), it) }
        assertTrue(tree.x("a") < tree.x("b") && tree.x("b") < tree.x("c"))
        assertTrue(tree.top("d") > tree.bottom("b"))
    }

    @Test
    fun upHasThemAboveTheRoot() {
        val tree = tree().apply { structure = MapStructure.ORG_UP }.arranged()

        listOf("a", "b", "c").forEach { assertTrue(tree.bottom(it) < tree.top(tree.rootId()), it) }
        assertTrue(tree.bottom("d") < tree.top("b"))
        assertTrue(tree.nodes.values.all { it.path.centerY.dpVal > 0f }, "kept on the map")
    }

    @Test
    fun summaryIsBelowTheBranchesItSumsUp() {
        val tree = tree().apply {
            addNode("s", getRootNode().id, "S", index = 2)
            setSummary("s", setOf("a", "b"))
        }.arranged()

        assertTrue(tree.top("s") > tree.bottom("d"))
        assertEquals((tree.left("a") + tree.right("e")) / 2, tree.x("s"), 1f)
    }

    @Test
    fun boundaryLeavesRoomLeftAndRight() {
        val without = tree().arranged()
        val with = tree().apply { addBoundary(Boundary("bd", getRootNode().id, setOf("b"))) }.arranged()

        val gap = { tree: Tree<*> -> tree.left("c") - tree.right("e") }
        assertTrue(gap(with) > gap(without))
    }

    @Test
    fun floatingTopicsBranchGrowsDown() {
        val tree = tree().apply {
            addNode("f", getRootNode().id, "F")
            addNode("g", "f", "G")
            setFloating("f", DpOffset(Dp(400f), Dp(200f)))
        }.arranged()

        assertTrue(tree.top("g") > tree.bottom("f"))
        assertEquals(tree.x("f"), tree.x("g"), 0.01f)
    }

    @Test
    fun summaryOfBranchesOnTwoSidesIsAboutBothOnlyOutsideTheMindMap() {
        val tree = tree().apply {
            addNode("s", getRootNode().id, "S", index = 2)
            setSummary("s", setOf("a", "b"))
        }

        assertEquals(listOf("a", "b"), tree.summaryChildIds("s"))
        tree.structure = MapStructure.MIND_MAP
        assertEquals(listOf("a"), tree.summaryChildIds("s"), "in a mind map \"b\" is on the other side")
        tree.structure = MapStructure.ORG_DOWN
        assertEquals(listOf("a", "b"), tree.summaryChildIds("s"))
    }
}

