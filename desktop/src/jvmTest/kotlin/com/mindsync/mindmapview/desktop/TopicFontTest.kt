package com.mindsync.mindmapview.desktop

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.TopicKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class TopicFontTest {
    @Test
    fun eachKindOfTopicIsMeasuredInItsOwnFont() = runComposeUiTest {
        val tree = Tree<Any>().apply {
            addNode("topic", getRootNode().id, "<p>iiiiiiii</p>")
            addNode("subtopic", "topic", "<p>iiiiiiii</p>")
            addNode("other", "topic", "<p>iiiiiiii</p>")
        }
        var widths = emptyMap<String, Int>()
        setContent {
            val measurer = MeasureTextSize(
                textMeasurer = rememberTextMeasurer()
                , textStyle = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.SansSerif)
                , density = LocalDensity.current
                , topicFontFamilies = mapOf(TopicKind.SUBTOPIC to FontFamily.Monospace)
            )
            widths = listOf("topic", "subtopic", "other").associateWith {
                measurer.measureContent(tree, tree.getNode(it)).text.size.width
            }
        }
        waitForIdle()

        assertNotEquals(widths.getValue("topic"), widths.getValue("subtopic"))
        assertEquals(widths.getValue("subtopic"), widths.getValue("other"))
    }
}
