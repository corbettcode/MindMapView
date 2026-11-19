package com.mindsync.library.outline

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineRowsTest {
    // The root with "p" on its right and "q" on its left; "b" and "c" below "p", and "f" below "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        addNode("b", "p", "B")
        addNode("c", "p", "C")
        addNode("f", "b", "F")
    }

    private fun Tree<*>.rowIds() = outlineRows(this).map { it.id to it.depth }

    @Test
    fun rowsAreTheTopicsInOrderWithTheirDepth() {
        val tree = tree()
        val rootId = tree.getRootNode().id

        assertEquals(listOf(rootId to 0, "p" to 1, "b" to 2, "f" to 3, "c" to 2, "q" to 1), tree.rowIds())
        val rows = outlineRows(tree).associateBy { it.id }
        assertTrue(rows.getValue("b").hasChildren)
        assertEquals(false, rows.getValue("c").hasChildren)
    }

    @Test
    fun topicsInAFoldedTopicAreNoRows() {
        val tree = tree().apply { setFolded("b", true) }

        assertEquals(listOf("p", "b", "c", "q"), tree.rowIds().drop(1).map { it.first })
        assertTrue(outlineRows(tree).single { it.id == "b" }.isFolded)
    }

    @Test
    fun summaryIsARowAfterItsSiblings() {
        val tree = tree().apply {
            addNode("s", "p", "S")
            setSummary("s", setOf("b", "c"))
        }

        val rows = outlineRows(tree)

        assertEquals(listOf("c", "s", "q"), rows.takeLast(3).map { it.id })
        assertTrue(rows.single { it.id == "s" }.isSummary)
    }

    @Test
    fun mapOfTheRootAloneIsOneRow() {
        val tree = Tree<Any>(Dp(0f), Dp(0f))

        assertEquals(listOf(tree.getRootNode().id to 0), tree.rowIds())
    }

    @Test
    fun rowsFollowAMove() {
        val tree = tree()

        tree.moveNode("c", "q")

        assertEquals(listOf("p", "b", "f", "q", "c"), tree.rowIds().drop(1).map { it.first })
        assertEquals(2, outlineRows(tree).single { it.id == "c" }.depth)
    }

    @Test
    fun rowsAboveAndBelowAreThoseShown() {
        val tree = tree().apply { setFolded("b", true) }
        val rows = outlineRows(tree)

        assertEquals("b", rows.rowBelow("p"))
        assertEquals("c", rows.rowBelow("b"))
        assertEquals("b", rows.rowAbove("c"))
        assertNull(rows.rowAbove(tree.getRootNode().id))
        assertNull(rows.rowBelow("q"))
        assertNull(rows.rowBelow("f"))
    }
}
