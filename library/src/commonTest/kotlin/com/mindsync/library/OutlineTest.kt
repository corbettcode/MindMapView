package com.mindsync.library

import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.shape
import com.mindsync.library.io.Outline
import com.mindsync.library.io.OutlineItem
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlineTest {
    @Test
    fun eachItemIsANodeBelowTheNodeOfItsParent() {
        val tree = Outline.toTree(
            OutlineItem(
                title = "projects"
                , note = "<p>/home/me/projects</p>"
                , children = listOf(
                    OutlineItem("app", children = listOf(OutlineItem("src")))
                    , OutlineItem("a < b & c")
                )
            )
        )

        val root = tree.getRootNode()
        assertEquals(RichText.fromPlainText("projects"), root.description)
        assertEquals("<p>/home/me/projects</p>", root.note)
        val (app, other) = root.children.map { tree.getNode(it) }
        assertEquals("app", app.description)
        assertEquals(RichText.fromPlainText("a < b & c"), other.description)
        assertEquals(root.id, app.parentId)
        val src = tree.getNode(app.children.single())
        assertEquals("src", src.description)
        assertEquals(app.id, src.parentId)
    }

    @Test
    fun aFoldedItemWithChildrenIsAFoldedNode() {
        val tree = Outline.toTree(
            OutlineItem(
                title = "root"
                , isFolded = true
                , children = listOf(
                    OutlineItem("folded", isFolded = true, children = listOf(OutlineItem("hidden")))
                    , OutlineItem("empty", isFolded = true)
                )
            )
        )

        val root = tree.getRootNode()
        val (folded, empty) = root.children
        assertTrue(tree.isFolded(folded))
        assertFalse(tree.isFolded(empty))
        assertFalse(tree.isFolded(root.id))
    }

    @Test
    fun topicsAndSubtopicsTakeTheirOwnShapes() {
        val tree = Outline.toTree(
            OutlineItem("root", children = listOf(OutlineItem("topic", children = listOf(OutlineItem("subtopic")))))
            , topicShape = NodeShape.RECTANGLE
            , subtopicShape = NodeShape.LINE
        )

        val topic = tree.getNode(tree.getRootNode().children.single())
        assertEquals(NodeShape.RECTANGLE, topic.shape)
        assertEquals(NodeShape.LINE, tree.getNode(topic.children.single()).shape)
    }
}
