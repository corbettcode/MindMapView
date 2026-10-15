package com.mindsync.library.io

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.RichText

/**
 * Writes a tree as a web page of nested lists, to read or share the mind map as text. A node with children folds
 * and unfolds in a browser, and starts out as it is folded in the tree. Every node is written, those hidden in a
 * folded node too, with its rich text and links as they are, and its note and attributes below it.
 */
object HtmlOutline {
    private const val STYLE = """
body { font-family: sans-serif; line-height: 1.5; margin: 2em; }
ul { list-style: none; padding-left: 1.5em; }
body > ul { padding-left: 0; }
summary { cursor: pointer; }
.note { color: #555; margin: 0.25em 0 0.5em 1.5em; }
.attributes { border-collapse: collapse; margin: 0.25em 0 0.5em 1.5em; font-size: 0.9em; }
.attributes td { border: 1px solid #ccc; padding: 0.1em 0.5em; }
"""

    fun write(tree: Tree<*>): String = buildString {
        val title = RichText.parse(tree.getRootNode().description).text.trim()
        append("<!DOCTYPE html>\n")
        append("<html>\n")
        append("<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<title>${escapeHtml(title)}</title>\n")
        append("<style>$STYLE</style>\n")
        append("</head>\n")
        append("<body>\n")
        append("<ul>\n")
        writeNode(tree, tree.getRootNode())
        append("</ul>\n")
        append("</body>\n")
        append("</html>\n")
    }

    private fun StringBuilder.writeNode(
        tree: Tree<*>
        , node: NodeData<*>
    ) {
        val text = "<span class=\"node\">${node.description}</span>"
        if (node.children.isEmpty()) {
            append("<li>$text")
            writeDetails(tree, node)
            append("</li>\n")
            return
        }
        val open = if (tree.isFolded(node.id)) "" else " open"
        append("<li><details$open><summary>$text</summary>\n")
        writeDetails(tree, node)
        append("<ul>\n")
        node.children.forEach { writeNode(tree, tree.getNode(it)) }
        append("</ul>\n")
        append("</details></li>\n")
    }

    // The note and the attributes of the node, which are written below its text.
    private fun StringBuilder.writeDetails(
        tree: Tree<*>
        , node: NodeData<*>
    ) {
        if (node.note.isNotBlank()) append("<div class=\"note\">${node.note}</div>")
        val attributes = tree.attributesOf(node.id)
        if (attributes.isNotEmpty()) {
            append("<table class=\"attributes\">")
            attributes.forEach { append("<tr><td>${escapeHtml(it.name)}</td><td>${escapeHtml(it.value)}</td></tr>") }
            append("</table>")
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
