package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mindsync.library.rememberMindMapState
import org.jetbrains.skia.Image
import java.awt.Frame

private const val APP_ICON_RESOURCE = "/icon.png"

private val appIcon: BitmapPainter? by lazy {
    object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE)?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
}

fun main() = application {
    val state = rememberMindMapState()
    val scope = rememberCoroutineScope()
    var frame by remember { mutableStateOf<Frame?>(null) }
    val app = remember { MindMapApp(DesktopPlatform { frame }, state, scope) }

    Window(
        onCloseRequest = { app.close(::exitApplication) }
        , icon = appIcon
        , title = app.title
        , onPreviewKeyEvent = app::onPreviewKeyEvent
        , onKeyEvent = app::onKeyEvent
    ) {
        SideEffect { frame = window }
        app.Content()
    }
}
