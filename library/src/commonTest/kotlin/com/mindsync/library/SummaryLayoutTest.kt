package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.layout.SUMMARY_OFFSET
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryLayoutTest {
    private val offset = SUMMARY_OFFSET.value

    // The space the layout leaves between siblings, and between their branches.
    private val spacing = 50f

    // The root with "p" on its right and "q" on its left; "b", "c" and "d" below "p", "e" below "c", and "f" and
    // "g" below "q".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
        addNode("e", "c", "E")
        listOf("f", "g").forEach { addNode(it, "q", it.uppercase()) }
    }

    // Adds the summary [id] of [childIds] right after the last of them, as the app adds one.
    private fun Tree<Any>.summary(
        id: String
        , vararg childIds: String
    ) {
        val parentId = getNode(childIds.first()).parentId!!
        val index = getNode(parentId).children.indexOfLast { it in childIds } + 1
        addNode(id, parentId, id.uppercase(), side = sideOf(childIds.first()), index = index)
        setSummary(id, childIds.toSet())
    }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<Any>.left(id: String): Float = getNode(id).let { it.path.centerX.dpVal - halfWidth(it).dpVal }
    private fun Tree<Any>.right(id: String): Float = getNode(id).let { it.path.centerX.dpVal + halfWidth(it).dpVal }
    private fun Tree<Any>.top(id: String): Float = getNode(id).let { it.path.centerY.dpVal - halfHeight(it).dpVal }
    private fun Tree<Any>.bottom(id: String): Float =
        getNode(id).let { it.path.centerY.dpVal + halfHeight(it).dpVal }
    private fun Tree<Any>.centerY(id: String): Float = getNode(id).path.centerY.dpVal

    @Test
    fun summaryIsPastTheFurthestOfItsSiblingsAndTheNodesBelowThem() {
        val tree = tree()
        tree.summary("s", "b", "c")
        tree.arranged()

        // "e" below "c" is the furthest out.
        assertEquals(tree.right("e") + offset, tree.left("s"), 0.01f)
    }

    @Test
    fun summaryIsCenteredOnItsSiblings() {
        val tree = tree()
        tree.summary("s", "b", "c")
        tree.arranged()

        assertEquals((tree.top("b") + tree.bottom("c")) / 2, tree.centerY("s"), 0.01f)
    }

    @Test
    fun summaryIsNotStackedWithItsSiblings() {
        val tree = tree()
        tree.summary("s", "b", "c")
        tree.arranged()

        assertEquals(spacing, tree.top("d") - tree.bottom("c"), 0.01f)
    }

    @Test
    fun onTheLeftASummaryIsLeftOfItsSiblings() {
        val tree = tree()
        tree.summary("t", "f", "g")
        tree.arranged()

        assertEquals(minOf(tree.left("f"), tree.left("g")) - offset, tree.right("t"), 0.01f)
        assertEquals((tree.top("f") + tree.bottom("g")) / 2, tree.centerY("t"), 0.01f)
    }

    @Test
    fun tallSummaryMakesRoomForItself() {
        val tree = tree()
        tree.summary("s", "c")
        listOf("s1", "s2", "s3").forEach { tree.addNode(it, "s", it.uppercase()) }
        tree.arranged()

        // The children of the summary reach above and below "c", so the siblings next to it move away.
        assertTrue(tree.top("s1") >= tree.bottom("b") + spacing - 0.01f, "the summary's children are clear of b")
        assertTrue(tree.top("d") >= tree.bottom("s3") + spacing - 0.01f, "the summary's children are clear of d")
        assertEquals((tree.top("c") + tree.bottom("c")) / 2, tree.centerY("s"), 0.01f)
    }

    @Test
    fun summaryAboutMoreSiblingsIsFurtherOut() {
        val tree = tree()
        tree.summary("inner", "b")
        tree.summary("outer", "b", "c", "d")
        tree.arranged()

        // It is past the inner summary, and past "e" below "c" too, whichever is further.
        assertEquals(maxOf(tree.right("inner"), tree.right("e")) + offset, tree.left("outer"), 0.01f)
        assertTrue(tree.left("outer") > tree.right("inner"))
    }

    @Test
    fun summariesOnTheLeftStayOnTheMap() {
        val tree = tree()
        tree.summary("t", "f", "g")
        listOf("t1", "t2").forEach { tree.addNode(it, "t", it.uppercase()) }
        tree.arranged()

        val leftMost = tree.nodes.keys.filter { tree.isVisible(it) }.minOf { tree.left(it) }
        assertTrue(leftMost >= 0f, "the map reaches past its left edge, to $leftMost")
    }

    @Test
    fun summaryOnASlantedSideOfTheRootIsPastItsSiblings() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("u", rootId, "U", side = LayoutSide.UPPER_RIGHT)
        tree.addNode("v", rootId, "V", side = LayoutSide.UPPER_RIGHT)
        tree.summary("w", "u", "v")
        tree.arranged()

        assertEquals(LayoutSide.UPPER_RIGHT, tree.sideOf("w"))
        assertEquals(maxOf(tree.right("u"), tree.right("v")) + offset, tree.left("w"), 0.01f)
        assertEquals((tree.top("u") + tree.bottom("v")) / 2, tree.centerY("w"), 0.01f)
    }

    @Test
    fun withoutSummariesTheLayoutIsAsBefore() {
        val plain = tree().arranged()
        val withRemovedSummary = tree().apply {
            summary("s", "b")
            removeNode("s")
        }.arranged()

        plain.nodes.keys.forEach { id ->
            assertEquals(plain.getNode(id).path.centerX.dpVal, withRemovedSummary.getNode(id).path.centerX.dpVal, 0.01f)
            assertEquals(plain.getNode(id).path.centerY.dpVal, withRemovedSummary.getNode(id).path.centerY.dpVal, 0.01f)
        }
    }
}
