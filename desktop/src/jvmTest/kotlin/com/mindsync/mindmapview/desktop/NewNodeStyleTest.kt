package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NewNodeStyleTest {
    private val tree = Tree<Any>(Dp(0f), Dp(0f))
    private val rootId = tree.getRootNode().id

    private fun style(
        parentId: String,
        side: LayoutSide = LayoutSide.RIGHT,
        shape: NodeShape? = null,
        defaultShape: NodeShape = NodeShape.RECTANGLE,
    ) = newNodeStyle(tree, tree.getNode(parentId), side, shape, defaultShape)

    @Test
    fun nodeOfAParentWithoutChildrenIsARectangleInItsOwnColors() {
        assertEquals(
            NewNodeStyle(NodeShape.RECTANGLE, NodeShape.RECTANGLE.defaultColor, NodeShape.RECTANGLE.defaultTextColor),
            style(rootId),
        )
    }

    @Test
    fun nodeOfAParentWithoutChildrenIsOfTheDefaultShape() {
        assertEquals(
            NewNodeStyle(NodeShape.OVAL, NodeShape.OVAL.defaultColor, NodeShape.OVAL.defaultTextColor),
            style(rootId, defaultShape = NodeShape.OVAL),
        )
    }

    @Test
    fun nodeIsLikeTheLastChildOfItsParent() {
        tree.addNode("parent", rootId, "parent")
        tree.addNode("first", "parent", "first", NodeShape.CIRCLE, Color.Red, Color.White)
        tree.addNode("last", "parent", "last", NodeShape.OVAL, Color.Blue, Color.Yellow, note = "<p>a note</p>")

        assertEquals(NewNodeStyle(NodeShape.OVAL, Color.Blue, Color.Yellow), style("parent"))
    }

    @Test
    fun nodeOfTheRootIsLikeTheLastChildOnItsSide() {
        tree.addNode("left", rootId, "left", NodeShape.CIRCLE, Color.Red, Color.White, LayoutSide.LEFT)
        tree.addNode("right", rootId, "right", NodeShape.STADIUM, Color.Blue, Color.Yellow, LayoutSide.RIGHT)

        assertEquals(NewNodeStyle(NodeShape.CIRCLE, Color.Red, Color.White), style(rootId, LayoutSide.LEFT))
        assertEquals(NewNodeStyle(NodeShape.STADIUM, Color.Blue, Color.Yellow), style(rootId, LayoutSide.RIGHT))
    }

    @Test
    fun nodeOfTheRootWithoutChildrenOnItsSideIsARectangleInItsOwnColors() {
        tree.addNode("right", rootId, "right", NodeShape.STADIUM, Color.Blue, Color.Yellow, LayoutSide.RIGHT)

        assertEquals(
            NewNodeStyle(NodeShape.RECTANGLE, NodeShape.RECTANGLE.defaultColor, NodeShape.RECTANGLE.defaultTextColor),
            style(rootId, LayoutSide.LEFT),
        )
    }

    @Test
    fun shapeThatWasChosenIsKeptAndOnlyTakesTheColorsOfALastChildOfThatShape() {
        tree.addNode("last", rootId, "last", NodeShape.OVAL, Color.Blue, Color.Yellow)

        assertEquals(NewNodeStyle(NodeShape.OVAL, Color.Blue, Color.Yellow), style(rootId, shape = NodeShape.OVAL))
        assertEquals(
            NewNodeStyle(NodeShape.CIRCLE, NodeShape.CIRCLE.defaultColor, NodeShape.CIRCLE.defaultTextColor),
            style(rootId, shape = NodeShape.CIRCLE),
        )
    }
}
