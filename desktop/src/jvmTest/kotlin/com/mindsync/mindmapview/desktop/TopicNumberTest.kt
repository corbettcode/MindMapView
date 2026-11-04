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
    fun nextNumberIsAfterTheHighestOfTopicsAndSubtopics() {
        assertEquals(8, nextNodeNumber(tree("Topic 2", "Subtopic 7", "Idea"), createdNodeCount = 0))
    }

    @Test
    fun nodesNumberedInMapsMadeBeforeTopicsCount() {
        assertEquals(10, nextNodeNumber(tree("node_9", "Topic 3"), createdNodeCount = 0))
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
