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
}
