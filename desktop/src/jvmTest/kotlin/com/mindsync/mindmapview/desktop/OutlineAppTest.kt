package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.TextEditHost
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.outline.outlineAttributeMarkTag
import com.mindsync.library.outline.outlineNoteMarkTag
import com.mindsync.library.outline.outlineRowTag
import com.mindsync.library.outline.OUTLINE_TEXT_EDITOR_TAG
import com.mindsync.library.util.RichText
import com.mindsync.mindmapview.desktop.component.SPLIT_DIVIDER_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OutlineAppTest {
    // The window takes every key whatever has the focus. Here the keys go to what has it, so the box that stands
    // for the window takes it back whenever no text field has it.
    private val focusRequester = FocusRequester()
    private val platform = TestPlatform()

    // The app with its keys taken as the window takes them, and three topics below its root.
    private fun ComposeUiTest.shownApp(width: Dp = 1000.dp): MindMapState {
        lateinit var app: MindMapApp
        setContent {
            val scope = rememberCoroutineScope()
            app = remember { MindMapApp(platform, MindMapState(), scope) }
            Box(
                modifier = Modifier
                    .size(width, 700.dp)
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
        , modifiers: List<Key> = emptyList()
    ) {
        val isTyping = listOf(NODE_TEXT_EDITOR_TAG, OUTLINE_TEXT_EDITOR_TAG)
            .any { onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        if (!isTyping) runOnIdle { focusRequester.requestFocus() }
        onRoot().performKeyInput {
            fun press(index: Int) {
                if (index == modifiers.size) pressKey(key) else withKeyDown(modifiers[index]) { press(index + 1) }
            }
            press(0)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.nextViewMode() = press(Key.O, listOf(Key.CtrlLeft, Key.ShiftLeft))

    private fun ComposeUiTest.typeInRow(text: String) {
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextInput(text)
        waitForIdle()
    }

    private fun MindMapState.idOf(text: String): String =
        tree.nodes.values.single { RichText.parse(it.description).text == text }.id

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text

    private fun MindMapState.childTexts(id: String = tree.getRootNode().id) =
        tree.getNode(id).children.map { textOf(it) }

    private fun MindMapState.selectTopic(text: String) = select(tree.getNode(idOf(text)))

    private fun ComposeUiTest.isShown(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun ctrlShiftOGoesRoundTheViewModesAndIsRemembered() = runComposeUiTest {
        shownApp()
        assertTrue(!isShown(OUTLINE_PANE_TAG))

        nextViewMode()
        assertEquals(ViewMode.OUTLINER, platform.preferencesStore.preferences.viewMode)
        assertTrue(isShown(OUTLINE_PANE_TAG))
        assertTrue(!isShown(SPLIT_DIVIDER_TAG))

        nextViewMode()
        assertEquals(ViewMode.BOTH, platform.preferencesStore.preferences.viewMode)
        assertTrue(isShown(OUTLINE_PANE_TAG))
        assertTrue(isShown(SPLIT_DIVIDER_TAG))

        nextViewMode()
        assertEquals(ViewMode.MAP, platform.preferencesStore.preferences.viewMode)
        assertTrue(!isShown(OUTLINE_PANE_TAG))
    }

    @Test
    fun narrowWindowShowsTheOutlineAlone() = runComposeUiTest {
        shownApp(width = 600.dp)

        nextViewMode()
        nextViewMode()

        assertEquals(ViewMode.BOTH, platform.preferencesStore.preferences.viewMode)
        assertTrue(isShown(OUTLINE_PANE_TAG))
        assertTrue(!isShown(SPLIT_DIVIDER_TAG))
    }

    @Test
    fun topicsAreTypedOneAfterAnotherWithEnter() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        state.selectTopic("Three")

        press(Key.Enter)
        assertEquals(TextEditHost.OUTLINE, state.textEditHost)
        typeInRow("Four")
        press(Key.Enter)
        typeInRow("Five")
        press(Key.Enter)
        press(Key.Enter)

        assertEquals(listOf("One", "Two", "Three", "Four", "Five"), state.childTexts())
        assertNull(state.textEditNodeId)
        press(Key.Z, listOf(Key.CtrlLeft))
        assertEquals(listOf("One", "Two", "Three", "Four"), state.childTexts())
    }

    @Test
    fun tabIndentsInTheOutlineAndAddsAChildOnTheMap() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        state.selectTopic("Two")

        press(Key.Tab)
        assertEquals(listOf("Two"), state.childTexts(state.idOf("One")))
        press(Key.Tab, listOf(Key.ShiftLeft))
        assertEquals(listOf("One", "Two", "Three"), state.childTexts())

        nextViewMode()
        nextViewMode()
        state.selectTopic("Three")
        press(Key.Tab)
        assertEquals(TextEditHost.MAP, state.textEditHost)
        assertEquals(state.idOf("Three"), state.tree.getNode(state.textEditNodeId!!).parentId)
        press(Key.Escape)
    }

    @Test
    fun arrowKeysGoAlongTheRowsAndFold() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        state.selectTopic("One")
        state.addNode("A")
        state.selectTopic("One")
        waitForIdle()

        press(Key.DirectionDown)
        assertEquals("A", state.selectedNode?.let { state.textOf(it.id) })
        press(Key.DirectionDown)
        assertEquals("Two", state.selectedNode?.let { state.textOf(it.id) })
        press(Key.DirectionUp)
        press(Key.DirectionLeft)
        assertEquals("One", state.selectedNode?.let { state.textOf(it.id) })
        press(Key.DirectionLeft)
        assertTrue(state.tree.isFolded(state.idOf("One")))
        press(Key.DirectionRight)
        assertTrue(!state.tree.isFolded(state.idOf("One")))
        press(Key.DirectionRight)
        assertEquals("A", state.selectedNode?.let { state.textOf(it.id) })
    }

    @Test
    fun altShiftUpMovesATopicOnTheMap() = runComposeUiTest {
        val state = shownApp()
        state.selectTopic("Three")

        press(Key.DirectionUp, listOf(Key.AltLeft, Key.ShiftLeft))

        assertEquals(listOf("One", "Three", "Two"), state.childTexts())
    }

    @Test
    fun changingTheViewWhileTypingKeepsTheText() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        state.selectTopic("Two")
        press(Key.F2)
        typeInRow("Twice")

        nextViewMode()

        assertNull(state.textEditNodeId)
        assertEquals(ViewMode.BOTH, platform.preferencesStore.preferences.viewMode)
        assertTrue(state.childTexts().contains("Twice"))
    }

    @Test
    fun marksOfARowShowTheNoteAndAttributesInPopups() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        val id = state.idOf("Two")
        state.select(state.tree.getNode(id))
        state.editNodeNote("Remember the milk")
        state.editNodeAttributes(listOf(NodeAttribute("Owner", "Ann")))
        state.select(null)
        waitForIdle()

        onNodeWithTag(outlineNoteMarkTag(id)).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1500)
        waitForIdle()
        onNodeWithText("Remember the milk", substring = true).assertExists()

        onNodeWithTag(outlineAttributeMarkTag(id)).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        onNodeWithText("Ann").assertExists()

        onNodeWithTag(outlineRowTag(state.idOf("One"))).performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithText("Ann").assertDoesNotExist()
        onNodeWithText("Remember the milk", substring = true).assertDoesNotExist()
    }

    @Test
    fun clickOnARowPicksTheTargetOfANewArrowLink() = runComposeUiTest {
        val state = shownApp()
        nextViewMode()
        state.selectTopic("One")
        press(Key.L, listOf(Key.CtrlLeft))
        onNodeWithText("Click the node or row", substring = true).assertExists()

        onNodeWithTag(outlineRowTag(state.idOf("Three"))).performClick()
        waitForIdle()

        val link = state.arrowLinks.single()
        assertEquals(state.idOf("One"), link.sourceId)
        assertEquals(state.idOf("Three"), link.targetId)
        onNodeWithText("Click the node or row", substring = true).assertDoesNotExist()

        onNodeWithTag(outlineRowTag(state.idOf("Two"))).performClick()
        waitForIdle()
        assertEquals(1, state.arrowLinks.size, "a click with no link being added adds none")
    }
}
