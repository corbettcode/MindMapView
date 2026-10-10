package com.mindsync.library.data

import android.content.Context
import com.mindsync.library.util.Dp

/** Creates a tree whose root node is placed at the center of the screen. */
fun <T> Tree(context: Context): Tree<T> {
    val displayMetrics = context.resources.displayMetrics
    val centerX = displayMetrics.widthPixels / displayMetrics.density / 2
    val centerY = displayMetrics.heightPixels / displayMetrics.density / 2
    return Tree(Dp(centerX), Dp(centerY))
}
