package com.mindsync.library.data

/** All there is of a [Tree] at one time, to put it back as it was. */
internal class TreeSnapshot(
    val nodes: Map<String, NodeData<*>>
    , val leftRootChildIds: Set<String>
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
            && leftRootChildIds == other.leftRootChildIds
            && nodeAttributes == other.nodeAttributes
            && foldedIds == other.foldedIds
            && arrowLinks == other.arrowLinks

    private fun NodeData<*>.content(): List<Any?> =
        listOf(this::class, id, parentId, children, description, note, color, textColor)
}
