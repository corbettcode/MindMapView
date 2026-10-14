package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.OutlineNode
import com.mindsync.library.data.Tree
import com.mindsync.library.util.RichText

private val WHITESPACE = Regex("\\s+")
private const val TAB_WIDTH = 4
// A list in a text that is pasted may be bulleted; a bullet is no part of the text of a node.
private val BULLET = Regex("^[-*\u2022]\\s+")

/**
 * Returns the node with the id [id] and the nodes below it as plain text, for other programs to paste: a
 * line for each node, without its markup, indented with a tab for each level below the node.
 */
internal fun nodeOutline(
    tree: Tree<*>
    , id: String
): String = buildList {
    tree.doPreorderTraversal(tree.getNode(id)) { node, depth ->
        add("\t".repeat(depth) + RichText.parse(node.description).text.replace(WHITESPACE, " ").trim())
    }
}.joinToString("\n")

/**
 * Returns the nodes an outline of text, as [nodeOutline] makes, gives: a node for each line that is not
 * blank, below the line before it that is indented less. A tab is as deep as [TAB_WIDTH] spaces.
 */
internal fun parseOutline(text: String): List<OutlineNode> {
    class Line(val indent: Int, val text: String)

    val lines = text.lines().filter { it.isNotBlank() }.map { line ->
        val indent = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') TAB_WIDTH else 1 }
        Line(indent, line.trim().replace(BULLET, ""))
    }
    var index = 0
    fun childrenOf(indent: Int): List<OutlineNode> = buildList {
        while (index < lines.size && lines[index].indent > indent) {
            val line = lines[index++]
            add(OutlineNode(RichText.fromPlainText(line.text), childrenOf(line.indent)))
        }
    }
    return childrenOf(-1)
}
