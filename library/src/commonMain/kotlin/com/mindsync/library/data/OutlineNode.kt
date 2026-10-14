package com.mindsync.library.data

/**
 * A node to add with the nodes below it, as an outline of text gives them: its [description], as rich
 * text, and its [children].
 */
data class OutlineNode(
    val description: String
    , val children: List<OutlineNode> = emptyList()
)
