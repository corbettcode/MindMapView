package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ModifiedTest {
    // The root with "first" and "second" below it, and "below first" below "first".
    private fun ComposeUiTest.stateWithNodes(): MindMapState {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("first", rootId, "first")
        tree.addNode("belowFirst", "first", "below first")
        tree.addNode("second", rootId, "second")
        val state = MindMapState(tree)
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(800.dp, 600.dp).testTag(TAG)
            )
        }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun MindMapState.selectNode(id: String) = select(tree.getNode(id))

    private fun androidx.compose.ui.unit.Density.center(node: NodeData<*>): Offset {
        return Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
    }

    @Test
    fun mapIsNotModifiedAtFirst() = runComposeUiTest {
        val state = stateWithNodes()

        assertFalse(state.isModified)
    }

    @Test
    fun replacingTheTreeModifiesTheMapAndCanBeUndone() = runComposeUiTest {
        val state = stateWithNodes()
        val imported = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = imported.getRootNode().id
        imported.addNode("imported", rootId, "imported")

        state.replaceTree(imported)

        assertTrue(state.isModified)
        assertEquals(setOf(rootId, "imported"), state.tree.nodes.keys)

        state.undo()

        assertFalse(state.isModified)
        assertEquals(setOf(rootId, "first", "belowFirst", "second"), state.tree.nodes.keys)
    }

    @Test
    fun changeModifiesTheMap() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")

        state.editNodeText("edited")

        assertTrue(state.isModified)
    }

    @Test
    fun foldingModifiesTheMap() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")

        state.toggleFold()

        assertTrue(state.isModified)
    }

    @Test
    fun undoingBackToTheSavedMapLeavesItUnmodifiedAndRedoingModifiesItAgain() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.editNodeText("edited")

        state.undo()
        waitForIdle()
        assertFalse(state.isModified)

        state.redo()
        waitForIdle()
        assertTrue(state.isModified)
    }

    @Test
    fun markSavedLeavesTheMapUnmodifiedUntilTheNextChange() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.editNodeText("edited")

        state.markSaved()
        assertFalse(state.isModified)

        // The map as it was before it was saved is a change from what was saved.
        state.undo()
        waitForIdle()
        assertTrue(state.isModified)
    }

    @Test
    fun settingATreeLeavesTheMapUnmodified() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.editNodeText("edited")

        state.setTree(Tree<Any>())

        assertFalse(state.isModified)
    }

    @Test
    fun selectingViewingAndDraggingTheRootDoNotModifyTheMap() = runComposeUiTest {
        val state = stateWithNodes()
        val root = state.tree.getRootNode()

        state.selectNode("first")
        state.selectAll()
        state.copyNode()
        state.showAttributes = false
        state.fitScreen()
        onNodeWithTag(TAG).performTouchInput {
            swipe(center(root), center(root) + Offset(200f, 100f), durationMillis = 200)
        }
        waitForIdle()

        assertFalse(state.isModified)
    }

    companion object {
        const val TAG = "mindMap"
    }
}
