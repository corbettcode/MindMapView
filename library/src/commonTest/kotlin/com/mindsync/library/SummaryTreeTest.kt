package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryTreeTest {
    // The root with "p" on its right and "q" on its left; "b", "c", "d" and the summary "s" below "p", and "f"
    // below "q".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d", "s").forEach { addNode(it, "p", it.uppercase()) }
        addNode("f", "q", "F")
    }

    @Test
    fun summaryIsAboutItsSiblingsAndThoseBetweenThem() {
        val tree = tree()

        tree.setSummary("s", setOf("d", "b"))

        assertTrue(tree.isSummary("s"))
        assertEquals(listOf("b", "c", "d"), tree.summaryChildIds("s"))
        assertEquals(mapOf("s" to listOf("b", "c", "d")), tree.summaries)
        assertFalse(tree.isSummary("b"))
    }

    @Test
    fun siblingAddedBetweenItsSiblingsIsInIt() {
        val tree = tree()
        tree.setSummary("s", setOf("b", "d"))

        tree.addNode("n", "p", "N", index = 1)

        assertEquals(listOf("b", "n", "c", "d"), tree.summaryChildIds("s"))
    }

    @Test
    fun removedSiblingsAreLeftOut() {
        val first = tree().apply {
            setSummary("s", setOf("b", "d"))
            removeNode("b")
        }
        val last = tree().apply {
            setSummary("s", setOf("b", "d"))
            removeNode("d")
        }
        val middle = tree().apply {
            setSummary("s", setOf("b", "d"))
            removeNode("c")
        }

        assertEquals(listOf("c", "d"), first.summaryChildIds("s"))
        assertEquals(listOf("b", "c"), last.summaryChildIds("s"))
        assertEquals(listOf("b", "d"), middle.summaryChildIds("s"))
    }

    @Test
    fun summaryWithNoSiblingsLeftIsAnOrdinaryNode() {
        val tree = tree()
        tree.setSummary("s", setOf("c"))

        tree.removeNode("c")

        assertFalse(tree.isSummary("s"))
        assertTrue(tree.summaries.isEmpty())
        assertTrue("s" in tree.getNode("p").children)
    }

    @Test
    fun summaryMovedUnderAnotherNodeIsAnOrdinaryNode() {
        val tree = tree()
        tree.setSummary("s", setOf("b", "c"))

        tree.removeNode("s")
        tree.attachNode("s", "q")

        assertFalse(tree.isSummary("s"))
    }

    @Test
    fun removedSummaryIsNoSummary() {
        val tree = tree()
        tree.setSummary("s", setOf("b"))

        tree.removeNode("p")

        assertFalse(tree.isSummary("s"))
        assertTrue(tree.summaries.isEmpty())
    }

    @Test
    fun underTheRootASummaryIsAboutTheNodesOnItsSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("r", rootId, "R")
        tree.addNode("t", rootId, "T")

        tree.setSummary("t", setOf("p", "q", "r"))

        assertEquals(listOf("p", "r"), tree.summaryChildIds("t"))
    }

    @Test
    fun summaryIsAboutNoSummaryNorItself() {
        val tree = tree()
        tree.addNode("u", "p", "U")
        tree.setSummary("s", setOf("b", "c"))

        tree.setSummary("u", setOf("b", "s", "u", "d"))

        // "s" is between "b" and "d", but a summary is about no summary.
        assertEquals(listOf("b", "c", "d"), tree.summaryChildIds("u"))
        assertFailsWith<IllegalArgumentException> { tree.setSummary("u", setOf("s", "u")) }
    }

    @Test
    fun formerSummaryIsAnOrdinarySiblingAgain() {
        val tree = tree()
        tree.setSummary("s", setOf("c"))
        tree.removeNode("c")
        tree.addNode("u", "p", "U")

        tree.setSummary("u", setOf("b", "s"))

        assertEquals(listOf("b", "d", "s"), tree.summaryChildIds("u"))
    }

    @Test
    fun summaryOfNoSiblingsIsNotMade() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> { tree.setSummary("s", setOf("f")) }
        assertFailsWith<IllegalArgumentException> { tree.setSummary("missing", setOf("b")) }
        assertTrue(tree.summaries.isEmpty())
    }

    @Test
    fun summaryIsMadeAnOrdinaryNode() {
        val tree = tree()
        tree.setSummary("s", setOf("b"))

        tree.removeSummary("s")

        assertFalse(tree.isSummary("s"))
    }

    @Test
    fun summariesArePartOfTheMapAndComeBackWithIt() {
        val tree = tree()
        val before = tree.snapshot()

        tree.setSummary("s", setOf("b", "c"))
        val withSummary = tree.snapshot()
        tree.restore(before)

        assertFalse(before.hasSameContentAs(withSummary))
        assertFalse(tree.isSummary("s"))
        tree.restore(withSummary)
        assertEquals(listOf("b", "c"), tree.summaryChildIds("s"))
    }
}
