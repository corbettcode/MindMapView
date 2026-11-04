package com.mindsync.library.model

/**
 * What a node is by its level in the map: the [ROOT_TOPIC] on level 0, a [TOPIC] on level 1, below the root, and
 * a [SUBTOPIC] on any level below that. A theme draws each of them in a style of its own.
 */
enum class TopicKind(
    val label: String
) {
    ROOT_TOPIC("Root Topic")
    , TOPIC("Topic")
    , SUBTOPIC("Subtopic");

    companion object {
        /** Returns the kind of a node on [level]. */
        fun ofLevel(
            level: Int
        ): TopicKind = when (level) {
            0 -> ROOT_TOPIC
            1 -> TOPIC
            else -> SUBTOPIC
        }
    }
}
