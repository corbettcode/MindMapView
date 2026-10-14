package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.util.RichText

private val WHITESPACE = Regex("\\s+")

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
