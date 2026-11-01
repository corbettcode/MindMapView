package com.mindsync.library

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.Markdown
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.util.RichText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFeatureTourTest {
    // The outline of the tree as Markdown gives it back: the leaves with attributes first among their siblings,
    // without their notes, and no attributes on the nodes with children.
    private fun expectedOutline(
        tree: Tree<*>
        , node: NodeData<*> = tree.getRootNode()
        , depth: Int = 0
    ): String {
        val isRow = isRow(tree, node)
        // A list in the text of a node is one line in Markdown, without its bullets.
        val text = RichText.lines(node.description).joinToString(" ") { it.text.trim() }
        val note = if (isRow) "" else RichText.lines(node.note).joinToString(" / ") { it.text.trim() }
        val attributes =
            if (node.children.isNotEmpty()) ""
            else tree.attributesOf(node.id)
                .filter { it.value.isNotBlank() }
                .joinToString(", ") { "${it.name}=${it.value}" }
        val (rows, others) = node.children.map { tree.getNode(it) }.partition { isRow(tree, it) }
        return "  ".repeat(depth) + text +
            (if (note.isEmpty()) "" else " [note: $note]") +
            (if (attributes.isEmpty()) "" else " {$attributes}") + "\n" +
            (rows + others).joinToString("") { expectedOutline(tree, it, depth + 1) }
    }

    private fun isRow(
        tree: Tree<*>
        , node: NodeData<*>
    ): Boolean = node.children.isEmpty() && tree.attributesOf(node.id).any { it.value.isNotBlank() }

    @Test
    fun featureTourRoundTrips() {
        val tree = MindMapViewFile.read(File("../desktop/feature_tour.mmv").readText())

        val back = Markdown.read(Markdown.write(tree), "t")

        assertEquals(expectedOutline(tree), markdownOutline(back))
    }
}
