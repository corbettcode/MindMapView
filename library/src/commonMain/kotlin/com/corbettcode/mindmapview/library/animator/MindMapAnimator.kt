package com.corbettcode.mindmapview.library.animator

class MindMapAnimator {
    private var animationStrategy: AnimationStrategy? = null

    fun setAnimationStrategy(strategy: AnimationStrategy) {
        animationStrategy = strategy
    }

    suspend fun executeAnimation() {
        animationStrategy?.animate()
    }
}
