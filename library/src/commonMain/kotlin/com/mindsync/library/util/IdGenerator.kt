package com.mindsync.library.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object IdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    fun makeRandomNodeId() = Uuid.random().toString()
}
