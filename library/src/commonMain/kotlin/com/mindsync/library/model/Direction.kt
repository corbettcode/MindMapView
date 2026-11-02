package com.mindsync.library.model

/** A way to go across the screen from a node, as the arrow keys do. */
enum class Direction(val x: Float, val y: Float) {
    LEFT(-1f, 0f),
    RIGHT(1f, 0f),
    UP(0f, -1f),
    DOWN(0f, 1f),
}
