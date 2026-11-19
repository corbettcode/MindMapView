package com.corbettcode.mindmapview.library.animator

import com.corbettcode.mindmapview.library.animator.AnimationUtils.START_PROCESS

class FitAnimation(
    private val startScaleFactor: Float
    , private val endScaleFactor: Float
    , private val endX: Float
    , private val endY: Float
    , private val update: (Float, Float, Float) -> Unit
) : AnimationStrategy {
    override suspend fun animate() {
        AnimationUtils.animateProgress { progress ->
            update(
                startScaleFactor + (endScaleFactor - startScaleFactor) * progress
                , endX + (START_PROCESS - endX) * progress
                , endY + (START_PROCESS - endY) * progress
            )
        }
    }
}
