package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.OutlineNode
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

    @Test
    fun outlineIsParsedIntoNodesBelowTheLinesIndentedLess() {
        assertEquals(
            listOf(
                OutlineNode("parent", listOf(OutlineNode("first", listOf(OutlineNode("below"))), OutlineNode("second")))
                , OutlineNode("other")
            )
            , parseOutline("parent\n\tfirst\n\t\tbelow\n\n  second\nother\n")
        )
    }

    @Test
    fun bulletsAreLeftOutAndMarkupIsEscaped() {
        assertEquals(
            listOf(OutlineNode("a &lt;b&gt; &amp; c", listOf(OutlineNode("item"))))
            , parseOutline("- a <b> & c\n    * item")
        )
    }

    @Test
    fun outlineOfANodeIsParsedBackIntoTheSameNodes() {
        tree.addNode("parent", rootId, "parent")
        tree.addNode("child", "parent", "child")
        assertEquals(
            listOf(OutlineNode("parent", listOf(OutlineNode("child"))))
            , parseOutline(nodeOutline(tree, "parent"))
        )
    }

    @Test
    fun blankTextIsNoNodes() {
        assertEquals(emptyList(), parseOutline(" \n\t\n"))
    }
}
