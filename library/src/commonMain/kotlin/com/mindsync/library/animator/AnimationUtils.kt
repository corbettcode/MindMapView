package com.mindsync.library.animator

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.cos

object AnimationUtils {
    const val ANIMATION_DURATION = 300L
    const val START_PROCESS = 0f
    const val END_PROCESS = 1f

    val AccelerateDecelerateEasing = Easing { fraction ->
        (cos((fraction + 1) * PI) / 2.0 + 0.5).toFloat()
    }

    suspend fun animateProgress(
        duration: Long = ANIMATION_DURATION,
        easing: Easing = AccelerateDecelerateEasing,
        onUpdate: (Float) -> Unit
    ) = animate(
        initialValue = START_PROCESS,
        targetValue = END_PROCESS,
        animationSpec = tween(durationMillis = duration.toInt(), easing = easing),
    ) { value, _ ->
        onUpdate(value)
    }
}
