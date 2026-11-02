package com.mindsync.library.model

/** How a node's note is shown: not at all, in a popup while the mouse is over the node, or inside the node. */
enum class NoteDisplay(
    val label: String
) {
    HIDDEN("Hidden")
    , FLYOVER("Flyover")
    , INLINE("Inline")
}
