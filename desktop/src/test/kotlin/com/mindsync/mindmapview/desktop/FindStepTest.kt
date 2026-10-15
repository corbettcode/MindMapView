package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindStepTest {
    // The root with "a", "b" and "c" below it, and "a1" below "a": in the order of the tree a, a1, b, c.
    private val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "a")
        addNode("a1", "a", "a1")
        addNode("b", rootId, "b")
        addNode("c", rootId, "c")
    }

    private fun next(matchIds: List<String>, currentId: String?) =
        nextMatch(tree, matchIds, currentId, FindDirection.NEXT)

    private fun previous(matchIds: List<String>, currentId: String?) =
        nextMatch(tree, matchIds, currentId, FindDirection.PREVIOUS)

    @Test
    fun stepFromNoMatchGoesToTheFirstOrTheLastMatch() {
        assertEquals("a1", next(listOf("a1", "c"), null))
        assertEquals("c", previous(listOf("a1", "c"), null))
    }

    @Test
    fun stepGoesToTheMatchAfterOrBeforeTheCurrentOne() {
        assertEquals("b", next(listOf("a1", "b", "c"), "a1"))
        assertEquals("a1", previous(listOf("a1", "b", "c"), "b"))
    }

    @Test
    fun stepWrapsAroundAtTheEnds() {
        assertEquals("a1", next(listOf("a1", "b", "c"), "c"))
        assertEquals("c", previous(listOf("a1", "b", "c"), "a1"))
    }

    @Test
    fun thereIsNoStepWithoutMatches() {
        assertNull(next(emptyList(), "a"))
        assertNull(previous(emptyList(), null))
    }

    // The node may have been edited since it was found, so that it no longer holds the text.
    @Test
    fun stepFromANodeThatIsNoLongerAMatchGoesOnFromWhereItIs() {
        assertEquals("b", next(listOf("a", "b", "c"), "a1"))
        assertEquals("a", previous(listOf("a", "b", "c"), "a1"))
    }

    @Test
    fun stepFromANodeThatIsGoneGoesToTheFirstOrTheLastMatch() {
        assertEquals("a", next(listOf("a", "c"), "removed"))
        assertEquals("c", previous(listOf("a", "c"), "removed"))
    }

    @Test
    fun countTellsWhichMatchTheCurrentOneIs() {
        assertEquals("2 of 3", findCountText(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun countTellsHowManyMatchesThereAreWhenTheCurrentNodeIsNotOne() {
        assertEquals("3 matches", findCountText(listOf("a", "b", "c"), "a1"))
        assertEquals("1 match", findCountText(listOf("a"), null))
    }

    @Test
    fun countTellsThereAreNoMatches() {
        assertEquals("No matches", findCountText(emptyList(), "a"))
    }
}
