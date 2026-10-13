package com.mindsync.library.data

/**
 * A name and a value given to a node, as FreeMind's attributes are. The attributes of a node are shown
 * as a table below its text. A node may have more than one attribute of a name.
 */
data class NodeAttribute(
    val name: String
    , val value: String
)
