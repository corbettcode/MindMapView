package com.mindsync.library.model

import androidx.compose.ui.graphics.Color
import com.mindsync.library.util.Dp

class DrawInfo {
    val circleColor = Color(0xFFE1719F)
    val nodeColors =
        listOf(
            Color(0xFFFFCF96),
            Color(0xFFB9BC81),
            Color(0xFF7AA37D),
            Color(0xFF4B877B),
            Color(0xFF336770),
        )
    val circleTextColor = Color.White
    val rectangleTextColor = Color.Black
    val textSize = Dp(12f)
    val strokeColor = Color(0xFF007EC2)
    val strokeWidth = Dp(5f)
    val padding = Dp(20f)
    val lineColor = Color.Black
    val darkModeLineColor = Color.Gray
    val lineWidth = Dp(5f)
    val boundaryColor = Color.Black.copy(alpha = 50 / 255f)
}
