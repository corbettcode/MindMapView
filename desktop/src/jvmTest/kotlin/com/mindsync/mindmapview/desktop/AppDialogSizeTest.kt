package com.mindsync.mindmapview.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDialogSizeTest {
    private val screen = DpSize(1366.dp, 728.dp)

    @Test
    fun sizeThatFitsIsKept() = assertEquals(DpSize(560.dp, 600.dp), DpSize(560.dp, 600.dp).fittedTo(screen))

    @Test
    fun sizeTallerThanTheScreenIsMadeToFit() =
        assertEquals(DpSize(560.dp, 728.dp), DpSize(560.dp, 980.dp).fittedTo(screen))

    // The window is kept on the screen, which is no resizing by the user, so the dialog keeps the size it asked for.
    @Test
    fun windowKeptOnTheScreenIsNotAResize() =
        assertFalse(isResizedByUser(DpSize(560.dp, 728.dp), asked = DpSize(560.dp, 980.dp), screen = screen))

    @Test
    fun windowOfAnotherSizeIsAResize() =
        assertTrue(isResizedByUser(DpSize(640.dp, 700.dp), asked = DpSize(560.dp, 980.dp), screen = screen))

    // A dialog starts at the height its content needs at the least.
    @Test
    fun firstFitIsTheContentMinimum() =
        assertEquals(700.dp, resizedHeight(current = 560.dp, oldMin = null, newMin = 700.dp))

    // Content that needs more or less room, as a section opens or closes, grows or shrinks the dialog by as much, so
    // room the user gave it by resizing it is kept.
    @Test
    fun contentChangeMovesTheHeightByAsMuch() {
        assertEquals(1010.dp, resizedHeight(current = 800.dp, oldMin = 600.dp, newMin = 810.dp))
        assertEquals(800.dp, resizedHeight(current = 1010.dp, oldMin = 810.dp, newMin = 600.dp))
    }

    @Test
    fun heightIsNeverBelowTheContentMinimum() =
        assertEquals(810.dp, resizedHeight(current = 500.dp, oldMin = 600.dp, newMin = 810.dp))

    // A window that grows downward past the bottom of the screen is moved up, as far as the top of the screen.
    @Test
    fun windowGrownPastTheBottomIsMovedUp() {
        assertEquals(120, keptOnScreenTop(top = 200, height = 600, screenTop = 0, screenBottom = 720))
        assertEquals(30, keptOnScreenTop(top = 200, height = 800, screenTop = 30, screenBottom = 720))
    }

    @Test
    fun windowOnTheScreenStaysWhereItIs() =
        assertEquals(50, keptOnScreenTop(top = 50, height = 600, screenTop = 0, screenBottom = 720))

    // A window that keeps its size is fitted to its content when it opens, and after that scrolls what is added.
    @Test
    fun windowKeepingItsSizeIsOnlyFittedOnce() {
        val state = AppDialogState(DpSize(560.dp, 560.dp), minSize = DpSize(480.dp, 0.dp))
        state.contentMinHeight = 600.dp
        state.fitToContent(titleBarHeight = 30.dp, keepsSize = true)
        assertEquals(DpSize(560.dp, 630.dp), state.size)
        assertEquals(DpSize(480.dp, 630.dp), state.minSize)

        state.contentMinHeight = 810.dp
        state.fitToContent(titleBarHeight = 30.dp, keepsSize = true)
        assertEquals(DpSize(560.dp, 630.dp), state.size)
        assertEquals(DpSize(480.dp, 630.dp), state.minSize)
    }

    @Test
    fun dialogNotKeepingItsSizeFollowsItsContent() {
        val state = AppDialogState(DpSize(560.dp, 560.dp))
        state.contentMinHeight = 600.dp
        state.fitToContent(titleBarHeight = 30.dp)
        state.contentMinHeight = 810.dp
        state.fitToContent(titleBarHeight = 30.dp)
        assertEquals(840.dp, state.size.height)
    }
}
