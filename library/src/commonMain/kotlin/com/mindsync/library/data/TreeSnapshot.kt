package com.mindsync.library.data

import com.mindsync.library.model.LayoutSide

/**
 * All there is of a [Tree] at one time, to put it back as it was.
 *
 * Represents a snapshot of a tree's current state, holding data related to its nodes, layout, attributes,
 * and visual representations (e.g., arrow links). This snapshot is utilized for operations involving
 * comparison or recreation of the tree's state.
 *
 * @property nodes The map of node IDs to their respective data, detailing the content and structure of the tree.
 * @property rootChildSides The map of node IDs to their respective child placement sides, indicating where
 * children of each node are laid out.
 * @property nodeAttributes The map of node IDs to their attributes, organizing metadata associated
 * with individual nodes.
 * @property foldedIds The set of node IDs corresponding to nodes that are currently folded.
 * @property arrowLinks The list of arrow links defining visual connections between nodes, such as directed
 * annotations or relationships.
 * @property isRootPlaced Flag indicating whether the root node has been placed within the layout.
 * @property isRootCentered Flag indicating whether the root node is vertically and horizontally centered in the layout.
 * @property isRootDragged Flag indicating whether the root node is currently being dragged.
 */
internal class TreeSnapshot(
    val nodes: Map<String, NodeData<*>>
    , val rootChildSides: Map<String, LayoutSide>
    , val nodeAttributes: Map<String, List<NodeAttribute>>
    , val foldedIds: Set<String>
    , val arrowLinks: List<ArrowLink>
    , val isRootPlaced: Boolean
    , val isRootCentered: Boolean
    , val isRootDragged: Boolean
) {
    /**
     * Whether the two hold the same map. Where the nodes are is left out, as the tree is arranged again
     * after a change and that is no change of its own.
     */
    fun hasSameContentAs(other: TreeSnapshot): Boolean =
        nodes.mapValues { it.value.content() } == other.nodes.mapValues { it.value.content() }
            && rootChildSides == other.rootChildSides
            && nodeAttributes == other.nodeAttributes
            && foldedIds == other.foldedIds
            && arrowLinks == other.arrowLinks

    private fun NodeData<*>.content(): List<Any?> =
        listOf(this::class, id, parentId, children, description, note, color, textColor)
}
