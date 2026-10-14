package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeOutlineTest {
    private val tree = Tree<Any>(Dp(0f), Dp(0f))
    private val rootId = tree.getRootNode().id

    @Test
    fun nodeWithoutChildrenIsOneLine() {
        tree.addNode("node", rootId, "<p>a <b>bold</b> node</p>")
        assertEquals("a bold node", nodeOutline(tree, "node"))
    }

    @Test
    fun nodesBelowAreIndentedByLevel() {
        tree.addNode("parent", rootId, "parent")
        tree.addNode("first", "parent", "first")
        tree.addNode("below", "first", "<p>two</p><p>paragraphs</p>")
        tree.addNode("second", "parent", "second")
        tree.addNode("other", rootId, "other")
        assertEquals("parent\n\tfirst\n\t\ttwo paragraphs\n\tsecond", nodeOutline(tree, "parent"))
    }
}
