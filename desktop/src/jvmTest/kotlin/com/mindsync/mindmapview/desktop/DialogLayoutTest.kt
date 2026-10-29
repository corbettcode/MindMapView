package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mindsync.mindmapview.desktop.component.DialogLayout
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DialogLayoutTest {
    private val gap = 16.dp
    private val noteMin = 80.dp

    private fun dialogContent(
        height: Dp
        , settingsHeight: Dp
        , onMinHeight: (Dp) -> Unit = {}
    ): @androidx.compose.runtime.Composable () -> Unit = {
        Box(modifier = Modifier.size(400.dp, height)) {
            DialogLayout(
                noteMinHeight = noteMin
                , gap = gap
                , contentPadding = PaddingValues(0.dp)
                , onContentMinHeightChange = onMinHeight
                , settings = { Spacer(modifier = Modifier.fillMaxWidth().height(settingsHeight)) }
                , note = { modifier -> Box(modifier = modifier.testTag("note")) }
                , buttons = { Text("OK", modifier = Modifier.height(40.dp)) }
            )
        }
    }

    // In a dialog with room to spare, the note takes what the rest leaves.
    @Test
    fun noteTakesTheRoomLeft() = runComposeUiTest {
        setContent(dialogContent(height = 600.dp, settingsHeight = 300.dp))
        // 600 less the settings, a gap above the note, and the buttons with their gap above them.
        onNodeWithTag("note").assertHeightIsEqualTo(600.dp - 300.dp - gap - gap - 40.dp)
        onNodeWithText("OK").assertIsDisplayed()
    }

    // In a dialog too small for the rest, the note keeps its least height, the rest scrolls, and the buttons stay.
    @Test
    fun tooSmallADialogScrollsAndKeepsItsButtons() = runComposeUiTest {
        setContent(dialogContent(height = 300.dp, settingsHeight = 500.dp))
        onNodeWithTag("note").assertHeightIsEqualTo(noteMin)
        onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun contentMinimumIsTheRestAndTheLeastNote() = runComposeUiTest {
        var minHeight by mutableStateOf<Dp?>(null)
        setContent(dialogContent(height = 300.dp, settingsHeight = 500.dp, onMinHeight = { minHeight = it }))
        waitForIdle()
        assertEquals(500.dp + gap + noteMin + gap + 40.dp, minHeight)
    }
}
