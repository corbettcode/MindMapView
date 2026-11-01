package com.mindsync.library

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.Tree
import com.mindsync.library.io.Opml
import com.mindsync.library.io.XmlElement
import com.mindsync.library.io.XmlParser
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpmlTest {
    private fun tree(rootText: String = "Rocks"): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        setRootNode(getRootNode().copy(description = rootText))
    }

    private fun opml(tree: Tree<Any>): XmlElement = XmlParser(Opml.write(tree)).parse().single { it.name == "opml" }

    private fun XmlElement.child(name: String): XmlElement = children.single { it.name == name }

    private fun XmlElement.outlines(): List<XmlElement> = children.filter { it.name == "outline" }

    private fun rootOutline(tree: Tree<Any>): XmlElement = opml(tree).child("body").outlines().single()

    @Test
    fun rootOnlyMapIsOneOutline() {
        val document = opml(tree())

        assertEquals("2.0", document.attributes["version"])
        assertEquals("Rocks", document.child("head").child("title").text)
        val outline = document.child("body").outlines().single()
        assertEquals("Rocks", outline.attributes["text"])
        assertTrue(outline.outlines().isEmpty())
    }

    @Test
    fun nodesNestAsTheTree() {
        val tree = tree().apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("a1", "a", "A1")
            addNode("b", rootId, "B")
        }

        val root = rootOutline(tree)

        assertEquals(listOf("A", "B"), root.outlines().map { it.attributes["text"] })
        assertEquals(listOf("A1"), root.outlines().first().outlines().map { it.attributes["text"] })
    }

    @Test
    fun foldedBranchesAreWrittenToo() {
        val tree = tree().apply {
            addNode("a", getRootNode().id, "A")
            addNode("a1", "a", "A1")
            setFolded("a", true)
        }

        val a = rootOutline(tree).outlines().single()

        assertEquals(listOf("A1"), a.outlines().map { it.attributes["text"] })
    }

    @Test
    fun textIsPlainAndEscaped() {
        val tree = tree("<b>Tom &amp; \"Jerry\"</b> &lt;3")

        val written = Opml.write(tree)

        assertFalse("<b>" in written)
        assertEquals("Tom & \"Jerry\" <3", rootOutline(tree).attributes["text"])
    }

    @Test
    fun noteIsPlainTextInNote() {
        val tree = tree().apply {
            updateNodeNote(getRootNode().id, "<p>one</p><p>two</p>")
            addNode("a", getRootNode().id, "A")
        }

        val root = rootOutline(tree)

        assertEquals("one\ntwo", root.attributes["_note"])
        assertNull(root.outlines().single().attributes["_note"])
    }

    @Test
    fun attributesAreOutlineAttributes() {
        val tree = tree().apply {
            setAttributes(getRootNode().id, listOf(NodeAttribute("Owner", "Ann"), NodeAttribute("Due date", "Friday")))
        }

        val root = rootOutline(tree)

        assertEquals("Ann", root.attributes["Owner"])
        assertEquals("Friday", root.attributes["Due_date"])
    }

    @Test
    fun attributeNamesThatClashOrAreInvalidAreMadeValid() {
        val tree = tree().apply {
            setAttributes(
                getRootNode().id
                , listOf(
                    NodeAttribute("text", "1")
                    , NodeAttribute("_note", "2")
                    , NodeAttribute("1st", "3")
                    , NodeAttribute("a:b", "4")
                    , NodeAttribute("Owner", "Ann")
                    , NodeAttribute("Owner", "Bo")
                )
            )
        }

        val attributes = rootOutline(tree).attributes

        assertEquals("Rocks", attributes["text"])
        assertEquals("1", attributes["attr_text"])
        assertEquals("2", attributes["attr__note"])
        assertEquals("3", attributes["_1st"])
        assertEquals("4", attributes["a_b"])
        assertEquals("Ann", attributes["Owner"])
        assertEquals("Bo", attributes["Owner_2"])
    }
}
