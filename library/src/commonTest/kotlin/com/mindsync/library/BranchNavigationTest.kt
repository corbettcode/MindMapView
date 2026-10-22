package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class BranchNavigationTest {
    private fun tree(): Tree<Any> {
        val tree = Tree<Any>(Dp(400f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "a")
        tree.addNode("a1", "a", "a1")
        tree.addNode("a2", "a", "a2")
        tree.addNode("a1x", "a1", "a1x")
        tree.addNode("b", rootId, "b")
        return tree
    }

    @Test
    fun selectionGoesUpAndDownABranch() = runComposeUiTest {
        val state = MindMapState(tree())
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()

        // Down the branch, the first child each time, until there is none.
        state.goToNode("a")
        state.selectFirstChild()
        assertEquals("a1", state.selectedNode?.id)
        state.selectFirstChild()
        assertEquals("a1x", state.selectedNode?.id)
        state.selectFirstChild()
        assertEquals("a1x", state.selectedNode?.id)

        // Up the branch to the root, where it stops.
        state.selectParent()
        assertEquals("a1", state.selectedNode?.id)
        state.selectParent()
        state.selectParent()
        val rootId = state.tree.getRootNode().id
        assertEquals(rootId, state.selectedNode?.id)
        state.selectParent()
        assertEquals(rootId, state.selectedNode?.id)

        // The root has a branch on each side, so it is not gone down from.
        state.selectFirstChild()
        assertEquals(rootId, state.selectedNode?.id)
    }

    @Test
    fun aFoldedNodeIsNotGoneDownFromAndNoSelectionStaysNone() = runComposeUiTest {
        val state = MindMapState(tree())
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()

        state.selectParent()
        state.selectFirstChild()
        assertNull(state.selectedNode)

        state.goToNode("a1")
        state.toggleFold()
        state.selectFirstChild()
        assertEquals("a1", state.selectedNode?.id)
    }
}
