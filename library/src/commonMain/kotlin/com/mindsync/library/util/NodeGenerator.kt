package com.mindsync.library.util

import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.CirclePath
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalPath
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.RectanglePath
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import kotlin.reflect.KClass
import kotlin.reflect.cast


object NodeGenerator {
    fun <N : NodeData<*>> makeNode(
        nodeClass: KClass<N>,
        description: String,
        parentId: String
    ): N {
        val id = IdGenerator.makeRandomNodeId()

        return when (nodeClass) {
            CircleNodeData::class -> nodeClass.cast(
                CircleNodeData(
                    id = id,
                    parentId = parentId,
                    path = CirclePath(Dp(0f), Dp(0f), Dp(50f)),  // 예시로 CirclePath 설정
                    description = description,
                    children = listOf()
                )
            )
            RectangleNodeData::class -> nodeClass.cast(
                RectangleNodeData(
                    id = id,
                    parentId = parentId,
                    path = RectanglePath(Dp(0f), Dp(0f), Dp(100f), Dp(50f)),  // 예시로 RectanglePath 설정
                    description = description,
                    children = listOf()
                )
            )
            StadiumNodeData::class -> nodeClass.cast(
                StadiumNodeData(
                    id = id,
                    parentId = parentId,
                    path = StadiumPath(Dp(0f), Dp(0f), Dp(50f), Dp(25f)),
                    description = description,
                    children = listOf()
                )
            )
            OvalNodeData::class -> nodeClass.cast(
                OvalNodeData(
                    id = id,
                    parentId = parentId,
                    path = OvalPath(Dp(0f), Dp(0f), Dp(50f), Dp(25f)),
                    description = description,
                    children = listOf()
                )
            )
            SquareNodeData::class -> nodeClass.cast(
                SquareNodeData(
                    id = id,
                    parentId = parentId,
                    path = RectanglePath(Dp(0f), Dp(0f), Dp(50f), Dp(50f)),
                    description = description,
                    children = listOf()
                )
            )
            LineNodeData::class -> nodeClass.cast(
                LineNodeData(
                    id = id,
                    parentId = parentId,
                    path = RectanglePath(Dp(0f), Dp(0f), Dp(50f), Dp(50f)),
                    description = description,
                    children = listOf()
                )
            )
            else -> throw IllegalArgumentException("Unsupported Node type")
        }
    }
}
