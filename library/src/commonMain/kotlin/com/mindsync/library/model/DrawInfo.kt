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

    // The lines from the root each take the next of these colors, starting over when they run out.
    val rootLineColors = listOf(
        Color(0xFF5B9A63),
        Color(0xFF5A74B8),
        Color(0xFFC98A4E),
        Color(0xFF8A5FA3),
        Color(0xFF5FA9BC),
        Color(0xFFB868AE),
        Color(0xFFA39A3A),
        Color(0xFF4F8F87),
        Color(0xFF8C6B45),
    )
    val linkColor = Color(0xFFFFD600)
    val linkWidth = Dp(2.5f)
    val noteMarkerColor = Color(0xFFFFD54F)
    val noteMarkerLineColor = Color(0xFF5C4A00)
    val boundaryColor = Color.Black.copy(alpha = 50 / 255f)
}
