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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.data.DpOffset
import com.mindsync.library.outline.outlineRowTag
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FloatingAppTest {
    private val focusRequester = FocusRequester()

    // The app with its keys taken as the window takes them, and three topics below its root.
    private fun ComposeUiTest.shownApp(): MindMapState {
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
        return state
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

    private fun MindMapState.floatingIds() = tree.getRootNode().children.filter { tree.isFloating(it) }

    @Test
    fun doubleClickOnBlankMapAddsAFloatingTopicToTypeIn() = runComposeUiTest {
        val state = shownApp()

        onNodeWithTag(MIND_MAP_TAG).performMouseInput {
            click(Offset(40f, height - 40f))
            click(Offset(40f, height - 40f))
        }
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("Idea")
        waitForIdle()
        press(Key.Enter)

        val id = state.floatingIds().single()
        assertEquals("Idea", RichText.parse(state.tree.getNode(id).description).text)
        press(Key.Z, Key.CtrlLeft)
        assertTrue(state.floatingIds().isEmpty())
    }

    @Test
    fun editMenuAddsAFloatingTopic() = runComposeUiTest {
        val state = shownApp()
        state.select(null)
        waitForIdle()

        onNodeWithText("Edit").performClick()
        waitForIdle()
        // The menu is longer than the window here, so the item is scrolled to first.
        onNodeWithText("Add Floating Topic").performScrollTo().performClick()
        waitForIdle()

        val id = state.floatingIds().single()
        assertEquals(id, state.textEditNodeId)
        press(Key.Escape)
        assertTrue(state.floatingIds().isEmpty())
    }

    @Test
    fun nodeMenuMakesATopicFloatAndAttachesItAgain() = runComposeUiTest {
        val state = shownApp()
        runOnIdle { focusRequester.requestFocus() }
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.O) } } }
        waitForIdle()
        val id = state.idOf("Two")

        onNodeWithTag(outlineRowTag(id)).performMouseInput { rightClick(center) }
        onNodeWithText("Make Floating Topic").performClick()
        waitForIdle()
        assertTrue(state.tree.isFloating(id))

        onNodeWithTag(outlineRowTag(id)).performMouseInput { rightClick(center) }
        onNodeWithText("Attach to Root").performClick()
        waitForIdle()
        assertFalse(state.tree.isFloating(id))
    }

    @Test
    fun altArrowNudgesAFloatingTopic() = runComposeUiTest {
        val state = shownApp()
        val id = state.idOf("Two")
        state.tree.setFloating(id, DpOffset(Dp(0f), Dp(300f)))
        state.select(state.tree.getNode(id))

        press(Key.DirectionRight, Key.AltLeft)
        press(Key.DirectionUp, Key.AltLeft)

        assertEquals(DpOffset(Dp(10f), Dp(290f)), assertNotNull(state.tree.floatingOffsetOf(id)))
    }

    @Test
    fun altArrowDoesNothingToAnAttachedTopic() = runComposeUiTest {
        val state = shownApp()
        state.select(state.tree.getNode(state.idOf("Two")))

        press(Key.DirectionRight, Key.AltLeft)

        assertTrue(state.floatingIds().isEmpty())
    }

    @Test
    fun blankMapMenuAddsAFloatingTopicWhereItWasOpened() = runComposeUiTest {
        val state = shownApp()

        onNodeWithTag(MIND_MAP_TAG).performMouseInput { rightClick(Offset(40f, height - 40f)) }
        onNodeWithText("Add Floating Topic").performClick()
        waitForIdle()

        val id = state.floatingIds().single()
        assertEquals(id, state.textEditNodeId)
        val root = state.tree.getRootNode()
        val node = state.tree.getNode(id)
        assertTrue(node.path.centerX.dpVal < root.path.centerX.dpVal, "left of the root, where it was opened")
        assertTrue(node.path.centerY.dpVal > root.path.centerY.dpVal, "below the root, where it was opened")
        press(Key.Escape)
        assertTrue(state.floatingIds().isEmpty())
    }
}

