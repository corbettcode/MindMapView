package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.priorityColor
import com.mindsync.library.layout.taskBadgeCenters
import com.mindsync.library.layout.taskBadges
import com.mindsync.library.layout.taskBadgesWidth
import com.mindsync.library.util.Dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskBadgeTest {
    private var clickedLink: String? = null

    // The root in the middle with "a" and "b" below it, shown on the screen. "b" is all a link.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply {
            addNode("a", getRootNode().id, "Apples")
            addNode("b", getRootNode().id, "<a href=\"https://example.com\">Bananas</a>")
        }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(800.dp, 600.dp).testTag(TAG)
                , onLinkClick = { _, url -> clickedLink = url }
            )
        }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun MindMapState.give(
        id: String
        , task: NodeTask
    ) {
        select(tree.getNode(id))
        task.priority?.let { setPriority(it) }
        task.progress?.let { setProgress(it) }
    }

    private fun MindMapState.widthOf(id: String): Float = (tree.getNode(id) as RectangleNodeData).path.width.dpVal

    // Where a point of the tree is on the screen.
    private fun MindMapState.onScreen(point: Offset): Offset = point * scaleFactor + currentPoint

    private fun ComposeUiTest.progressBadgeOf(
        state: MindMapState
        , id: String
    ): Offset {
        val node = state.tree.getNode(id)
        val badges = taskBadges(state.tree.taskOf(id))
        val content = state.mindMapManager.getMeasureTextSize()!!
            .measureContent(state.tree, node, state.attributeDisplay, state.noteDisplay)
        return with(density) { taskBadgeCenters(node, content, badges.size) }.last()
    }

    @Test
    fun nodeIsWiderByItsBadges() = runComposeUiTest {
        val state = shownState()
        val width = state.widthOf("a")

        state.give("a", NodeTask(priority = 1, progress = TaskProgress.HALF))
        waitForIdle()

        val badgesWidth = with(density) { taskBadgesWidth(2).toDp().value }
        assertEquals(width + badgesWidth, state.widthOf("a"), 0.5f)
    }

    @Test
    fun badgesAreDrawnInTheirColors() = runComposeUiTest {
        val state = shownState()

        state.give("a", NodeTask(priority = 1))
        waitForIdle()

        val pixels = assertNotNull(state.exportImage()).toPixelMap()
        val red = priorityColor(1)
        val isRedDrawn = (0 until pixels.height).any { y -> (0 until pixels.width).any { pixels[it, y] == red } }
        assertTrue(isRedDrawn, "the priority is drawn")
    }

    @Test
    fun clickOnTheProgressTakesItAStepOn() = runComposeUiTest {
        val state = shownState()
        state.give("a", NodeTask(priority = 2, progress = TaskProgress.QUARTER))
        state.select(null)
        waitForIdle()

        val badge = state.onScreen(progressBadgeOf(state, "a"))
        onNodeWithTag(TAG).performTouchInput { click(badge) }
        waitForIdle()

        assertEquals(TaskProgress.HALF, state.tree.taskOf("a").progress)
        assertEquals(null, state.selectedNode, "the click selects no node")
    }

    @Test
    fun clickOnTheTextSelectsTheNode() = runComposeUiTest {
        val state = shownState()
        state.give("a", NodeTask(progress = TaskProgress.QUARTER))
        state.select(null)
        waitForIdle()
        val node = state.tree.getNode("a")
        val rightOfText = with(density) {
            Offset(
                (node.path.centerX.dpVal + state.widthOf("a") / 2 - 12f).dp.toPx()
                , node.path.centerY.dpVal.dp.toPx()
            )
        }

        onNodeWithTag(TAG).performTouchInput { click(state.onScreen(rightOfText)) }
        waitForIdle()

        assertEquals("a", state.selectedNode?.id)
        assertEquals(TaskProgress.QUARTER, state.tree.taskOf("a").progress)
    }

    @Test
    fun textIsTypedWhereItIsDrawnAfterTheBadges() = runComposeUiTest {
        val state = shownState()
        state.give("a", NodeTask(priority = 1, progress = TaskProgress.DONE))
        waitForIdle()
        val node = state.tree.getNode("a")
        val content = state.mindMapManager.getMeasureTextSize()!!
            .measureContent(state.tree, node, state.attributeDisplay, state.noteDisplay)

        state.startTextEdit()
        waitForIdle()

        val bounds = onNodeWithTag(NODE_TEXT_EDITOR_TAG).getBoundsInRoot()
        val editorCenterX = with(density) { ((bounds.left + bounds.right) / 2).toPx() }
        val textCenterX = state.onScreen(
            Offset(with(density) { node.path.centerX.dpVal.dp.toPx() } + content.textShiftX, 0f)
        ).x
        assertTrue(abs(editorCenterX - textCenterX) < 2f, "the editor is at $editorCenterX, the text at $textCenterX")
        assertTrue(content.textShiftX > 0f)
    }

    @Test
    fun linkIsFoundInTheTextAfterTheBadges() = runComposeUiTest {
        val state = shownState()
        state.give("b", NodeTask(priority = 1, progress = TaskProgress.DONE))
        state.select(null)
        waitForIdle()
        val node = state.tree.getNode("b")
        val content = state.mindMapManager.getMeasureTextSize()!!
            .measureContent(state.tree, node, state.attributeDisplay, state.noteDisplay)
        // Just inside the right end of the text, which the badges moved right: without the move, past its end.
        val textEnd = with(density) {
            Offset(
                node.path.centerX.dpVal.dp.toPx() + content.textShiftX + content.text.size.width / 2f - 3f
                , node.path.centerY.dpVal.dp.toPx()
            )
        }

        onNodeWithTag(TAG).performTouchInput { click(state.onScreen(textEnd)) }
        waitForIdle()

        assertEquals("https://example.com", clickedLink)
    }
}

private const val TAG = "mindMap"
