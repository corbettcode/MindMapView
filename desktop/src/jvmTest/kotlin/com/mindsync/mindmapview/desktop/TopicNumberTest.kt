package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.model.TopicKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicNumberTest {
    private fun tree(vararg descriptions: String): Tree<Any> = Tree<Any>().apply {
        descriptions.forEachIndexed { i, description -> addNode("n$i", getRootNode().id, "<p>$description</p>") }
    }

    @Test
    fun topicsAndSubtopicsAreEachCountedOnTheirOwn() {
        val tree = tree("Topic 2", "Topic 7").apply {
            addNode("s0", "n0", "<p>Subtopic 3</p>")
        }

        assertEquals(8, nextNodeNumber(tree, TopicKind.TOPIC, createdNodeCount = 0))
        assertEquals(4, nextNodeNumber(tree, TopicKind.SUBTOPIC, createdNodeCount = 0))
    }

    @Test
    fun nodesOfAKindAreCountedWhateverTheirText() {
        val tree = tree("Idea", "Plan", "Goal")

        assertEquals(4, nextNodeNumber(tree, TopicKind.TOPIC, createdNodeCount = 0))
        assertEquals(1, nextNodeNumber(tree, TopicKind.SUBTOPIC, createdNodeCount = 0))
    }

    @Test
    fun numberCountsForTheKindItsTextNames() {
        // A subtopic moved up to be a topic keeps the number of a subtopic.
        val tree = tree("Subtopic 9")

        assertEquals(2, nextNodeNumber(tree, TopicKind.TOPIC, createdNodeCount = 0))
        assertEquals(10, nextNodeNumber(tree, TopicKind.SUBTOPIC, createdNodeCount = 0))
    }

    @Test
    fun nodesNumberedInMapsMadeBeforeTopicsCountForTheirLevel() {
        val tree = tree("node_9").apply { addNode("s0", "n0", "<p>node_4</p>") }

        assertEquals(10, nextNodeNumber(tree, TopicKind.TOPIC, createdNodeCount = 0))
        assertEquals(5, nextNodeNumber(tree, TopicKind.SUBTOPIC, createdNodeCount = 0))
    }

    @Test
    fun nodeThatWasAddedAndRemovedIsNotNumberedAgain() {
        assertEquals(6, nextNodeNumber(tree(), TopicKind.SUBTOPIC, createdNodeCount = 5))
    }

    @Test
    fun nodeIsNamedForItsLevel() {
        val tree = Tree<Any>().apply {
            addNode("a", getRootNode().id, "A")
            addNode("b", "a", "B")
            addNode("c", "b", "C")
        }

        assertEquals(TopicKind.ROOT_TOPIC, tree.topicKindOf(tree.getRootNode().id))
        assertEquals(TopicKind.TOPIC, tree.topicKindOf("a"))
        assertEquals(TopicKind.SUBTOPIC, tree.topicKindOf("b"))
        assertEquals(TopicKind.SUBTOPIC, tree.topicKindOf("c"))
        assertEquals(TopicKind.TOPIC, tree.childTopicKindOf(tree.getRootNode().id))
        assertEquals(TopicKind.SUBTOPIC, tree.childTopicKindOf("a"))
        assertEquals("Root Topic", TopicKind.ROOT_TOPIC.label)
    }
}
