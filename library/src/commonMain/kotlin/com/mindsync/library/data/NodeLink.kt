package com.mindsync.library.data

/**
 * A graphical link: an arrow from one node to another that is no part of the structure of the tree.
 */
data class NodeLink(
    val fromId: String
    , val toId: String
)
