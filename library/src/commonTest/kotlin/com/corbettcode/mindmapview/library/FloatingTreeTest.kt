package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.DpOffset
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.io.MindMapViewFile
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatingTreeTest {
    // The root with "a" and "b" on its right and "c" on its left; "d" below "a".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B")
        addNode("c", rootId, "C", side = LayoutSide.LEFT)
        addNode("d", "a", "D")
    }

    private val offset = DpOffset(Dp(120f), Dp(-80f))

    private fun Tree<*>.rootChildren() = getRootNode().children

    @Test
    fun childOfTheRootFloatsAndIsAttachedAgain() {
        val tree = tree()

        tree.setFloating("a", offset)
        assertTrue(tree.isFloating("a"))
        assertEquals(offset, tree.floatingOffsetOf("a"))

        tree.setFloating("a", null)
        assertFalse(tree.isFloating("a"))
        assertNull(tree.floatingOffsetOf("a"))
    }

    @Test
    fun onlyChildrenOfTheRootFloat() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> { tree.setFloating("d", offset) }
        assertFailsWith<IllegalArgumentException> { tree.setFloating(tree.getRootNode().id, offset) }
        assertFailsWith<IllegalArgumentException> { tree.setFloating("x", offset) }
    }

    @Test
    fun floatingTopicsComeAfterTheAttachedOnes() {
        val tree = tree()

        tree.setFloating("a", offset)
        assertEquals(listOf("b", "c", "a"), tree.rootChildren())

        tree.addNode("e", tree.getRootNode().id, "E")
        assertEquals(listOf("b", "c", "e", "a"), tree.rootChildren())

        tree.setFloating("b", offset)
        tree.setFloating("a", null)
        assertEquals(listOf("c", "e", "a", "b"), tree.rootChildren())
    }

    @Test
    fun floatingTopicMovedUnderAnotherIsAttached() {
        val tree = tree()
        tree.setFloating("a", offset)

        tree.moveNode("a", "b")
        assertFalse(tree.isFloating("a"))

        tree.moveNode("a", tree.getRootNode().id)
        assertFalse(tree.isFloating("a"), "it is attached when it comes back")
    }

    @Test
    fun removedFloatingTopicLeavesNoFlag() {
        val tree = tree()
        tree.setFloating("b", offset)

        tree.removeNode("b")

        assertFalse(tree.isFloating("b"))
        assertNull(tree.snapshot().floatingOffsets["b"])
    }

    @Test
    fun floatingTopicAndTheNodesBelowItAreOnTheRight() {
        val tree = tree()

        tree.setFloating("c", offset)
        tree.setRootChildSide("c", LayoutSide.LEFT)
        tree.addNode("f", "c", "F")

        assertEquals(LayoutSide.RIGHT, tree.sideOf("c"))
        assertEquals(LayoutSide.RIGHT, tree.sideOf("f"))
    }

    @Test
    fun snapshotKeepsTheFloatingTopics() {
        val tree = tree()
        val before = tree.snapshot()
        tree.setFloating("a", offset)
        val after = tree.snapshot()

        assertFalse(before.hasSameContentAs(after))
        tree.restore(before)
        assertFalse(tree.isFloating("a"))
        tree.restore(after)
        assertEquals(offset, tree.floatingOffsetOf("a"))
    }

    @Test
    fun copiedFloatingTopicFloatsOnTheRootAndIsAttachedElsewhere() {
        val tree = tree()
        tree.setFloating("a", offset)
        val state = MindMapState(tree).apply { setTree(tree) }
        state.select(tree.getNode("a"))
        state.copyNode()

        state.select(state.tree.getRootNode())
        state.pasteNode()
        val onRoot = state.tree.getRootNode().children.last()
        state.select(state.tree.getNode("b"))
        state.pasteNode()
        val onB = state.tree.getNode("b").children.single()

        assertEquals(offset + DpOffset(Dp(24f), Dp(24f)), state.tree.floatingOffsetOf(onRoot))
        assertFalse(state.tree.isFloating(onB))
        assertEquals(1, state.tree.getNode(onRoot).children.size, "with the nodes below it")
    }

    @Test
    fun mapWithoutFloatingTopicsIsAsBefore() {
        val tree = tree()
        val text = MindMapViewFile.write(tree)

        tree.setFloating("a", offset)
        tree.setFloating("a", null)
        tree.moveNode("a", tree.getRootNode().id, 0)

        assertEquals(text, MindMapViewFile.write(tree))
    }
}
