package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SiblingInsertTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
        addNode("c", "a", "C")
    }

    private fun Tree<Any>.childrenOf(id: String) = getNode(id).children

    @Test
    fun nodeWithoutAnIndexGoesAfterTheLastChild() {
        val tree = tree()

        tree.addNode("d", "a", "D")

        assertEquals(listOf("b", "c", "d"), tree.childrenOf("a"))
    }

    @Test
    fun nodeGoesAtItsIndexAmongTheChildren() {
        val first = tree().apply { addNode("d", "a", "D", index = 0) }
        val middle = tree().apply { addNode("d", "a", "D", index = 1) }
        val last = tree().apply { addNode("d", "a", "D", index = 2) }

        assertEquals(listOf("d", "b", "c"), first.childrenOf("a"))
        assertEquals(listOf("b", "d", "c"), middle.childrenOf("a"))
        assertEquals(listOf("b", "c", "d"), last.childrenOf("a"))
    }

    @Test
    fun indexPastEitherEndPutsTheNodeAtThatEnd() {
        val before = tree().apply { addNode("d", "a", "D", index = -3) }
        val after = tree().apply { addNode("d", "a", "D", index = 9) }

        assertEquals(listOf("d", "b", "c"), before.childrenOf("a"))
        assertEquals(listOf("b", "c", "d"), after.childrenOf("a"))
    }

    @Test
    fun childOfTheRootAtAnIndexKeepsItsSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("e", rootId, "E", side = LayoutSide.UPPER_LEFT)

        tree.addNode("f", rootId, "F", side = LayoutSide.UPPER_LEFT, index = 1)

        assertEquals(listOf("a", "f", "e"), tree.childrenOf(rootId))
        assertEquals(LayoutSide.UPPER_LEFT, tree.sideOf("f"))
        assertEquals(LayoutSide.UPPER_LEFT, tree.sideOf("e"))
        assertEquals(LayoutSide.RIGHT, tree.sideOf("a"))
    }
}
