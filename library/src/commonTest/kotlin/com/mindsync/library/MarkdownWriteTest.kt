package com.mindsync.library

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.Markdown
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals

/** The text, note and attributes of each node of [tree], a line for each node, indented by its depth. */
internal fun markdownOutline(
    tree: Tree<*>
    , node: NodeData<*> = tree.getRootNode()
    , depth: Int = 0
): String {
    val text = RichText.parse(node.description).text.trim()
    val note = RichText.lines(node.note).joinToString(" / ") { it.text.trim() }
    val attributes = tree.attributesOf(node.id).joinToString(", ") { "${it.name}=${it.value}" }
    return "  ".repeat(depth) + text +
        (if (note.isEmpty()) "" else " [note: $note]") +
        (if (attributes.isEmpty()) "" else " {$attributes}") + "\n" +
        node.children.joinToString("") { markdownOutline(tree, tree.getNode(it), depth + 1) }
}

class MarkdownWriteTest {
    private fun tree(rootText: String = "Root"): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        setRootNode(getRootNode().copy(description = rootText))
    }

    private fun roundTrip(tree: Tree<Any>): Tree<Any> = Markdown.read(Markdown.write(tree), "t")

    @Test
    fun rootOnlyMapIsOneHeading() {
        val tree = tree()

        assertEquals("# Root\n", Markdown.write(tree))
        assertEquals("Root\n", markdownOutline(roundTrip(tree)))
    }

    @Test
    fun deepTreeRoundTrips() {
        val tree = tree().apply {
            fun addLevel(
                parentId: String
                , depth: Int
            ) {
                if (depth > 6) return
                listOf("a", "b").forEach { name ->
                    val id = "$parentId-$name"
                    addNode(id, parentId, "level $depth $name")
                    addLevel(id, depth + 1)
                }
            }
            addLevel(getRootNode().id, 1)
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun notesUnderHeadingsAndListItemsRoundTrip() {
        val tree = tree().apply {
            addNode("h", getRootNode().id, "Heading")
            addNode("h2", "h", "Sub")
            addNode("h3", "h2", "List 1")
            addNode("h4", "h3", "List 2")
            addNode("h5", "h4", "List 3")
            updateNodeNote("h", "<p>first</p><p>second</p>")
            updateNodeNote("h4", "<p>deep one</p><p>deep two</p>")
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun noteOfTheRootRoundTrips() {
        val tree = tree().apply {
            updateNodeNote(getRootNode().id, "About the map")
            addNode("a", getRootNode().id, "A")
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun leafAttributesRoundTripAndComeFirst() {
        val tree = tree().apply {
            addNode("p", getRootNode().id, "P")
            addNode("a", "p", "A")
            addNode("b", "p", "B")
            addNode("b1", "b", "B1")
            addNode("c", "p", "C")
            setAttributes("a", listOf(NodeAttribute("Owner", "Ann")))
            setAttributes("c", listOf(NodeAttribute("Owner", "Bo"), NodeAttribute("Due", "Fri")))
        }

        assertEquals(
            "Root\n  P\n    A {Owner=Ann}\n    C {Owner=Bo, Due=Fri}\n    B\n      B1\n"
            , markdownOutline(roundTrip(tree))
        )
    }

    @Test
    fun leafAttributesUnderAListItemRoundTrip() {
        val tree = tree().apply {
            addNode("h", getRootNode().id, "H")
            addNode("h2", "h", "H2")
            addNode("l", "h2", "List")
            addNode("l1", "l", "Leaf")
            addNode("l2", "l", "Other")
            addNode("l3", "l2", "Below")
            setAttributes("l1", listOf(NodeAttribute("Size", "3")))
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun repeatedAttributeNamesRoundTrip() {
        val tree = tree().apply {
            addNode("a", getRootNode().id, "A")
            setAttributes("a", listOf(NodeAttribute("Tag", "x"), NodeAttribute("Tag", "y")))
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun attributesOfANodeWithChildrenAreDropped() {
        val tree = tree().apply {
            addNode("a", getRootNode().id, "A")
            addNode("a1", "a", "A1")
            setAttributes("a", listOf(NodeAttribute("Owner", "Ann")))
        }

        assertEquals("Root\n  A\n    A1\n", markdownOutline(roundTrip(tree)))
    }

    @Test
    fun richTextAndMarkupRoundTripInHeadingsListsAndCells() {
        val texts = listOf("2*3*4", "snake_case_name", "# not", "- not", "1. not", "a | b", "Tom & Jerry <3", "[x](y)")
        val tree = tree("<b>Bold</b> and <i>italic</i>").apply {
            addNode("h", getRootNode().id, "H")
            addNode("h2", "h", "H2")
            texts.forEachIndexed { index, text ->
                addNode("d$index", getRootNode().id, RichText.fromPlainText(text))
                addNode("l$index", "h2", RichText.fromPlainText(text))
                addNode("l$index-child", "l$index", "child")
                addNode("t$index", "h", "Row $index")
                setAttributes("t$index", listOf(NodeAttribute("Value", text)))
            }
        }

        val back = roundTrip(tree)

        assertEquals("<b>Bold</b> and <i>italic</i>", back.getRootNode().description)
        assertEquals(markdownOutline(tree).lines().sorted(), markdownOutline(back).lines().sorted())
    }

    @Test
    fun emptyNodeAfterAHeadingsNoteRoundTrips() {
        val tree = tree().apply {
            addNode("h", getRootNode().id, "H")
            addNode("h2", "h", "H2")
            updateNodeNote("h2", "a note")
            addNode("empty", "h2", "")
            addNode("f", "h2", "F")
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }

    @Test
    fun emptyTextRoundTrips() {
        val tree = tree().apply {
            addNode("a", getRootNode().id, "")
            addNode("b", "a", "B")
        }

        assertEquals(markdownOutline(tree), markdownOutline(roundTrip(tree)))
    }
}
