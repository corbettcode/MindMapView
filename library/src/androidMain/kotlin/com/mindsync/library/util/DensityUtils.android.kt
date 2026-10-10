package com.mindsync.library.util

import android.content.Context
import androidx.compose.ui.unit.Density

fun Dp.toPx(context: Context): Float {
    return toPx(Density(context))
}

fun Px.toDp(context: Context): Float {
    return toDp(Density(context))
}
