package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TreeMoveTest {
    // The root with "a" and "b" on its right; "c", "d" and "e" below "a", and "f" below "c".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B")
        listOf("c", "d", "e").forEach { addNode(it, "a", it.uppercase()) }
        addNode("f", "c", "F")
    }

    @Test
    fun nodeMovesToAnotherParentAtAnIndex() {
        val tree = tree().apply { addNode("g", "b", "G") }

        tree.moveNode("d", "b", 0)

        assertEquals(listOf("c", "e"), tree.getNode("a").children)
        assertEquals(listOf("d", "g"), tree.getNode("b").children)
        assertEquals("b", tree.getNode("d").parentId)
    }

    @Test
    fun nodeMovesUpAndDownAmongItsSiblings() {
        val tree = tree()

        tree.moveNode("e", "a", 0)
        assertEquals(listOf("e", "c", "d"), tree.getNode("a").children)

        tree.moveNode("e", "a", 2)
        assertEquals(listOf("c", "d", "e"), tree.getNode("a").children)
    }

    @Test
    fun nodeMovesToTheEndWithNoIndexOrOnePastIt() {
        val tree = tree()

        tree.moveNode("c", "a")
        assertEquals(listOf("d", "e", "c"), tree.getNode("a").children)

        tree.moveNode("d", "a", 99)
        assertEquals(listOf("e", "c", "d"), tree.getNode("a").children)
    }

    @Test
    fun nodeMovesWithTheNodesBelowIt() {
        val tree = tree()

        tree.moveNode("c", "b")

        assertEquals(listOf("f"), tree.getNode("c").children)
        assertEquals("c", tree.getNode("f").parentId)
    }

    @Test
    fun nodeIsNotMovedIntoItsOwnBranch() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> { tree.moveNode("a", "f") }
        assertFailsWith<IllegalArgumentException> { tree.moveNode("a", "a") }
        assertEquals(listOf("c", "d", "e"), tree.getNode("a").children)
    }

    @Test
    fun rootAndMissingNodesAreNotMoved() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> { tree.moveNode(tree.getRootNode().id, "a") }
        assertFailsWith<IllegalArgumentException> { tree.moveNode("x", "a") }
        assertFailsWith<IllegalArgumentException> { tree.moveNode("c", "x") }
    }

    @Test
    fun foldedParentIsUnfolded() {
        val tree = tree().apply { addNode("g", "b", "G") }
        tree.setFolded("b", true)

        tree.moveNode("d", "b")

        assertFalse(tree.isFolded("b"))
    }

    @Test
    fun nodeMovedToTheRootGoesOnItsSide() {
        val tree = tree()

        tree.moveNode("d", tree.getRootNode().id, 1, LayoutSide.LEFT)

        assertEquals(listOf("a", "d", "b"), tree.getRootNode().children)
        assertEquals(LayoutSide.LEFT, tree.sideOf("d"))

        tree.moveNode("d", "b")
        assertEquals(LayoutSide.RIGHT, tree.sideOf("d"))
    }

    @Test
    fun nodeMovedOutOfASummaryLeavesItAboutTheRest() {
        val tree = tree().apply {
            addNode("s", "a", "S")
            setSummary("s", setOf("c", "e"))
        }

        tree.moveNode("e", "b")

        assertEquals(listOf("c", "d"), tree.summaryChildIds("s"))
    }
}
