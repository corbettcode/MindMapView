package com.mindsync.library.model

import androidx.compose.ui.graphics.Color
import com.mindsync.library.util.Dp

class DrawInfo {
    val textSize = Dp(12f)
    val strokeColor = Color(0xFF007EC2)
    val strokeWidth = Dp(5f)
    val padding = Dp(20f)
    val lineColor = Color.Black
    val darkModeLineColor = Color.Gray
    val lineWidth = Dp(5f)
    val boundaryColor = Color.Black.copy(alpha = 50 / 255f)
}
