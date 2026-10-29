package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.NodeLineStyle
import com.mindsync.mindmapview.desktop.component.LINE_STYLE_LABELS
import com.mindsync.mindmapview.desktop.component.LINE_WIDTH_LABELS
import kotlin.test.Test
import kotlin.test.assertEquals

class LineSettingsTest {
    @Test
    fun everyStyleAndWidthHasALabelAfterInherited() {
        assertEquals(listOf(null) + NodeLineStyle.entries, LINE_STYLE_LABELS.keys.toList())
        assertEquals(
            listOf("Inherited", "Linear", "Bezier", "Sharp linear", "Sharp bezier")
            , LINE_STYLE_LABELS.values.toList()
        )
        assertEquals(listOf("Inherited", "Thin", "1", "2", "4", "8"), LINE_WIDTH_LABELS.values.toList())
    }
}
