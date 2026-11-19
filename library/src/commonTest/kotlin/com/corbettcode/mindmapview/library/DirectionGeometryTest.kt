package com.corbettcode.mindmapview.library

import androidx.compose.ui.unit.Density
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.layout.MindMapLayoutManager
import com.corbettcode.mindmapview.library.layout.summaryBracket
import com.corbettcode.mindmapview.library.model.GrowDirection
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DirectionGeometryTest {
    // One pixel a dp, so the geometry's pixels are the layout's dp.
    private val density = Density(1f)

    // An org chart: the root with "a", "b" and "c" below it, "d" and "e" below "b", and a summary "s" of "a" and "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(600f), Dp(100f)).apply {
        val rootId = getRootNode().id
        listOf("a", "b", "c").forEach { addNode(it, rootId, it.uppercase()) }
        addNode("d", "b", "D")
        addNode("e", "b", "E")
        addNode("s", rootId, "S", index = 2)
        setSummary("s", setOf("a", "b"))
        structure = MapStructure.ORG_DOWN
        MindMapLayoutManager(LayoutSide.RIGHT, GrowDirection.DOWN, isAllRootChildren = true)
            .arrangeChildren(getRootNode(), this)
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.top(id: String) = getNode(id).let { it.path.centerY.dpVal - halfHeight(it).dpVal }
    private fun Tree<*>.bottom(id: String) = getNode(id).let { it.path.centerY.dpVal + halfHeight(it).dpVal }

    @Test
    fun linesLeaveTheBottomOfAParentAndEnterTheTopOfAChild() {
        val tree = tree()

        with(density) {
            val start = lineAnchor(tree.getNode("b"), GrowDirection.DOWN, isOutgoing = true)
            val end = lineAnchor(tree.getNode("d"), GrowDirection.DOWN, isOutgoing = false)
            assertEquals(tree.x("b"), start.x, 0.01f)
            assertEquals(tree.bottom("b"), start.y, 0.01f)
            assertEquals(tree.x("d"), end.x, 0.01f)
            assertEquals(tree.top("d"), end.y, 0.01f)
            assertEquals(tree.top("b"), lineAnchor(tree.getNode("b"), GrowDirection.UP, isOutgoing = true).y, 0.01f)
        }
    }

    @Test
    fun addButtonAndFoldRingAreBelowTheNode() {
        val tree = tree()

        with(density) {
            val button = addButtonCenter(tree, tree.getNode("b"))
            assertEquals(tree.x("b"), button.x, 0.01f)
            assertTrue(button.y > tree.bottom("b"))
            assertEquals(button, foldMarkerCenter(tree, tree.getNode("b")))
            val rootButton = addButtonCenter(tree, tree.getRootNode())
            assertTrue(rootButton.y > tree.bottom(tree.getRootNode().id), "the root has one button, below it")
        }
    }

    @Test
    fun summaryBracketIsBelowTheBranchesItSumsUp() {
        val tree = tree()

        val bracket = assertNotNull(with(density) { summaryBracket(tree, "s") })

        assertFalse(bracket.isHorizontal)
        // Below "d" and "e", the lowest of the nodes it is about, and across "a" to "b"'s branch.
        assertTrue(bracket.backX > tree.bottom("d"))
        assertTrue(bracket.pointX > bracket.backX)
        assertTrue(bracket.top < tree.x("a") && bracket.bottom > tree.x("e"))
        val point = bracket.at(bracket.pointX, bracket.pointY)
        assertEquals(bracket.pointX, point.y)
    }

    @Test
    fun droppedTopicTakesItsPlaceBetweenItsSiblingsByX() {
        val tree = tree()
        val rootId = tree.getRootNode().id

        val isMoved = tree.moveAmongSiblings("c", Dp(tree.x("a") - 10f), Dp(500f))

        assertTrue(isMoved)
        assertEquals("c", tree.getNode(rootId).children.first())
    }

    @Test
    fun mindMapButtonsAreWhereTheyWere() {
        val tree = tree().apply { structure = MapStructure.MIND_MAP }

        with(density) {
            listOf("b", "d").forEach { id ->
                assertEquals(addButtonCenter(tree.getNode(id), tree.sideOf(id)), addButtonCenter(tree, tree.getNode(id)))
            }
        }
    }
}
