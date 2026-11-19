package com.corbettcode.mindmapview.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.corbettcode.mindmapview.library.data.DpOffset
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FloatingDragTest {
    private var blankDoubleClick: Offset? = null
    private var blankMenu: Offset? = null

    // The root with "a" and "b" below it, "c" below "a", and "f" floating below the root, shown on the screen.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(300f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("b", rootId, "B")
            addNode("c", "a", "C")
            addNode("f", rootId, "F")
            setFloating("f", DpOffset(Dp(0f), Dp(200f)))
        }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(900.dp, 700.dp).testTag(TAG)
                , onBlankDoubleClick = { blankDoubleClick = it }
                , onBlankMenu = { blankMenu = it }
            )
        }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    // Where the center of the node is on the screen, and a point that far from it.
    private fun ComposeUiTest.onScreen(
        state: MindMapState
        , id: String
        , dx: Float = 0f
        , dy: Float = 0f
    ): Offset {
        val path = state.tree.getNode(id).path
        val point = with(density) {
            Offset((path.centerX.dpVal + dx).dp.toPx(), (path.centerY.dpVal + dy).dp.toPx())
        }
        return point * state.scaleFactor + state.currentPoint
    }

    // Drags the node [id] to [to] on the screen and drops it, with Alt held down when [isAlt].
    private fun ComposeUiTest.drag(
        state: MindMapState
        , id: String
        , to: Offset
        , isAlt: Boolean = false
    ) {
        runOnIdle {
            state.onTouchDown(onScreen(state, id))
            state.onNodeDrag(to)
            state.onTouchUp({ _, _, _ -> }, isDetaching = isAlt)
        }
        waitForIdle()
    }

    @Test
    fun floatingTopicDroppedOnBlankMapStaysThere() = runComposeUiTest {
        val state = shownState()

        drag(state, "f", onScreen(state, "f", dx = 150f, dy = 40f))

        val offset = assertNotNull(state.tree.floatingOffsetOf("f"))
        assertEquals(150f, offset.x.dpVal, 0.5f)
        assertEquals(240f, offset.y.dpVal, 0.5f)
        state.undo()
        assertEquals(DpOffset(Dp(0f), Dp(200f)), state.tree.floatingOffsetOf("f"))
    }

    @Test
    fun floatingTopicDroppedOnATopicIsAttachedToIt() = runComposeUiTest {
        val state = shownState()

        drag(state, "f", onScreen(state, "b"))

        assertEquals("b", state.tree.getNode("f").parentId)
        assertFalse(state.tree.isFloating("f"))
    }

    @Test
    fun topicDroppedWithAltOnBlankMapFloats() = runComposeUiTest {
        val state = shownState()

        drag(state, "c", onScreen(state, "f", dx = 300f), isAlt = true)

        assertEquals(state.tree.getRootNode().id, state.tree.getNode("c").parentId)
        assertTrue(state.tree.isFloating("c"))
        state.undo()
        assertEquals("a", state.tree.getNode("c").parentId)
    }

    @Test
    fun topicDroppedWithoutAltIsOnlyReordered() = runComposeUiTest {
        val state = shownState()

        drag(state, "b", onScreen(state, "a", dy = -60f))

        assertNull(state.tree.floatingOffsetOf("b"))
        assertEquals(listOf("b", "a", "f"), state.tree.getRootNode().children)
    }

    @Test
    fun doubleClickOnBlankMapTellsWhere() = runComposeUiTest {
        val state = shownState()
        val blank = onScreen(state, "f", dx = 250f)

        onNodeWithTag(TAG).performMouseInput {
            click(blank)
            click(blank)
        }
        waitForIdle()

        val point = assertNotNull(blankDoubleClick)
        assertEquals(blank.x, point.x, 1f)
        assertEquals(blank.y, point.y, 1f)
    }

    @Test
    fun rightClickOnBlankMapAsksForItsMenuThere() = runComposeUiTest {
        val state = shownState()
        val blank = onScreen(state, "f", dx = 250f)

        onNodeWithTag(TAG).performMouseInput { rightClick(onScreen(state, "b")) }
        waitForIdle()
        assertNull(blankMenu, "a node has a menu of its own")

        onNodeWithTag(TAG).performMouseInput { rightClick(blank) }
        waitForIdle()
        val point = assertNotNull(blankMenu)
        assertEquals(blank.x, point.x, 1f)
        assertEquals(blank.y, point.y, 1f)
    }
}

private const val TAG = "map"
