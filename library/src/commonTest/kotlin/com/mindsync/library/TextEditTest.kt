package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextEditTest {
    // The root with "a" below it, and "b" and "c" below "a".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            addNode("a", getRootNode().id, "A")
            addNode("b", "a", "B")
            addNode("c", "a", "C")
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNode(id: String) = select(tree.getNode(id))

    private fun MindMapState.textOf(id: String) = tree.getNode(id).description

    private fun MindMapState.childrenOf(id: String) = tree.getNode(id).children

    // What there is of the map but where its nodes are drawn.
    private fun MindMapState.content(): String = FreeMind.write(tree)

    private fun MindMapState.addSiblingToEdit(): String {
        addNodeForTextEdit { addSiblingNode("Topic 1") }
        return textEditNodeId!!
    }

    @Test
    fun addedNodeIsSelectedAndEdited() {
        val state = state()
        state.selectNode("b")

        val id = state.addSiblingToEdit()

        assertEquals(listOf("b", id, "c"), state.childrenOf("a"))
        assertEquals(id, state.selectedNode?.id)
        assertEquals("Topic 1", state.textOf(id))
    }

    @Test
    fun addedNodeAndItsTextAreOneChange() {
        val state = state()
        state.selectNode("b")
        val id = state.addSiblingToEdit()

        state.previewText("Hel")
        state.previewText("Hello")
        state.commitTextEdit()

        assertNull(state.textEditNodeId)
        assertEquals("Hello", state.textOf(id))
        state.undo()
        assertEquals(listOf("b", "c"), state.childrenOf("a"))
        assertFalse(state.canUndo)
        state.redo()
        assertEquals(listOf("b", id, "c"), state.childrenOf("a"))
        assertEquals("Hello", state.textOf(id))
    }

    @Test
    fun cancelledNewNodeLeavesTheMapAsItWas() {
        val state = state()
        val content = state.content()
        state.selectNode("b")
        state.addSiblingToEdit()

        state.previewText("Hello")
        state.cancelTextEdit()

        assertNull(state.textEditNodeId)
        assertEquals(content, state.content())
        assertFalse(state.canUndo)
        assertFalse(state.isModified)
        assertEquals("b", state.selectedNode?.id)
    }

    @Test
    fun typingIntoANodeIsOneChange() {
        val state = state()
        state.selectNode("b")

        state.startTextEdit()
        state.previewText("x")
        state.previewText("xy")
        state.commitTextEdit()

        assertEquals("xy", state.textOf("b"))
        state.undo()
        assertEquals("B", state.textOf("b"))
        assertFalse(state.canUndo)
    }

    @Test
    fun cancelledTypingPutsTheTextBack() {
        val state = state()
        state.selectNode("b")

        state.startTextEdit()
        state.previewText("xy")
        state.cancelTextEdit()

        assertEquals("B", state.textOf("b"))
        assertEquals("b", state.selectedNode?.id)
        assertFalse(state.canUndo)
        assertFalse(state.isModified)
    }

    @Test
    fun typingStartedWithACharacterReplacesTheText() {
        val state = state()
        state.selectNode("b")

        state.startTextEdit("q")

        assertEquals("b", state.textEditNodeId)
        assertEquals("q", state.textOf("b"))
    }

    @Test
    fun theSameTextInTheEditorsOwnHtmlIsNoChange() {
        val state = state()
        state.selectNode("b")

        state.startTextEdit()
        state.previewText("<p>B</p>")
        state.commitTextEdit()

        assertEquals("B", state.textOf("b"))
        assertFalse(state.canUndo)
        assertFalse(state.isModified)
    }

    @Test
    fun committedEmptyNewNodeGoes() {
        val state = state()
        val content = state.content()
        state.selectNode("b")
        state.addSiblingToEdit()

        state.previewText("  ")
        state.commitTextEdit()

        assertEquals(content, state.content())
        assertFalse(state.canUndo)
    }

    @Test
    fun committedEmptyTextKeepsTheTextThereWas() {
        val state = state()
        state.selectNode("b")

        state.startTextEdit()
        state.previewText("<p></p>")
        state.commitTextEdit()

        assertEquals("B", state.textOf("b"))
        assertFalse(state.canUndo)
    }

    @Test
    fun rootIsNeverLeftWithoutText() {
        val state = state()
        val root = state.tree.getRootNode()
        val text = root.description
        state.select(root)

        state.startTextEdit("")
        state.commitTextEdit()

        assertEquals(text, state.textOf(root.id))
    }

    @Test
    fun undoWhileTypingUndoesTheTyping() {
        val state = state()
        state.selectNode("b")
        state.startTextEdit()
        state.previewText("xy")

        state.undo()

        assertNull(state.textEditNodeId)
        assertEquals("B", state.textOf("b"))
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun selectingAnotherNodeCommitsTheTyping() {
        val state = state()
        state.selectNode("b")
        state.startTextEdit()
        state.previewText("xy")

        state.selectNode("c")

        assertNull(state.textEditNodeId)
        assertEquals("xy", state.textOf("b"))
        assertTrue(state.canUndo)
    }

    @Test
    fun anotherChangeIsAChangeOfItsOwnAfterTheTyping() {
        val state = state()
        state.selectNode("b")
        state.startTextEdit()
        state.previewText("xy")

        state.editNodeColor(Color.Red)
        state.undo()

        assertEquals("xy", state.textOf("b"))
        assertTrue(state.canUndo)
        state.undo()
        assertEquals("B", state.textOf("b"))
    }

    @Test
    fun startingToTypeIntoAnotherNodeCommitsTheFirst() {
        val state = state()
        state.selectNode("b")
        state.startTextEdit()
        state.previewText("xy")

        state.tree.getNode("c").let { state.select(it) }
        state.startTextEdit("z")
        state.commitTextEdit()

        assertEquals("xy", state.textOf("b"))
        assertEquals("z", state.textOf("c"))
        state.undo()
        assertEquals("C", state.textOf("c"))
        assertEquals("xy", state.textOf("b"))
    }

    @Test
    fun anotherTreeEndsTheTyping() {
        val state = state()
        state.selectNode("b")
        state.startTextEdit()
        state.previewText("xy")

        state.setTree(Tree<Any>(Dp(0f), Dp(0f)))

        assertNull(state.textEditNodeId)
        assertFalse(state.canUndo)
    }

    @Test
    fun previewWithoutAnEditChangesNothing() {
        val state = state()
        val content = state.content()
        state.selectNode("b")

        state.previewText("xy")

        assertEquals(content, state.content())
    }

    @Test
    fun nodeGrowsWithTheTextAsItIsTypedAndKeepsIt() = runComposeUiTest {
        val state = state()
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()
        state.selectNode("b")
        val width = (state.tree.getNode("b") as RectangleNodeData).path.width

        state.startTextEdit()
        state.previewText("A much longer text than there was")
        waitForIdle()

        val typed = state.tree.getNode("b") as RectangleNodeData
        assertTrue(typed.path.width.dpVal > width.dpVal)
        assertEquals("A much longer text than there was", typed.description)
    }
}
