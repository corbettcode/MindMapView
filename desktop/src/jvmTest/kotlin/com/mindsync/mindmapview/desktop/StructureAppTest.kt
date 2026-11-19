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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.mindmapview.desktop.dialog.UserPreferences
import com.mindsync.mindmapview.desktop.menuBar.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class StructureAppTest {
    private val focusRequester = FocusRequester()

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
        listOf("One", "Two").forEach { state.addNode(it) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.press(
        key: Key
        , modifier: Key
    ) {
        runOnIdle { focusRequester.requestFocus() }
        onRoot().performKeyInput { withKeyDown(modifier) { pressKey(key) } }
        waitForIdle()
    }

    @Test
    fun viewMenuSetsTheStructureAndUndoTakesItBack() = runComposeUiTest {
        val state = shownApp()

        onNodeWithText("View").performClick()
        onNodeWithText("Structure").performClick()
        onNodeWithText(MapStructure.ORG_DOWN.label).performClick()
        waitForIdle()
        assertEquals(MapStructure.ORG_DOWN, state.tree.structure)

        press(Key.Z, Key.CtrlLeft)
        assertEquals(MapStructure.MIND_MAP, state.tree.structure)
    }

    @Test
    fun branchesAreNotMovedRoundTheRootOutsideTheMindMap() = runComposeUiTest {
        val state = shownApp()
        state.setStructure(MapStructure.LOGIC_RIGHT)
        val one = state.tree.getRootNode().children.first()
        state.select(state.tree.getNode(one))
        val canUndoBefore = state.canUndo

        press(Key.M, Key.CtrlLeft)

        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(one))
        assertEquals(canUndoBefore, state.canUndo)
    }

    @Test
    fun newMapsStartInThePreferredStructure() {
        val preferences = UserPreferences(newMapStructure = MapStructure.TIMELINE)

        assertEquals(MapStructure.TIMELINE, newMapTree(preferences).structure)
        assertEquals(MapStructure.MIND_MAP, newMapTree(UserPreferences()).structure)
    }

    @Test
    fun thePreferenceIsKept() {
        var kept = emptyMap<String, String>()
        val store = UserPreferencesStore(read = { kept[it] }, write = { name, value -> kept = kept + (name to value) })

        store.update(store.preferences.copy(newMapStructure = MapStructure.ORG_UP))

        val reread = UserPreferencesStore(read = { kept[it] }, write = { _, _ -> })
        assertEquals(MapStructure.ORG_UP, reread.preferences.newMapStructure)
        assertFalse(UserPreferencesStore.NEW_MAP_STRUCTURE in UserPreferencesStore.MAP_PREFERENCES)
    }
}
