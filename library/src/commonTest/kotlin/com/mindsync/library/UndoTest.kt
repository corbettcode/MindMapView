package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OutlineNode
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UndoTest {
    private fun ComposeUiTest.setMindMap(state: MindMapState) {
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(800.dp, 600.dp).testTag(TAG)
            )
        }
        waitForIdle()
    }

    // The root with "first" and "second" below it, and "below first" below "first".
    private fun ComposeUiTest.stateWithNodes(): MindMapState {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("first", rootId, "first")
        state.tree.addNode("belowFirst", "first", "below first")
        state.tree.addNode("second", rootId, "second")
        state.setTree(state.tree)
        setMindMap(state)
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    // What there is of the map but where its nodes are drawn.
    private fun MindMapState.content(): String = FreeMind.write(tree)

    private fun MindMapState.selectNode(id: String) = select(tree.getNode(id))

    private fun androidx.compose.ui.unit.Density.center(node: NodeData<*>): Offset {
        return Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
    }

    @Test
    fun thereIsNothingToUndoAtFirst() = runComposeUiTest {
        val state = stateWithNodes()
        val content = state.content()

        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        state.undo()
        state.redo()
        assertEquals(content, state.content())
    }

    @Test
    fun addedNodeIsUndoneAndRedone() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("second")

        state.addNode("added")
        waitForIdle()
        val addedId = state.tree.getNode("second").children.single()
        assertTrue(state.canUndo)

        state.undo()
        waitForIdle()
        assertEquals(emptyList(), state.tree.getNode("second").children)
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)

        state.redo()
        waitForIdle()
        assertEquals(listOf(addedId), state.tree.getNode("second").children)
        assertEquals("added", state.tree.getNode(addedId).description)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun everyChangeToTheMapIsOneStepToUndoAndRedo() = runComposeUiTest {
        val state = stateWithNodes()
        val contents = mutableListOf(state.content())
        fun change(change: () -> Unit) {
            change()
            waitForIdle()
            assertNotEquals(contents.last(), state.content(), "change ${contents.size} changed nothing")
            contents.add(state.content())
        }

        state.selectNode("first")
        change { state.editNodeText("first edited") }
        change { state.editNodeColor(Color.Red) }
        change { state.editNodeTextColor(Color.Blue) }
        change { state.editNodeShape(NodeShape.OVAL) }
        change { state.editNodeAttributes(listOf(NodeAttribute("name", "value"))) }
        change { state.editNodeNote("a note") }
        change { state.toggleFold() }
        change { state.unfoldAll() }
        change { state.foldOneLevel() }
        change { state.unfoldOneLevel() }
        state.select(state.tree.getRootNode())
        change { state.toggleChildrenFold() }
        change { state.toggleChildrenFold() }
        state.selectNode("first")
        change { state.foldAll() }
        change { state.toggleFold() }
        var linkId = ""
        change { linkId = state.addArrowLink("first", "second")!!.id }
        change { state.updateArrowLink(state.arrowLinks.single().copy(hasStartArrow = true)) }
        change { state.removeArrowLink(linkId) }
        change { state.moveNodeToOtherSide() }
        change { state.layoutSide = LayoutSide.RIGHT }
        state.copyNode()
        state.selectNode("second")
        change { state.pasteNode() }
        change { state.addNodes(listOf(OutlineNode("outlined"))) }
        state.selectNode("belowFirst")
        change { state.cutNode() }
        state.selectNode("second")
        change { state.removeNode() }

        contents.dropLast(1).reversed().forEach { content ->
            state.undo()
            waitForIdle()
            assertEquals(content, state.content())
        }
        assertFalse(state.canUndo)
        contents.drop(1).forEach { content ->
            state.redo()
            waitForIdle()
            assertEquals(content, state.content())
        }
        assertFalse(state.canRedo)
    }

    @Test
    fun changeThatLeavesTheMapAsItWasIsNotRecorded() = runComposeUiTest {
        val state = stateWithNodes()

        state.selectNode("first")
        state.editNodeText("first")
        state.editNodeColor(state.tree.getNode("first").color)
        state.selectNode("second")
        state.toggleFold()
        state.select(state.tree.getRootNode())
        state.removeNode()
        state.cutNode()
        waitForIdle()

        assertFalse(state.canUndo)
    }

    @Test
    fun selectingCopyingAndViewingAreNotRecorded() = runComposeUiTest {
        val state = stateWithNodes()

        state.selectNode("first")
        state.copyNode()
        state.selectAll()
        state.showAttributes = false
        state.fitScreen()
        state.goToNode("belowFirst")
        waitForIdle()

        assertFalse(state.canUndo)
    }

    @Test
    fun goingToAHiddenNodeIsUndoneByFoldingAgain() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.toggleFold()
        waitForIdle()

        state.goToNode("belowFirst")
        waitForIdle()
        assertFalse(state.tree.isFolded("first"))

        state.undo()
        waitForIdle()
        assertTrue(state.tree.isFolded("first"))
    }

    @Test
    fun groupedChangesAreUndoneTogether() = runComposeUiTest {
        val state = stateWithNodes()
        val content = state.content()
        state.selectNode("first")

        state.groupChanges {
            state.editNodeText("edited")
            state.editNodeColor(Color.Red)
            state.editNodeNote("a note")
        }
        waitForIdle()

        state.undo()
        waitForIdle()
        assertEquals(content, state.content())
        assertFalse(state.canUndo)
    }

    @Test
    fun groupWithoutChangesIsNotRecorded() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")

        state.groupChanges { state.editNodeText("first") }

        assertFalse(state.canUndo)
    }

    @Test
    fun newChangeLeavesNothingToRedo() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.editNodeText("edited")
        state.undo()
        waitForIdle()

        state.selectNode("second")
        state.editNodeText("second edited")

        assertFalse(state.canRedo)
    }

    @Test
    fun settingATreeForgetsTheHistory() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")
        state.editNodeText("edited")
        state.undo()
        state.editNodeText("edited again")

        state.setTree(Tree<Any>())

        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun undoKeepsTheSelectedNodesThatAreStillThere() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("second")
        state.addNode("added")
        waitForIdle()
        val addedId = state.tree.getNode("second").children.single()
        state.selectNode("first")
        state.toggleSelected(addedId)

        state.undo()
        waitForIdle()

        assertEquals(listOf("first"), state.selectedNodes.map { it.id })
        assertEquals("first", state.selectedNode?.id)
    }

    @Test
    fun undoneNodeIsDrawnWhereItWasArranged() = runComposeUiTest {
        val state = stateWithNodes()
        val path = state.tree.getNode("belowFirst").path
        state.selectNode("first")
        state.toggleFold()
        waitForIdle()

        state.undo()
        waitForIdle()

        assertEquals(path, state.tree.getNode("belowFirst").path)
    }

    @Test
    fun nodeDroppedOnAnotherIsUndoneInOneStep() = runComposeUiTest {
        val state = stateWithNodes()
        val content = state.content()
        val first = state.tree.getNode("first")
        val second = state.tree.getNode("second")

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()
        assertEquals("first", state.tree.getNode("second").parentId)

        state.undo()
        waitForIdle()
        assertEquals(content, state.content())
        assertFalse(state.canUndo)
    }

    @Test
    fun nodeDroppedOnEmptySpaceInItsOwnPlaceIsNotRecorded() = runComposeUiTest {
        val state = stateWithNodes()
        val second = state.tree.getNode("second")

        onNodeWithTag(TAG).performTouchInput {
            swipe(center(second), Offset(700f, center(second).y), durationMillis = 200)
        }
        waitForIdle()

        assertFalse(state.canUndo)
    }

    @Test
    fun draggedRootIsUndoneInOneStep() = runComposeUiTest {
        val state = stateWithNodes()
        val root = state.tree.getRootNode()

        onNodeWithTag(TAG).performTouchInput {
            swipe(center(root), center(root) + Offset(200f, 100f), durationMillis = 200)
        }
        waitForIdle()
        assertNotEquals(root.path, state.tree.getRootNode().path)

        state.undo()
        waitForIdle()
        assertEquals(root.path, state.tree.getRootNode().path)
        assertFalse(state.canUndo)
    }

    @Test
    fun arrowLinkFromACtrlDragIsUndone() = runComposeUiTest {
        val state = stateWithNodes()
        val first = state.tree.getNode("first")
        val second = state.tree.getNode("second")

        onNodeWithTag(TAG).performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag(TAG).performMouseInput {
            val start = center(first)
            val end = center(second)
            moveTo(start)
            press()
            (1..4).forEach { step -> moveTo(start + (end - start) * (step / 4f)) }
            release()
        }
        onNodeWithTag(TAG).performKeyInput { keyUp(Key.CtrlLeft) }
        waitForIdle()
        assertEquals(1, state.arrowLinks.size)

        state.undo()
        waitForIdle()
        assertTrue(state.arrowLinks.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun editNodeLineIsUndone() = runComposeUiTest {
        val state = stateWithNodes()
        state.selectNode("first")

        state.editNodeLine(NodeLine(style = NodeLineStyle.LINEAR))
        waitForIdle()
        assertEquals(NodeLine(style = NodeLineStyle.LINEAR), state.tree.lineOf("first"))

        state.undo()
        waitForIdle()
        assertTrue(state.tree.lineOf("first").isEmpty)

        state.redo()
        waitForIdle()
        assertEquals(NodeLine(style = NodeLineStyle.LINEAR), state.tree.lineOf("first"))
    }

    @Test
    fun undoBringsBackRemovedNodeLine() = runComposeUiTest {
        val state = stateWithNodes()
        val line = NodeLine(NodeLineStyle.SHARP_BEZIER, Color.Red, NodeLineWidth.FOUR)
        state.selectNode("first")
        state.editNodeLine(line)
        state.selectNode("belowFirst")
        state.editNodeLine(NodeLine(width = NodeLineWidth.THIN))

        state.selectNode("first")
        state.removeNode()
        waitForIdle()
        assertFalse("first" in state.tree.getRootNode().children)

        state.undo()
        waitForIdle()
        assertEquals(line, state.tree.lineOf("first"))
        assertEquals(NodeLine(width = NodeLineWidth.THIN), state.tree.lineOf("belowFirst"))
    }

    companion object {
        const val TAG = "mindMap"
    }
}
