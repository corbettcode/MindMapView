package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.window.ComposeViewport
import com.mindsync.library.rememberMindMapState

// The recent mind maps are loaded first, as the app reads them once to start with.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    jsLoadRecentMaps().then {
        ComposeViewport(viewportContainerId = "app") {
            WebApp()
        }
        null
    }
}

@Composable
private fun WebApp() {
    val state = rememberMindMapState()
    val scope = rememberCoroutineScope()
    val app = remember { MindMapApp(WebPlatform, state, scope) }
    val focusRequester = remember { FocusRequester() }
    val takeFocus: () -> Unit = {
        jsFocusCanvas()
        focusRequester.requestFocus()
    }

    LaunchedEffect(app.title) { jsSetTitle(app.title) }
    LaunchedEffect(state.isModified) { jsSetLeaveWarning(state.isModified) }
    // A page has no window to see the keys wherever the focus is, so the app takes the focus whenever it
    // could be lost: when a dialog closes and when the mind map is pressed.
    // A dialog that closes gives up the focus once it is gone, a frame later.
    LaunchedEffect(app.isDialogOpen) {
        if (app.isDialogOpen) return@LaunchedEffect
        withFrameNanos {}
        takeFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent(app::onPreviewKeyEvent)
            .onKeyEvent(app::onKeyEvent)
            .focusRequester(focusRequester)
            .focusable()
    ) {
        app.Content(onMapPress = takeFocus)
    }
}
