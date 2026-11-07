package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.data.BoundaryStyle
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BoundaryAppTest {
    // The window takes every key whatever has the focus. Here the keys go to what has it, so the box that stands
    // for the window takes it back whenever no text field has it.
    private val focusRequester = FocusRequester()

    // The app with its keys taken as the window takes them, and three topics below its root.
    private fun ComposeUiTest.shownApp(): MindMapApp {
        lateinit var app: MindMapApp
        setContent {
            val scope = rememberCoroutineScope()
            app = remember { MindMapApp(TestPlatform(), MindMapState(), scope) }
            Box(
                modifier = Modifier
                    .size(1000.dp, 700.dp)
                    .onPreviewKeyEvent(app::onPreviewKeyEvent)
                    .onKeyEvent(app::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                app.Content()
            }
        }
        waitForIdle()
        val state = app.state
        state.select(state.tree.getRootNode())
        listOf("One", "Two", "Three").forEach { state.addNode(it) }
        state.animateTreeChange()
        waitForIdle()
        return app
    }

    private fun ComposeUiTest.press(
        key: Key
        , modifier: Key? = null
    ) {
        if (onAllNodesWithTag(NODE_TEXT_EDITOR_TAG).fetchSemanticsNodes().isEmpty()) {
            runOnIdle { focusRequester.requestFocus() }
        }
        onRoot().performKeyInput {
            if (modifier == null) pressKey(key) else withKeyDown(modifier) { pressKey(key) }
        }
        waitForIdle()
    }

    private fun MindMapState.idOf(text: String): String =
        tree.nodes.values.single { RichText.parse(it.description).text == text }.id

    private fun MindMapState.selectTopics(vararg texts: String) {
        select(tree.getNode(idOf(texts.first())))
        texts.drop(1).forEach { toggleSelected(idOf(it)) }
    }

    @Test
    fun ctrlBPutsABoundaryAroundTheSelectedTopics() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("One", "Three")

        press(Key.B, modifier = Key.CtrlLeft)

        val boundary = state.boundaries.single()
        assertEquals(listOf("One", "Two", "Three").map { state.idOf(it) }, state.tree.boundaryChildIds(boundary))
        press(Key.Z, modifier = Key.CtrlLeft)
        assertTrue(state.boundaries.isEmpty())
    }

    @Test
    fun ctrlBWhileTypingMakesTheTextBold() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("Two")
        press(Key.F2)

        press(Key.B, modifier = Key.CtrlLeft)
        press(Key.Enter)

        assertTrue(state.boundaries.isEmpty())
        val description = state.tree.getNode(state.idOf("Two")).description
        assertTrue("<b>" in description || "bold" in description || "font-weight" in description, description)
    }

    @Test
    fun boundaryMenuChangesAndRemovesIt() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        state.selectTopics("Two")
        press(Key.B, modifier = Key.CtrlLeft)
        val node = state.tree.getNode(state.idOf("Two")) as RectangleNodeData
        // In the padding above the topic, inside the boundary, where there is nothing else.
        val insideY = node.path.centerY.dpVal - node.path.height.dpVal / 2 - 5f
        val inside = with(density) {
            Offset(node.path.centerX.dpVal.dp.toPx(), insideY.dp.toPx())
        } * state.scaleFactor + state.currentPoint
        val boundaryId = state.boundaries.single().id

        onNodeWithTag(MIND_MAP_TAG).performMouseInput { rightClick(inside) }
        waitForIdle()
        onNodeWithText("Solid").performClick()
        waitForIdle()
        assertEquals(BoundaryStyle.SOLID, state.boundaries.single().style)

        onNodeWithTag(MIND_MAP_TAG).performMouseInput { rightClick(inside) }
        waitForIdle()
        onNodeWithText("Remove Boundary").performClick()
        waitForIdle()
        assertTrue(state.boundaries.isEmpty())
        assertNull(state.boundaries.firstOrNull { it.id == boundaryId })
    }
}
