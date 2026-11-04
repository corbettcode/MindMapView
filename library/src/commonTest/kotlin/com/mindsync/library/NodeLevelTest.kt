package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapThemes
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeLevelTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
        addNode("c", "b", "C")
        addNode("d", getRootNode().id, "D")
    }

    @Test
    fun levelIsHowManyNodesANodeIsBelow() {
        val tree = tree()

        assertEquals(0, tree.levelOf(tree.getRootNode().id))
        assertEquals(1, tree.levelOf("a"))
        assertEquals(2, tree.levelOf("b"))
        assertEquals(3, tree.levelOf("c"))
        assertEquals(1, tree.levelOf("d"))
    }

    @Test
    fun movedNodeIsOnTheLevelOfWhereItGoes() {
        val tree = tree()

        tree.attachNode("b", "d")
        tree.attachNode("a", "c")

        assertEquals(1, tree.levelOf("d"))
        assertEquals(2, tree.levelOf("b"))
        assertEquals(3, tree.levelOf("c"))
        assertEquals(4, tree.levelOf("a"))
    }

    @Test
    fun themeDrawsEveryLevelBelowTheMainTopicsAsSubtopics() {
        val tree = tree().apply { theme = MapThemes.byId("ocean") }

        assertEquals(tree.theme.root, tree.levelStyleOf(tree.getRootNode().id))
        assertEquals(tree.theme.mainTopic, tree.levelStyleOf("a"))
        assertEquals(tree.theme.subtopic, tree.levelStyleOf("b"))
        assertEquals(tree.theme.subtopic, tree.levelStyleOf("c"))
        assertEquals(tree.theme.mainTopic, tree.childLevelStyleOf(tree.getRootNode().id))
        assertEquals(tree.theme.subtopic, tree.childLevelStyleOf("c"))
    }
}
