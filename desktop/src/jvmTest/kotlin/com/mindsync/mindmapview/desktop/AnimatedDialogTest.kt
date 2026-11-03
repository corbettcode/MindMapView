package com.mindsync.mindmapview.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AnimatedDialogTest {
    @Test
    fun dialogStaysWithItsLastValueWhileItCloses() = runComposeUiTest {
        var value by mutableStateOf<String?>("first")
        mainClock.autoAdvance = false
        setContent {
            AnimatedDialog(value = value) { Text(it, modifier = Modifier.dialogAnimation()) }
        }
        mainClock.advanceTimeBy(500)
        onNodeWithText("first").assertExists()

        value = null
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("first").assertExists()

        mainClock.advanceTimeBy(500)
        onNodeWithText("first").assertDoesNotExist()
    }

    @Test
    fun dialogWithoutTheAnimationClosesAtOnce() = runComposeUiTest {
        var isOpen by mutableStateOf(true)
        setContent {
            AnimatedDialog(isOpen = isOpen) { Text("plain") }
        }
        onNodeWithText("plain").assertExists()

        isOpen = false
        waitForIdle()
        onNodeWithText("plain").assertDoesNotExist()
    }
}
