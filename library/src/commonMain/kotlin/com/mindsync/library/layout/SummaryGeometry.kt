package com.mindsync.library.layout

import androidx.compose.ui.unit.dp

/** How far the bracket of a summary is past the furthest out of the nodes it is about. */
internal val SUMMARY_BRACKET_GAP = 8.dp

/** How wide the bracket of a summary is, from its back to its point. */
internal val SUMMARY_BRACKET_WIDTH = 12.dp

/** How far a summary node is past the point of its bracket. */
internal val SUMMARY_SPACE = 24.dp

/** How far a summary node is past the furthest out of the nodes it is about: its bracket is in between. */
internal val SUMMARY_OFFSET = SUMMARY_BRACKET_GAP + SUMMARY_BRACKET_WIDTH + SUMMARY_SPACE
