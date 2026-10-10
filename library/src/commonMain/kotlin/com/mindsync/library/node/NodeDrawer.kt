package com.mindsync.library.node

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult

interface NodeDrawer {
    fun drawNode(drawScope: DrawScope)
    fun drawText(drawScope: DrawScope, text: TextLayoutResult)
}
