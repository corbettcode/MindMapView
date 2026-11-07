package com.mindsync.library.io

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.RichText

/**
 * Writes a tree as an OPML 2.0 outline, which outliners such as Workflowy, OmniOutliner and Logseq read. The root
 * is the one outline at the top, with every node below it, those hidden in a folded node too. The text of a node is
 * written without its formatting, its note as plain text in `_note`, and its attributes as attributes of its
 * outline, with names made valid in XML and kept apart from the names OPML uses. An outline has no arrow links nor
 * boundaries, so they are left out.
 */
object Opml {
    const val EXTENSION = "opml"

    private val RESERVED_NAMES = setOf("text", "_note", "type")
    private val INVALID_NAME_CHARACTER = Regex("""[^A-Za-z0-9_.-]""")

    fun write(tree: Tree<*>): String = buildString {
        val root = tree.getRootNode()
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n")
        append("<head>\n")
        append("<title>${escape(plainText(root.description))}</title>\n")
        append("</head>\n")
        append("<body>\n")
        writeOutline(tree, root, depth = 0)
        append("</body>\n")
        append("</opml>\n")
    }

    private fun StringBuilder.writeOutline(
        tree: Tree<*>
        , node: NodeData<*>
        , depth: Int
    ) {
        append("  ".repeat(depth))
        append("<outline")
        attributesOf(tree, node).forEach { (name, value) -> append(" $name=\"${escape(value)}\"") }
        if (node.children.isEmpty()) {
            append("/>\n")
            return
        }
        append(">\n")
        node.children.forEach { writeOutline(tree, tree.getNode(it), depth + 1) }
        append("  ".repeat(depth))
        append("</outline>\n")
    }

    private fun attributesOf(
        tree: Tree<*>
        , node: NodeData<*>
    ): List<Pair<String, String>> {
        val attributes = mutableListOf("text" to plainText(node.description))
        val note = RichText
            .lines(node.note)
            .joinToString("\n") { it.text.trim() }
        if (note.isNotEmpty()) attributes.add("_note" to note)
        val usedNames = attributes.map { it.first }.toMutableSet()
        tree.attributesOf(node.id).forEach { attribute ->
            val name = uniqueName(validName(attribute.name), usedNames)
            usedNames.add(name)
            attributes.add(name to attribute.value)
        }
        return attributes
    }

    private fun validName(name: String): String {
        val valid = name.replace(INVALID_NAME_CHARACTER, "_")
        return if (valid.firstOrNull()?.let { it.isLetter() || it == '_' } == true) valid else "_$valid"
    }

    private fun uniqueName(
        name: String
        , usedNames: Set<String>
    ): String {
        val prefixed = if (name in RESERVED_NAMES) "attr_$name" else name
        if (prefixed !in usedNames) return prefixed
        return generateSequence(2) { it + 1 }.map { "${prefixed}_$it" }.first { it !in usedNames }
    }

    private fun plainText(html: String): String = if (html.isEmpty()) "" else RichText.parse(html).text.trim()

    // A line break in an attribute would be read back as a space, so it is written as a character reference.
    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "&#10;")
}
