package com.mindsync.library.io

import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.util.IdGenerator
import com.mindsync.library.util.RichText

/**
 * An item of a hierarchy of names, such as a folder and the folders in it, to make a mind map of with
 * [Outline.toTree]. Its [title] is plain text, and its [note] is HTML.
 */
data class OutlineItem(
    val title: String
    , val children: List<OutlineItem> = emptyList()
    , val note: String = ""
    , val isFolded: Boolean = false
)

/** Makes a mind map of a hierarchy of names. */
object Outline {
    /**
     * Returns the mind map of [root]: a node for each item, below the node of the item it is in. A folded item
     * is a folded node, unless it is the root or has no children.
     */
    fun toTree(root: OutlineItem): Tree<Any> {
        val tree = Tree<Any>()
        val rootNode = tree.getRootNode()
        val children = root.children.map { addNode(tree, it, rootNode.id) }
        tree.setRootNode(
            rootNode.copy(
                description = RichText.fromPlainText(root.title)
                , children = children
                , note = root.note
            )
        )
        return tree
    }

    private fun addNode(
        tree: Tree<Any>
        , item: OutlineItem
        , parentId: String
    ): String {
        val id = IdGenerator.makeRandomNodeId()
        val children = item.children.map { addNode(tree, it, id) }
        val shape = NodeShape.RECTANGLE
        tree.setNode(
            id
            , unplacedNode(
                shape
                , id
                , parentId
                , RichText.fromPlainText(item.title)
                , children
                , shape.defaultColor
                , shape.defaultTextColor
                , item.note
            )
        )
        tree.setFolded(id, item.isFolded)
        return id
    }
}
