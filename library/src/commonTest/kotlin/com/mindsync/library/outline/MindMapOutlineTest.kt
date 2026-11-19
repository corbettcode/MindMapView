package com.mindsync.library.outline

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MindMapOutlineTest {
    private var menuNodeId: String? = null
    private var menuPosition: Offset? = null
    private var doubleClickedId: String? = null
    private val noteHovers = mutableListOf<String?>()
    private val attributeHovers = mutableListOf<String?>()

    // The root with "p" on its right and "q" on its left; "b" and "c" below "p", and "f" below "b".
    private fun ComposeUiTest.shownState(tree: Tree<Any> = tree()): MindMapState {
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent {
            MindMapOutline(
                state = state
                , modifier = Modifier.size(400.dp, 300.dp)
                , onRowMenu = { node, position ->
                    menuNodeId = node.id
                    menuPosition = position
                }
                , onRowDoubleClick = { doubleClickedId = it.id }
                , onNoteHover = { node, _ -> noteHovers.add(node?.id) }
                , onAttributeHover = { node, _ -> attributeHovers.add(node?.id) }
            )
        }
        waitForIdle()
        return state
    }

    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        addNode("b", "p", "B")
        addNode("c", "p", "C")
        addNode("f", "b", "F")
    }

    private fun ComposeUiTest.boundsOf(id: String) = onNodeWithTag(outlineRowTag(id)).getBoundsInRoot()

    @Test
    fun rowsAreInOrderAndIndentedByDepth() = runComposeUiTest {
        val state = shownState()
        val ids = listOf(state.tree.getRootNode().id, "p", "b", "f", "c", "q")

        val tops = ids.map { boundsOf(it).top }
        assertEquals(tops.sorted(), tops)
        assertTrue(arrowLeftOf("f") > arrowLeftOf("b"))
        assertTrue(arrowLeftOf("b") > arrowLeftOf("p"))
        assertEquals(arrowLeftOf("p"), arrowLeftOf("q"))
    }

    // The arrow that folds a topic, or where it would be, is the start of what its row shows.
    private fun ComposeUiTest.arrowLeftOf(id: String) = onNodeWithTag(outlineFoldTag(id)).getBoundsInRoot().left

    @Test
    fun clickSelectsTheTopicOfTheMap() = runComposeUiTest {
        val state = shownState()

        onNodeWithTag(outlineRowTag("c")).performClick()
        waitForIdle()

        assertEquals("c", state.selectedNode?.id)
    }

    @Test
    fun secondClickIsADoubleClick() = runComposeUiTest {
        shownState()

        onNodeWithTag(outlineRowTag("c")).performMouseInput {
            click()
            click()
        }
        waitForIdle()

        assertEquals("c", doubleClickedId)
    }

    @Test
    fun arrowFoldsTheTopicAndItsRowsGo() = runComposeUiTest {
        val state = shownState()

        onNodeWithTag(outlineFoldTag("b")).performClick()
        waitForIdle()

        assertTrue(state.tree.isFolded("b"))
        onNodeWithTag(outlineRowTag("f")).assertDoesNotExist()
        state.undo()
        waitForIdle()
        onNodeWithTag(outlineRowTag("f")).assertExists()
    }

    @Test
    fun changesOfTheMapShowInTheOutline() = runComposeUiTest {
        val state = shownState()

        state.select(state.tree.getNode("q"))
        state.addNode("New")
        waitForIdle()
        val added = assertNotNull(state.tree.getNode("q").children.singleOrNull())
        onNodeWithTag(outlineRowTag(added)).assertExists()

        state.select(state.tree.getNode("c"))
        state.removeNode()
        waitForIdle()
        onNodeWithTag(outlineRowTag("c")).assertDoesNotExist()
    }

    @Test
    fun selectedRowIsScrolledIntoView() = runComposeUiTest {
        val tree = tree().apply { (1..60).forEach { addNode("n$it", "q", "Topic $it") } }
        val state = shownState(tree)
        onNodeWithTag(outlineRowTag("n60")).assertDoesNotExist()

        state.select(state.tree.getNode("n60"))
        waitForIdle()

        onNodeWithTag(outlineRowTag("n60")).assertIsDisplayed()
    }

    @Test
    fun rightClickAsksForTheMenuOfTheTopic() = runComposeUiTest {
        val state = shownState()

        onNodeWithTag(outlineRowTag("b")).performMouseInput { rightClick(center) }
        waitForIdle()

        assertEquals("b", menuNodeId)
        assertEquals("b", state.selectedNode?.id)
        val row = boundsOf("b")
        val position = assertNotNull(menuPosition)
        assertFalse(position.y < row.top.value * density.density - 1)
    }

    @Test
    fun pointerOnTheMarksOfARowTellsOfItsNoteAndAttributes() = runComposeUiTest {
        val tree = tree().apply {
            updateNodeNote("c", "A note")
            setAttributes("c", listOf(NodeAttribute("Owner", "Ann")))
        }
        shownState(tree)

        onNodeWithTag(outlineNoteMarkTag("c")).performMouseInput { moveTo(center) }
        onNodeWithTag(outlineRowTag("p")).performMouseInput { moveTo(center) }
        onNodeWithTag(outlineAttributeMarkTag("c")).performMouseInput { moveTo(center) }
        onNodeWithTag(outlineRowTag("p")).performMouseInput { moveTo(center) }
        waitForIdle()

        assertEquals("c", noteHovers.first())
        assertEquals(null, noteHovers.last())
        assertEquals("c", attributeHovers.first())
        assertEquals(null, attributeHovers.last())
        onNodeWithTag(outlineNoteMarkTag("b")).assertDoesNotExist()
    }

    @Test
    fun markGoneWithThePointerOnItTellsItIsLeft() = runComposeUiTest {
        val tree = tree().apply { updateNodeNote("c", "A note") }
        val state = shownState(tree)
        onNodeWithTag(outlineNoteMarkTag("c")).performMouseInput { moveTo(center) }
        waitForIdle()

        state.select(state.tree.getNode("c"))
        state.removeNode()
        waitForIdle()

        assertEquals(listOf("c", null), noteHovers.distinct())
    }
}
