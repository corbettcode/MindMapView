package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OutlineNode
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.OvalPath
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.data.Tree
import com.mindsync.library.data.shape
import com.mindsync.library.io.FreeMind
import com.mindsync.library.layout.MindMapLeftLayoutManager
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import com.mindsync.library.util.RichTextLink
import com.mindsync.library.util.RichTextSize
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MindMapTest {
    private val clicks = mutableListOf<NodeData<*>?>()
    private val doubleClicks = mutableListOf<String>()
    private val moves = mutableListOf<Pair<String, String>>()
    private val addChildClicks = mutableListOf<String>()
    private val addChildSides = mutableListOf<LayoutSide>()
    private val linkClicks = mutableListOf<Pair<String, String>>()
    private val arrowLinkMenus = mutableListOf<Pair<String, Offset>>()
    private val nodeMenus = mutableListOf<Pair<String, Offset>>()
    private val linkChanges = mutableListOf<Triple<String, String, Boolean>>()

    private fun ComposeUiTest.setMindMap(state: MindMapState) {
        setContent {
            MindMap(
                state = state,
                modifier = Modifier.size(800.dp, 600.dp).testTag(TAG),
                onNodeClick = { clicks.add(it) },
                onNodeDoubleClick = { doubleClicks.add(it.id) },
                onNodeMove = { _, target, parent -> moves.add(target.id to parent.id) },
                onNodeLink = { _, from, to, isLinked -> linkChanges.add(Triple(from.id, to.id, isLinked)) },
                onAddChildClick = { node, side ->
                    addChildClicks.add(node.id)
                    addChildSides.add(side)
                },
                onLinkClick = { node, url -> linkClicks.add(node.id to url) },
                onArrowLinkMenu = { link, position -> arrowLinkMenus.add(link.id to position) },
                onNodeMenu = { node, position -> nodeMenus.add(node.id to position) },
            )
        }
    }

    private fun ComposeUiTest.clickNode(node: NodeData<*>) {
        onNodeWithTag(TAG).performTouchInput { click(center(node)) }
        waitForIdle()
    }

    private fun ComposeUiTest.clickNodeWith(
        key: Key
        , node: NodeData<*>
    ) {
        onNodeWithTag(TAG).performKeyInput { keyDown(key) }
        onNodeWithTag(TAG).performMouseInput { click(center(node)) }
        onNodeWithTag(TAG).performKeyInput { keyUp(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.ctrlClickNode(node: NodeData<*>) = clickNodeWith(Key.CtrlLeft, node)

    private fun ComposeUiTest.shiftClickNode(node: NodeData<*>) = clickNodeWith(Key.ShiftLeft, node)

    // Drags with the mouse from [from] to [to] while Ctrl is held down.
    private fun ComposeUiTest.ctrlDrag(
        from: NodeData<*>,
        to: androidx.compose.ui.unit.Density.() -> Offset,
    ) {
        onNodeWithTag(TAG).performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag(TAG).performMouseInput {
            val start = center(from)
            val end = to()
            moveTo(start)
            press()
            (1..4).forEach { step -> moveTo(start + (end - start) * (step / 4f)) }
            release()
        }
        onNodeWithTag(TAG).performKeyInput { keyUp(Key.CtrlLeft) }
        waitForIdle()
    }

    private fun androidx.compose.ui.unit.Density.center(node: NodeData<*>): Offset {
        return Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
    }

    @Test
    fun rootIsCenteredWhenNoPositionIsGiven() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()

        val root = state.tree.getRootNode()
        assertEquals(Dp(400f), root.path.centerX)
        assertEquals(Dp(300f), root.path.centerY)
    }

    @Test
    fun rootStaysCenteredWhenTheSizeChanges() = runComposeUiTest {
        val state = MindMapState()
        var size by mutableStateOf(DpSize(800.dp, 600.dp))
        setContent { MindMap(state = state, modifier = Modifier.size(size).testTag(TAG)) }
        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()
        val childId = state.tree.getRootNode().children.single()
        val fromRoot = state.tree.getNode(childId).path.centerX - state.tree.getRootNode().path.centerX

        size = DpSize(1000.dp, 700.dp)
        waitForIdle()

        val root = state.tree.getRootNode()
        assertEquals(Dp(500f), root.path.centerX)
        assertEquals(Dp(350f), root.path.centerY)
        // The nodes below the root go along with it.
        assertEquals(fromRoot, state.tree.getNode(childId).path.centerX - root.path.centerX)
        assertEquals(Dp(350f), state.tree.getNode(childId).path.centerY)
    }

    @Test
    fun rootWithAGivenPositionStaysThereWhenTheSizeChanges() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        var size by mutableStateOf(DpSize(800.dp, 600.dp))
        setContent { MindMap(state = state, modifier = Modifier.size(size).testTag(TAG)) }
        waitForIdle()

        size = DpSize(1000.dp, 700.dp)
        waitForIdle()

        assertEquals(Dp(150f), state.tree.getRootNode().path.centerX)
        assertEquals(Dp(300f), state.tree.getRootNode().path.centerY)
    }

    @Test
    fun draggingTheRootMovesTheTreeAndIsNoLongerCentered() = runComposeUiTest {
        val state = MindMapState()
        var size by mutableStateOf(DpSize(800.dp, 600.dp))
        setContent {
            MindMap(
                state = state,
                modifier = Modifier.size(size).testTag(TAG),
                onNodeMove = { _, target, parent -> moves.add(target.id to parent.id) },
            )
        }
        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()
        val childId = state.tree.getRootNode().children.single()
        val child = state.tree.getNode(childId)
        val fromRoot = child.path.centerX - state.tree.getRootNode().path.centerX

        // The root is dropped on its child, which it is not attached to as another node would be.
        onNodeWithTag(TAG).performTouchInput { swipe(center(state.tree.getRootNode()), center(child), durationMillis = 200) }
        waitForIdle()

        val root = state.tree.getRootNode()
        assertEquals(child.path.centerX.dpVal, root.path.centerX.dpVal, 1f)
        assertEquals(child.path.centerY.dpVal, root.path.centerY.dpVal, 1f)
        assertEquals(fromRoot, state.tree.getNode(childId).path.centerX - root.path.centerX)
        assertEquals(root.path.centerY, state.tree.getNode(childId).path.centerY)
        assertTrue(moves.isEmpty())
        assertEquals(listOf(childId), root.children)
        assertNull(root.parentId)

        size = DpSize(1000.dp, 700.dp)
        waitForIdle()
        assertEquals(root.path, state.tree.getRootNode().path)
    }

    @Test
    fun rootDraggedTowardsTheTopGoesThereAndTheMindMapPansToTheNodesAboveIt() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        clickNode(state.tree.getRootNode())
        repeat(4) {
            state.addNode("child")
            waitForIdle()
        }
        val height = state.mindMapManager.measureHeight(state.tree.getRootNode())

        onNodeWithTag(TAG).performTouchInput { swipe(center(state.tree.getRootNode()), Offset(300.dp.toPx(), 20.dp.toPx()), durationMillis = 200) }
        waitForIdle()

        val root = state.tree.getRootNode()
        assertEquals(300f, root.path.centerX.dpVal, 1f)
        assertEquals(20f, root.path.centerY.dpVal, 1f)
        val top = root.children.minOf { state.tree.getNode(it).path.centerY.dpVal }
        assertTrue(top < 0f)

        // The root stays there as the tree is arranged again.
        state.animateTreeChange()
        waitForIdle()
        assertEquals(root.path, state.tree.getRootNode().path)

        // The mind map pans down as far as the nodes above its top need to be seen, and no further.
        state.onTransform(Offset(0f, 10000f), 1f)
        val reach = with(density) { (height.dpVal / 2 + 50f - root.path.centerY.dpVal).dp.toPx() }
        assertEquals(0f, state.currentPoint.x)
        assertEquals(reach, state.currentPoint.y, 0.5f)
        assertTrue(with(density) { top.dp.toPx() } + state.currentPoint.y > 0f)
    }

    @Test
    fun rootIsALightGrayOvalWithBlackText() {
        val root: NodeData<*> = Tree<Any>(Dp(150f), Dp(300f)).getRootNode()
        assertIs<OvalNodeData>(root)
        assertEquals("🌱" to "root", DescriptionEmoji.split(root.description))
        assertEquals(Color.LightGray, root.color)
        assertEquals(Color.Black, root.textColor)
    }

    @Test
    fun clickSelectsNodeAndEmptySpaceClearsSelection() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        assertIs<OvalNodeData>(state.selectedNode)
        assertIs<OvalNodeData>(clicks.last())

        onNodeWithTag(TAG).performTouchInput { click(Offset(700.dp.toPx(), 50.dp.toPx())) }
        waitForIdle()
        assertNull(state.selectedNode)
        assertNull(clicks.last())
    }

    @Test
    fun addEditAndRemoveNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<RectangleNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertTrue(child.path.leftX().dpVal > (root.path.centerX + root.path.radiusX).dpVal)
        assertEquals(root.path.centerY.dpVal, child.path.centerY.dpVal, 0.01f)

        clickNode(child)
        assertEquals(child.id, state.selectedNode?.id)
        state.editNodeText("a much longer description")
        waitForIdle()
        val edited = assertIs<RectangleNodeData>(state.tree.getNode(child.id))
        assertEquals("a much longer description", edited.description)
        assertTrue(edited.path.width.dpVal > child.path.width.dpVal)

        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())
        assertNull(state.selectedNode)
    }

    @Test
    fun copiedNodeIsPastedWithTheNodesBelowItAsTheyWereWhenCopied() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("parent", rootId, "parent", NodeShape.OVAL, Color.Red, Color.White, note = "a note")
        state.tree.addNode("child", "parent", "child")
        state.tree.setAttributes("child", listOf(NodeAttribute("name", "value")))
        state.tree.addNode("other", rootId, "other")
        state.tree.addArrowLink(ArrowLink("inside", "child", "parent", Color.Blue, hasStartArrow = true))
        state.tree.addArrowLink(ArrowLink("outside", "child", "other"))
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()

        assertFalse(state.hasCopiedNode)
        clickNode(state.tree.getNode("parent"))
        state.copyNode()
        assertTrue(state.hasCopiedNode)
        state.editNodeText("changed after copying")

        clickNode(state.tree.getNode("other"))
        state.pasteNode()
        state.pasteNode()
        waitForIdle()

        val pasted = state.tree.getNode("other").children.map { state.tree.getNode(it) }
        assertEquals(2, pasted.size)
        assertNotEquals(pasted[0].id, pasted[1].id)
        pasted.forEach { node ->
            assertIs<OvalNodeData>(node)
            assertEquals("parent", node.description)
            assertEquals("a note", node.note)
            assertEquals(Color.Red, node.color)
            assertEquals(Color.White, node.textColor)
            val child = state.tree.getNode(node.children.single())
            assertEquals("child", child.description)
            assertEquals(listOf(NodeAttribute("name", "value")), state.tree.attributesOf(child.id))
            assertTrue(node.path.centerX.dpVal > state.tree.getNode("other").path.centerX.dpVal)
        }
        // Only the arrow link between copied nodes is pasted, once for each paste, between the pasted nodes.
        val pastedLinks = state.arrowLinks.filter { it.id != "inside" && it.id != "outside" }
        assertEquals(
            pasted.map { state.tree.getNode(it.children.single()).id to it.id }.toSet()
            , pastedLinks.map { it.sourceId to it.targetId }.toSet()
        )
        assertEquals(2, pastedLinks.size)
        pastedLinks.forEach {
            assertEquals(Color.Blue, it.color)
            assertTrue(it.hasStartArrow)
        }
    }

    @Test
    fun outlineIsAddedAsNodesBelowTheSelectedNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        clickNode(state.tree.getRootNode())

        state.addNodes(
            listOf(OutlineNode("first", listOf(OutlineNode("below"))), OutlineNode("second"))
            , NodeShape.OVAL
            , Color.Red
            , Color.White
            , LayoutSide.LEFT
        )
        waitForIdle()

        val root = state.tree.getRootNode()
        val (first, second) = root.children.map { state.tree.getNode(it) }
        assertEquals("first", first.description)
        assertEquals("second", second.description)
        val below = state.tree.getNode(first.children.single())
        assertEquals("below", below.description)
        listOf(first, second, below).forEach { node ->
            assertIs<OvalNodeData>(node)
            assertEquals(Color.Red, node.color)
            assertEquals(Color.White, node.textColor)
            assertEquals(LayoutSide.LEFT, state.tree.sideOf(node.id))
            assertTrue(node.path.centerX.dpVal < root.path.centerX.dpVal)
        }
    }

    @Test
    fun ctrlClickAddsNodesToTheSelectionAndTakesThemOut() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        listOf("a", "b", "c").forEach { state.tree.addNode(it, rootId, it) }
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()
        fun selectedIds() = state.selectedNodes.map { it.id }

        clickNode(state.tree.getNode("a"))
        ctrlClickNode(state.tree.getNode("b"))
        ctrlClickNode(state.tree.getNode("c"))
        assertEquals(listOf("a", "b", "c"), selectedIds())
        assertEquals("c", state.selectedNode?.id)

        ctrlClickNode(state.tree.getNode("c"))
        assertEquals(listOf("a", "b"), selectedIds())
        assertEquals("b", state.selectedNode?.id)
        ctrlClickNode(state.tree.getNode("a"))
        assertEquals(listOf("b"), selectedIds())

        // A click without Ctrl selects the node alone again.
        ctrlClickNode(state.tree.getNode("c"))
        clickNode(state.tree.getNode("a"))
        assertEquals(listOf("a"), selectedIds())
    }

    @Test
    fun shiftClickSelectsTheRangeOfNodesFromTheNodeClickedBefore() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(400f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        listOf("a", "b", "c", "d").forEach { state.tree.addNode(it, rootId, it) }
        state.tree.addNode("b1", "b", "b1")
        state.tree.addNode("left", rootId, "left", side = LayoutSide.LEFT)
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()
        fun selectedIds() = state.selectedNodes.map { it.id }

        // Of siblings, the range is the siblings between them, towards the node clicked last.
        clickNode(state.tree.getNode("d"))
        shiftClickNode(state.tree.getNode("b"))
        assertEquals(listOf("d", "c", "b"), selectedIds())
        assertEquals("b", state.selectedNode?.id)

        // The range starts at the same node again.
        shiftClickNode(state.tree.getNode("a"))
        assertEquals(listOf("d", "c", "b", "a"), selectedIds())

        // Of other nodes, it is the nodes shown between them.
        clickNode(state.tree.getNode("b1"))
        shiftClickNode(state.tree.getNode("left"))
        assertEquals(listOf("b1", "c", "d", "left"), selectedIds())

        // A node added with Ctrl is where the next range starts.
        clickNode(state.tree.getNode("a"))
        ctrlClickNode(state.tree.getNode("c"))
        shiftClickNode(state.tree.getNode("d"))
        assertEquals(listOf("c", "d"), selectedIds())
    }

    @Test
    fun selectAllSelectsTheNodesThatAreShownButTheRoot() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(400f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("a", rootId, "a")
        state.tree.addNode("a1", "a", "a1")
        state.tree.addNode("b", rootId, "b")
        state.tree.addNode("b1", "b", "b1")
        state.tree.addNode("left", rootId, "left", side = LayoutSide.LEFT)
        state.tree.setFolded("b", true)
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()

        state.selectAll()
        assertEquals(listOf("a", "a1", "b", "left"), state.selectedNodes.map { it.id })
        assertEquals("left", state.selectedNode?.id)

        // All of them can be removed together.
        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())

        // With only the root left, the selection is not changed.
        clickNode(state.tree.getRootNode())
        state.selectAll()
        assertEquals(listOf(rootId), state.selectedNodes.map { it.id })
    }

    @Test
    fun selectedNodesAreCopiedRemovedAndFoldedTogether() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("a", rootId, "a")
        state.tree.addNode("a1", "a", "a1")
        state.tree.addNode("b", rootId, "b")
        state.tree.addNode("b1", "b", "b1")
        state.tree.addNode("target", rootId, "target")
        state.tree.addArrowLink(ArrowLink("between", "a1", "b1"))
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()

        // A node below another selected one is part of its branch already.
        clickNode(state.tree.getNode("b"))
        ctrlClickNode(state.tree.getNode("a"))
        ctrlClickNode(state.tree.getNode("a1"))
        assertEquals(listOf("a", "b"), state.selectedBranches.map { it.id })

        state.toggleFold()
        assertTrue(state.tree.isFolded("a") && state.tree.isFolded("b"))
        state.toggleFold()
        assertFalse(state.tree.isFolded("a") || state.tree.isFolded("b"))

        state.copyNode()
        clickNode(state.tree.getNode("target"))
        state.pasteNode()
        waitForIdle()
        val pasted = state.tree.getNode("target").children.map { state.tree.getNode(it) }
        assertEquals(listOf("a", "b"), pasted.map { it.description })
        val pastedLink = state.arrowLinks.single { it.id != "between" }
        assertEquals(pasted.map { it.children.single() }, listOf(pastedLink.sourceId, pastedLink.targetId))

        clickNode(state.tree.getNode("a"))
        ctrlClickNode(state.tree.getNode("b"))
        state.removeNode()
        waitForIdle()
        assertEquals(listOf("target"), state.tree.getRootNode().children)
        assertTrue(state.selectedNodes.isEmpty())
    }

    @Test
    fun cutNodeIsRemovedAndPastedElsewhere() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("cut", rootId, "cut")
        state.tree.addNode("below", "cut", "below")
        state.tree.addNode("other", rootId, "other")
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()

        clickNode(state.tree.getRootNode())
        state.cutNode()
        assertFalse(state.hasCopiedNode)
        assertEquals(listOf("cut", "other"), state.tree.getRootNode().children)

        clickNode(state.tree.getNode("cut"))
        state.cutNode()
        waitForIdle()
        assertEquals(listOf("other"), state.tree.getRootNode().children)
        assertNull(state.selectedNode)

        clickNode(state.tree.getNode("other"))
        state.pasteNode()
        waitForIdle()
        val pasted = state.tree.getNode(state.tree.getNode("other").children.single())
        assertEquals("cut", pasted.description)
        assertEquals("below", state.tree.getNode(pasted.children.single()).description)
    }

    @Test
    fun addEditAndRemoveStadiumNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", NodeShape.STADIUM)
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<StadiumNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertTrue(child.path.leftX().dpVal > (root.path.centerX + root.path.radiusX).dpVal)
        assertEquals(root.path.centerY.dpVal, child.path.centerY.dpVal, 0.01f)
        assertEquals(child.path.length.dpVal + child.path.radius.dpVal * 2, child.path.width().dpVal, 0.01f)
        assertEquals(child.path.radius.dpVal * 2, child.path.height().dpVal, 0.01f)

        clickNode(child)
        assertEquals(child.id, state.selectedNode?.id)
        state.editNodeText("a much longer description")
        waitForIdle()
        val edited = assertIs<StadiumNodeData>(state.tree.getNode(child.id))
        assertEquals("a much longer description", edited.description)
        assertTrue(edited.path.length.dpVal > child.path.length.dpVal)
        assertEquals(child.path.radius.dpVal, edited.path.radius.dpVal, 0.01f)

        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())
        assertNull(state.selectedNode)
    }

    @Test
    fun addDragAndRemoveCircleNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.CIRCLE)
        waitForIdle()

        val root = state.tree.getRootNode()
        val (firstId, secondId) = root.children
        val first = state.tree.getNode(firstId)
        val second = assertIs<CircleNodeData>(state.tree.getNode(secondId))
        assertEquals(root.id, second.parentId)
        assertTrue((second.path.centerX - second.path.radius).dpVal > (root.path.centerX + root.path.radiusX).dpVal)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()
        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(firstId, state.tree.getNode(secondId).parentId)

        clickNode(state.tree.getNode(secondId))
        assertEquals(secondId, state.selectedNode?.id)
        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getNode(firstId).children.isEmpty())

        clickNode(state.tree.getRootNode())
        state.removeNode()
        waitForIdle()
        assertEquals(root.id, state.tree.getRootNode().id)
    }

    @Test
    fun nodeColorDefaultsToItsShapeAndCanBeChanged() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        assertEquals(Color.LightGray, root.color)

        clickNode(root)
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM)
        waitForIdle()
        state.addNode("third", NodeShape.RECTANGLE, Color.Red)
        waitForIdle()

        val (firstId, secondId, thirdId) = state.tree.getRootNode().children
        assertEquals(NodeShape.RECTANGLE.defaultColor, state.tree.getNode(firstId).color)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)
        assertEquals(Color.Red, state.tree.getNode(thirdId).color)

        clickNode(state.tree.getNode(firstId))
        state.editNodeColor(Color.Blue)
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)
        assertEquals(Color.Blue, state.selectedNode?.color)
        state.editNodeText("renamed")
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)

        // Moving a node deeper into the tree keeps its color.
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        onNodeWithTag(TAG).performTouchInput { swipe(center(first), center(second), durationMillis = 200) }
        waitForIdle()
        assertEquals(secondId, state.tree.getNode(firstId).parentId)
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)
    }

    @Test
    fun nodeTextColorDefaultsToItsShapeAndCanBeChanged() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        assertEquals(Color.Black, root.textColor)

        clickNode(root)
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM, textColor = Color.Red)
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        assertEquals(NodeShape.RECTANGLE.defaultTextColor, state.tree.getNode(firstId).textColor)
        assertEquals(Color.Red, state.tree.getNode(secondId).textColor)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)

        clickNode(state.tree.getNode(firstId))
        state.editNodeTextColor(Color.Blue)
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).textColor)
        assertEquals(Color.Blue, state.selectedNode?.textColor)
        assertEquals(NodeShape.RECTANGLE.defaultColor, state.tree.getNode(firstId).color)
        state.editNodeText("renamed")
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).textColor)
    }

    @Test
    fun doubleClickOnNodeIsReportedOnce() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        clickNode(root)
        state.addNode("child")
        waitForIdle()
        val child = state.tree.getNode(state.tree.getRootNode().children.single())

        // Single clicks, clicks on different nodes and slow repeat clicks are not double clicks.
        mainClock.advanceTimeBy(1_000)
        clickNode(root)
        clickNode(child)
        mainClock.advanceTimeBy(1_000)
        clickNode(child)
        assertTrue(doubleClicks.isEmpty())

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG).performTouchInput { doubleClick(center(child)) }
        waitForIdle()
        assertEquals(listOf(child.id), doubleClicks)
        assertEquals(child.id, state.selectedNode?.id)

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG).performTouchInput { doubleClick(Offset(700.dp.toPx(), 50.dp.toPx())) }
        waitForIdle()
        assertEquals(listOf(child.id), doubleClicks)
    }

    @Test
    fun addButtonAtTheStartOfTheLinesAddsAChildToTheirSourceNode() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        val root = state.tree.getRootNode()

        // A leaf node has an add button too.
        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(root)) }
        waitForIdle()
        assertEquals(listOf(root.id), addChildClicks)
        assertEquals(root.id, state.selectedNode?.id)
        addChildClicks.clear()

        state.addNode("first")
        waitForIdle()
        val first = state.tree.getNode(state.tree.getRootNode().children.single())
        clickNode(first)
        assertEquals(first.id, state.selectedNode?.id)

        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(state.tree.getRootNode())) }
        waitForIdle()
        assertEquals(listOf(root.id), addChildClicks)
        assertEquals(root.id, state.selectedNode?.id)

        state.addNode("second")
        waitForIdle()
        assertEquals(2, state.tree.getRootNode().children.size)
    }

    @Test
    fun leftLayoutPlacesChildrenToTheLeftOfTheirParent() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        assertEquals(LayoutSide.RIGHT, state.layoutSide)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        val firstId = state.tree.getRootNode().children.single()
        clickNode(state.tree.getNode(firstId))
        state.addNode("second")
        waitForIdle()
        val secondId = state.tree.getNode(firstId).children.single()
        assertTrue(state.tree.getNode(firstId).path.centerX.dpVal > state.tree.getRootNode().path.centerX.dpVal)

        state.layoutSide = LayoutSide.LEFT
        waitForIdle()
        val root = state.tree.getRootNode()
        val first = state.tree.getNode(firstId) as RectangleNodeData
        val second = state.tree.getNode(secondId) as RectangleNodeData
        assertEquals(Dp(400f), root.path.centerX)
        assertEquals(root.path.centerY, first.path.centerY)
        assertEquals((root.path.centerX - root.path.radiusX).dpVal - 50f, first.path.rightX().dpVal, 0.01f)
        assertEquals(first.path.leftX().dpVal - 50f, second.path.rightX().dpVal, 0.01f)

        // The add button moves to the left edge with the lines.
        clickNode(second)
        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(first, LayoutSide.LEFT)) }
        waitForIdle()
        assertEquals(listOf(first.id), addChildClicks)
        assertEquals(first.id, state.selectedNode?.id)

        state.layoutSide = LayoutSide.RIGHT
        waitForIdle()
        assertTrue(state.tree.getNode(firstId).path.centerX.dpVal > state.tree.getRootNode().path.centerX.dpVal)
    }

    @Test
    fun rootHasASetOfChildrenOnEachSide() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()

        // The root has an add button on each side, which reports the side it is on.
        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(state.tree.getRootNode(), LayoutSide.LEFT)) }
        waitForIdle()
        assertEquals(listOf(LayoutSide.LEFT), addChildSides)
        state.addNode("left", side = LayoutSide.LEFT)
        waitForIdle()
        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(state.tree.getRootNode(), LayoutSide.RIGHT)) }
        waitForIdle()
        assertEquals(listOf(LayoutSide.LEFT, LayoutSide.RIGHT), addChildSides)
        state.addNode("right")
        state.addNode("right too")
        waitForIdle()

        val root = state.tree.getRootNode()
        val (leftId, rightId, rightTooId) = root.children
        val left = state.tree.getNode(leftId) as RectangleNodeData
        val right = state.tree.getNode(rightId) as RectangleNodeData
        val rightToo = state.tree.getNode(rightTooId) as RectangleNodeData
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(leftId))
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(rightId))
        assertEquals((root.path.centerX - root.path.radiusX).dpVal - 50f, left.path.rightX().dpVal, 0.01f)
        assertEquals((root.path.centerX + root.path.radiusX).dpVal + 50f, right.path.leftX().dpVal, 0.01f)
        // Each side is centered on the root on its own.
        assertEquals(root.path.centerY, left.path.centerY)
        assertEquals(root.path.centerY.dpVal, (right.path.centerY.dpVal + rightToo.path.centerY.dpVal) / 2, 0.01f)

        // A node below a child of the root is on that child's side, whatever side is asked for.
        clickNode(left)
        state.addNode("below left", side = LayoutSide.RIGHT)
        waitForIdle()
        val belowLeft = state.tree.getNode(state.tree.getNode(leftId).children.single()) as RectangleNodeData
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(belowLeft.id))
        assertTrue(belowLeft.path.rightX().dpVal < (state.tree.getNode(leftId) as RectangleNodeData).path.leftX().dpVal)

        // Its add button is on the left, and the sides survive a FreeMind round trip.
        addChildClicks.clear()
        onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(belowLeft, LayoutSide.LEFT)) }
        waitForIdle()
        assertEquals(listOf(belowLeft.id), addChildClicks)
        val read = FreeMind.read(FreeMind.write(state.tree))
        assertEquals(LayoutSide.LEFT, read.sideOf(belowLeft.id))
        assertEquals(LayoutSide.RIGHT, read.sideOf(rightId))
    }

    @Test
    fun draggingANodeOntoTheLeftOfTheRootMovesItToTheLeftSide() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        val firstId = state.tree.getRootNode().children.single()
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(firstId))

        onNodeWithTag(TAG).performTouchInput {
            val root = state.tree.getRootNode()
            swipe(center(state.tree.getNode(firstId)), center(root) - Offset(20.dp.toPx(), 0f), durationMillis = 200)
        }
        waitForIdle()

        assertEquals(listOf(firstId), state.tree.getRootNode().children)
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(firstId))
        assertTrue(state.tree.getNode(firstId).path.centerX.dpVal < state.tree.getRootNode().path.centerX.dpVal)
    }

    @Test
    fun movingANodeToTheOtherSideMovesItsBranch() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        state.addNode("second")
        waitForIdle()
        val (firstId, secondId) = state.tree.getRootNode().children
        clickNode(state.tree.getNode(firstId))
        state.addNode("below first")
        waitForIdle()
        val belowFirstId = state.tree.getNode(firstId).children.single()

        // Nothing is moved for the root.
        clickNode(state.tree.getRootNode())
        state.moveNodeToOtherSide()
        waitForIdle()
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(firstId))
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(secondId))

        // A node below a child of the root takes that child, and the nodes below it, along.
        clickNode(state.tree.getNode(belowFirstId))
        state.moveNodeToOtherSide()
        waitForIdle()
        val root = state.tree.getRootNode()
        val first = state.tree.getNode(firstId) as RectangleNodeData
        val belowFirst = state.tree.getNode(belowFirstId) as RectangleNodeData
        assertEquals(listOf(firstId, secondId), root.children)
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(firstId))
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(belowFirstId))
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(secondId))
        assertEquals((root.path.centerX - root.path.radiusX).dpVal - 50f, first.path.rightX().dpVal, 0.01f)
        assertTrue(belowFirst.path.rightX().dpVal < first.path.leftX().dpVal)

        // Moving it again brings it back.
        clickNode(first)
        state.moveNodeToOtherSide()
        waitForIdle()
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf(belowFirstId))
        assertTrue(state.tree.getNode(firstId).path.centerX.dpVal > state.tree.getRootNode().path.centerX.dpVal)
    }

    @Test
    fun movingSelectedNodesMovesTheirBranchesToTheOtherSideOfTheLastSelectedOne() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(400f), Dp(300f)))
        setMindMap(state)
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("a", rootId, "a")
        state.tree.addNode("a1", "a", "a1")
        state.tree.addNode("b", rootId, "b")
        state.tree.addNode("c", rootId, "c", side = LayoutSide.LEFT)
        state.tree.addNode("d", rootId, "d")
        state.setTree(state.tree)
        state.animateTreeChange()
        waitForIdle()

        // Two nodes of one branch move it once, and a branch on the side they go to already stays there.
        clickNode(state.tree.getNode("c"))
        ctrlClickNode(state.tree.getNode("a1"))
        ctrlClickNode(state.tree.getNode("a"))
        ctrlClickNode(state.tree.getNode("b"))
        state.moveNodeToOtherSide()
        waitForIdle()
        listOf("a", "a1", "b", "c").forEach { assertEquals(LayoutSide.LEFT, state.tree.sideOf(it), it) }
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf("d"))

        // Nothing is moved while the root is the node selected last.
        ctrlClickNode(state.tree.getRootNode())
        state.moveNodeToOtherSide()
        waitForIdle()
        assertEquals(LayoutSide.LEFT, state.tree.sideOf("a"))
    }

    @Test
    fun leftLayoutMovesTheRootRightUntilTheTreeFits() {
        val tree = Tree<Any>(Dp(60f), Dp(300f))
        tree.addNode("first", tree.getRootNode().id, "first", NodeShape.RECTANGLE, side = LayoutSide.LEFT)
        val first = tree.getNode("first") as RectangleNodeData
        tree.setNode("first", first.copy(path = first.path.copy(width = Dp(80f), height = Dp(40f))))

        MindMapLeftLayoutManager().arrangeNode(tree)

        val arranged = tree.getNode("first") as RectangleNodeData
        assertEquals(50f, arranged.path.leftX().dpVal, 0.01f)
        assertEquals(arranged.path.rightX().dpVal + 50f, tree.getRootNode().path.let { it.centerX - it.radiusX }.dpVal, 0.01f)
    }

    @Test
    fun linesFromTheRootEachHaveTheirOwnColor() {
        val drawInfo = DrawInfo()
        val colors = drawInfo.rootLineColors.indices.map { rootLineColor(drawInfo, it) }

        assertEquals(colors.size, colors.toSet().size)
        assertFalse(drawInfo.lineColor in colors)
        assertFalse(drawInfo.darkModeLineColor in colors)
        // With more children than colors, the colors start over.
        assertEquals(colors.first(), rootLineColor(drawInfo, colors.size))
    }

    @Test
    fun emojiIsKeptInFrontOfTheDescription() {
        val withEmoji = DescriptionEmoji.prepend("⭐", "<p><b>some</b> text</p>")
        assertEquals("<p>⭐ <b>some</b> text</p>", withEmoji)
        assertEquals("⭐ some text", RichText.parse(withEmoji).text)
        assertEquals("⭐" to "<p><b>some</b> text</p>", DescriptionEmoji.split(withEmoji))

        // Picking another emoji replaces the one that is there, and picking none removes it.
        assertEquals("<p>❤️ <b>some</b> text</p>", DescriptionEmoji.prepend("❤️", withEmoji))
        assertEquals("<p><b>some</b> text</p>", DescriptionEmoji.prepend(null, withEmoji))

        assertEquals(null to "<p>some text</p>", DescriptionEmoji.split("<p>some text</p>"))
        assertEquals("🚀 plain", DescriptionEmoji.prepend("🚀", "plain"))
        assertEquals("🚀" to "plain", DescriptionEmoji.split("🚀 plain"))
        assertEquals("<p>🚀</p><ul><li>item</li></ul>", DescriptionEmoji.prepend("🚀", "<ul><li>item</li></ul>"))
        assertEquals("🚀" to "<ul><li>item</li></ul>", DescriptionEmoji.split("<p>🚀</p><ul><li>item</li></ul>"))

        // The emoji survives a FreeMind round trip.
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        tree.addNode("a", tree.getRootNode().id, DescriptionEmoji.prepend("⭐", "<p>plain</p>"))
        tree.addNode("b", tree.getRootNode().id, withEmoji)
        val read = FreeMind.read(FreeMind.write(tree))
        assertEquals("⭐" to "plain", DescriptionEmoji.split(read.getNode("a").description))
        assertEquals("⭐" to "<p><b>some</b> text</p>", DescriptionEmoji.split(read.getNode("b").description))
    }

    @Test
    fun descriptionMarkupIsParsedAsRichText() {
        val richText = RichText.parse("a <b>bold</b> &amp; <i>italic</i> word")

        assertEquals("a bold & italic word", richText.text)
        assertTrue(richText.spanStyles.any { it.item.fontWeight == FontWeight.Bold && richText.text.substring(it.start, it.end) == "bold" })
        assertTrue(richText.spanStyles.any { it.item.fontStyle == FontStyle.Italic && richText.text.substring(it.start, it.end) == "italic" })
    }

    private fun AnnotatedString.sizeOf(word: String): RichTextSize {
        val start = text.indexOf(word)
        val fontSize = spanStyles
            .lastOrNull { it.start <= start && it.end >= start + word.length && it.item.fontSize != TextUnit.Unspecified }
            ?.item?.fontSize ?: TextUnit.Unspecified
        return RichTextSize.of(fontSize)
    }

    @Test
    fun richTextSizeIsTheOneNearestAFontSize() {
        assertEquals(RichTextSize.NORMAL, RichTextSize.of(TextUnit.Unspecified))
        assertEquals(RichTextSize.SMALL, RichTextSize.of(0.8f.em))
        assertEquals(RichTextSize.NORMAL, RichTextSize.of(1f.em))
        assertEquals(RichTextSize.LARGE, RichTextSize.of(1.4f.em))
        // A size in a description is read as CSS pixels, of which a normal size has 16.
        assertEquals(RichTextSize.SMALL, RichTextSize.of(12.8f.sp))
        assertEquals(RichTextSize.NORMAL, RichTextSize.of(16f.sp))
        assertEquals(RichTextSize.LARGE, RichTextSize.of(22.4f.sp))
    }

    @Test
    fun descriptionFontSizesAreRelativeToTheNodeText() {
        val richText = RichText.parse(
            "<small>little</small> plain <span style=\"font-size: 1.4em;\">big</span> <span style=\"font-size: 16px;\">same</span>",
        )

        assertEquals("little plain big same", richText.text)
        assertTrue(richText.spanStyles.none { it.item.fontSize.isSp })
        assertEquals(RichTextSize.SMALL, richText.sizeOf("little"))
        assertEquals(RichTextSize.NORMAL, richText.sizeOf("plain"))
        assertEquals(RichTextSize.LARGE, richText.sizeOf("big"))
        assertEquals(RichTextSize.NORMAL, richText.sizeOf("same"))
    }

    @Test
    fun sizesAppliedInAnEditorAreKeptInTheDescription() {
        val editor = RichTextState().setHtml("little plain big")
        editor.addSpanStyle(RichTextSize.SMALL.spanStyle, TextRange(0, 6))
        editor.addSpanStyle(RichTextSize.LARGE.spanStyle, TextRange(13, 16))

        val richText = RichText.parse(editor.toHtml())
        assertEquals(RichTextSize.SMALL, richText.sizeOf("little"))
        assertEquals(RichTextSize.NORMAL, richText.sizeOf("plain"))
        assertEquals(RichTextSize.LARGE, richText.sizeOf("big"))

        // Opened in an editor again, the sizes are still the ones that were chosen.
        val reopened = RichTextState().setHtml(editor.toHtml()).annotatedString
        assertEquals(RichTextSize.SMALL, reopened.sizeOf("little"))
        assertEquals(RichTextSize.LARGE, reopened.sizeOf("big"))
    }

    @Test
    fun nodeGrowsAndShrinksWithTheSizeOfItsText() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        listOf("<small>some text</small>", "some text", "<span style=\"font-size: 1.4em;\">some text</span>").forEach {
            state.addNode(it)
            waitForIdle()
        }

        val (small, normal, large) = state.tree.getRootNode().children.map {
            assertIs<RectangleNodeData>(state.tree.getNode(it)).path
        }
        val padding = DrawInfo().padding.dpVal
        assertEquals((normal.width.dpVal - padding) * 0.8f, small.width.dpVal - padding, 1.5f)
        assertEquals((normal.width.dpVal - padding) * 1.4f, large.width.dpVal - padding, 1.5f)
        // A line is never lower than one of normal text, so small text only makes a node narrower.
        assertEquals(normal.height.dpVal, small.height.dpVal, 0.01f)
        assertTrue(large.height.dpVal > normal.height.dpVal)
    }

    @Test
    fun freeMindRoundTripKeepsTextSizes() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val description = "<p><small>little</small> and <span style=\"font-size: 1.4em;\">big</span></p>"
        tree.addNode("a", tree.getRootNode().id, description)

        val read = FreeMind.read(FreeMind.write(tree))
        assertEquals(description, read.getNode("a").description)
    }

    @Test
    fun nodeIsSizedByItsRenderedTextNotItsMarkup() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("some text")
        waitForIdle()
        state.addNode("<u>some text</u>")
        waitForIdle()
        state.addNode("&lt;u&gt;some text&lt;/u&gt;")
        waitForIdle()

        val (plain, rich, escaped) = state.tree.getRootNode().children.map {
            assertIs<RectangleNodeData>(state.tree.getNode(it)).path
        }
        assertEquals(plain.width.dpVal, rich.width.dpVal, 0.01f)
        assertTrue(escaped.width.dpVal > rich.width.dpVal)
        assertEquals("<u>some text</u>", state.tree.getNode(state.tree.getRootNode().children[1]).description)
    }

    @Test
    fun freeMindRoundTripKeepsStructureShapesColorsAndText() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.updateNodeColor(rootId, Color.Blue)
        tree.addNode("a", rootId, "plain & <simple>".replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
        tree.addNode("b", rootId, "<p>some <b>bold</b><br>text</p>", NodeShape.STADIUM, Color.Red, Color.White)
        tree.addNode("c", "b", "<p>leaf</p>", NodeShape.CIRCLE)
        tree.addNode("d", "b", "\"quoted\"")

        val written = FreeMind.write(tree)
        assertTrue(written.startsWith("<map version=\"1.0.1\">"))
        assertTrue("TEXT=\"plain &amp; &lt;simple&gt;\"" in written)
        assertTrue("TEXT=\"leaf\"" in written)
        assertTrue("<richcontent TYPE=\"NODE\"><html><head></head><body><p>some <b>bold</b><br/>text</p></body></html></richcontent>" in written)

        val read = FreeMind.read(written)
        val root = read.getRootNode()
        assertEquals("🌱 root", root.description)
        assertEquals(Color.Blue, root.color)
        assertEquals(listOf("a", "b"), root.children)

        val a = assertIs<RectangleNodeData>(read.getNode("a"))
        assertEquals(rootId, a.parentId)
        assertEquals("plain &amp; &lt;simple&gt;", a.description)
        assertEquals(NodeShape.RECTANGLE.defaultColor, a.color)

        val b = assertIs<StadiumNodeData>(read.getNode("b"))
        assertEquals("<p>some <b>bold</b><br/>text</p>", b.description)
        assertEquals(Color.Red, b.color)
        assertEquals(Color.White, b.textColor)
        assertEquals(listOf("c", "d"), b.children)

        val c = assertIs<CircleNodeData>(read.getNode("c"))
        assertEquals("b", c.parentId)
        assertEquals("leaf", c.description)
        assertEquals("&quot;quoted&quot;".replace("&quot;", "\""), read.getNode("d").description)

        assertEquals(written, FreeMind.write(read))
    }

    @Test
    fun freeMindFileWrittenByFreeMindIsRead() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map version="1.0.1">
            <!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->
            <node CREATED="1" ID="ID_1" MODIFIED="2" TEXT="Tom &amp; Jerry&#xa;second line">
            <font NAME="SansSerif" SIZE="20"/>
            <node COLOR="#ff0000" CREATED="1" ID="ID_2" MODIFIED="2" POSITION="right" TEXT="first" BACKGROUND_COLOR='#00FF00'>
            <edge COLOR="#808080" WIDTH="thin"/>
            <icon BUILTIN="idea"/>
            <node ID="ID_2" TEXT="duplicate id"/>
            </node>
            <node CREATED="1" ID="ID_3" MODIFIED="2" POSITION="left">
            <richcontent TYPE="NODE"><html>
              <head>

              </head>
              <body>
                <p>
                  rich <b>text</b> here
                </p>
              </body>
            </html>
            </richcontent>
            <richcontent TYPE="NOTE"><html><body><p>a note</p></body></html></richcontent>
            </node>
            </node>
            </map>
        """.trimIndent()

        val tree = FreeMind.read(content)
        val root = tree.getRootNode()
        // FreeMind's <font> sizes all of the text, here as large text.
        val rootText = RichText.parse(root.description)
        assertEquals("Tom & Jerry second line", rootText.text)
        assertEquals(RichTextSize.LARGE, rootText.sizeOf("Tom & Jerry"))
        assertEquals(RichTextSize.LARGE, rootText.sizeOf("second line"))
        assertEquals(Color.LightGray, root.color)
        assertEquals(listOf("ID_2", "ID_3"), root.children)

        val first = assertIs<RectangleNodeData>(tree.getNode("ID_2"))
        assertEquals("first", first.description)
        assertEquals(Color.Green, first.color)
        assertEquals(Color.Red, first.textColor)
        val duplicate = tree.getNode(first.children.single())
        assertEquals("duplicate id", duplicate.description)
        assertEquals("ID_2", duplicate.parentId)
        assertTrue(duplicate.id != "ID_2")

        val rich = tree.getNode("ID_3")
        assertEquals("<p>rich <b>text</b> here</p>", rich.description)
        assertEquals(NodeShape.RECTANGLE.defaultTextColor, rich.textColor)
        assertEquals("rich text here", RichText.parse(rich.description).text)
    }

    @Test
    fun readingSomethingThatIsNotAFreeMindMapFails() {
        assertFailsWith<IllegalArgumentException> { FreeMind.read("just some text") }
        assertFailsWith<IllegalArgumentException> { FreeMind.read("<map version=\"1.0.1\"></map>") }
        assertFailsWith<IllegalArgumentException> { FreeMind.read("<map><node TEXT=\"unterminated></map>") }
    }

    @Test
    fun treeReadFromFreeMindIsArrangedWhenShown() = runComposeUiTest {
        val source = Tree<Any>(Dp(150f), Dp(300f))
        source.addNode("a", source.getRootNode().id, "first")
        source.addNode("b", source.getRootNode().id, "second", NodeShape.STADIUM)
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("old")
        waitForIdle()
        clickNode(state.tree.getNode(state.tree.getRootNode().children.single()))

        state.setTree(FreeMind.read(FreeMind.write(source)))
        state.animateTreeChange()
        waitForIdle()

        assertNull(state.selectedNode)
        val root = state.tree.getRootNode()
        assertEquals(Dp(400f), root.path.centerX)
        val a = assertIs<RectangleNodeData>(state.tree.getNode("a"))
        val b = assertIs<StadiumNodeData>(state.tree.getNode("b"))
        assertTrue(a.path.width.dpVal > 0f)
        assertTrue(a.path.leftX().dpVal > (root.path.centerX + root.path.radiusX).dpVal)
        assertTrue(b.path.centerY.dpVal > a.path.centerY.dpVal)
    }

    @Test
    fun stadiumHitTestFollowsTheRoundedCaps() {
        val path = StadiumPath(Dp(100f), Dp(100f), length = Dp(60f), radius = Dp(20f))

        assertTrue(path.contains(Dp(100f), Dp(100f)))
        assertTrue(path.contains(Dp(149f), Dp(100f)))
        assertTrue(path.contains(Dp(130f), Dp(119f)))
        assertTrue(path.contains(Dp(51f), Dp(100f)))
        // Inside the bounding box, but outside the rounded cap.
        assertFalse(path.contains(Dp(148f), Dp(118f)))
        assertFalse(path.contains(Dp(52f), Dp(82f)))
        assertFalse(path.contains(Dp(151f), Dp(100f)))
        assertFalse(path.contains(Dp(100f), Dp(121f)))
    }

    @Test
    fun draggingStadiumOntoAnotherNodeReattachesIt() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM)
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(listOf(secondId), state.tree.getNode(firstId).children)
        assertIs<StadiumNodeData>(state.tree.getNode(secondId))
        assertEquals(firstId, state.tree.getNode(secondId).parentId)
    }

    @Test
    fun addEditAndRemoveOvalNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", NodeShape.OVAL)
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<OvalNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertEquals(NodeShape.OVAL.defaultColor, child.color)
        assertEquals(NodeShape.OVAL.defaultTextColor, child.textColor)
        assertTrue(child.path.leftX().dpVal > (root.path.centerX + root.path.radiusX).dpVal)
        assertEquals(root.path.centerY.dpVal, child.path.centerY.dpVal, 0.01f)
        assertEquals(child.path.radiusX.dpVal * 2, child.path.width().dpVal, 0.01f)
        assertEquals(child.path.radiusY.dpVal * 2, child.path.height().dpVal, 0.01f)

        clickNode(child)
        assertEquals(child.id, state.selectedNode?.id)
        state.editNodeText("a much longer description")
        waitForIdle()
        val edited = assertIs<OvalNodeData>(state.tree.getNode(child.id))
        assertEquals("a much longer description", edited.description)

        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())
        assertNull(state.selectedNode)
    }

    @Test
    fun ovalWidthAndHeightEachFollowTheText() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", NodeShape.OVAL)
        waitForIdle()
        val child = assertIs<OvalNodeData>(state.tree.getNode(state.tree.getRootNode().children.single()))

        clickNode(child)
        state.editNodeText("a much longer description")
        waitForIdle()
        val wider = assertIs<OvalNodeData>(state.tree.getNode(child.id))
        assertTrue(wider.path.radiusX.dpVal > child.path.radiusX.dpVal)
        assertEquals(child.path.radiusY.dpVal, wider.path.radiusY.dpVal, 0.01f)

        state.editNodeText("<p>child<br>child<br>child</p>")
        waitForIdle()
        val taller = assertIs<OvalNodeData>(state.tree.getNode(child.id))
        assertTrue(taller.path.radiusY.dpVal > child.path.radiusY.dpVal)
        assertTrue(taller.path.radiusX.dpVal < wider.path.radiusX.dpVal)
    }

    @Test
    fun ovalEnclosesItsText() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("the same text", NodeShape.RECTANGLE)
        waitForIdle()
        state.addNode("the same text", NodeShape.OVAL)
        waitForIdle()

        val (rectangleId, ovalId) = state.tree.getRootNode().children
        val rectangle = assertIs<RectangleNodeData>(state.tree.getNode(rectangleId))
        val oval = assertIs<OvalNodeData>(state.tree.getNode(ovalId))
        // A rectangle is its text plus the padding, which gives the size of the text itself.
        val padding = DrawInfo().padding.dpVal
        val textCornerX = oval.path.centerX + Dp((rectangle.path.width.dpVal - padding) / 2)
        val textCornerY = oval.path.centerY + Dp((rectangle.path.height.dpVal - padding) / 2)
        assertTrue(oval.path.contains(textCornerX, textCornerY))
    }

    @Test
    fun ovalHitTestFollowsTheEllipse() {
        val path = OvalPath(Dp(100f), Dp(100f), radiusX = Dp(40f), radiusY = Dp(20f))

        assertTrue(path.contains(Dp(100f), Dp(100f)))
        assertTrue(path.contains(Dp(139f), Dp(100f)))
        assertTrue(path.contains(Dp(61f), Dp(100f)))
        assertTrue(path.contains(Dp(100f), Dp(119f)))
        assertTrue(path.contains(Dp(100f), Dp(81f)))
        // Inside the bounding box, but outside the ellipse.
        assertFalse(path.contains(Dp(135f), Dp(115f)))
        assertFalse(path.contains(Dp(65f), Dp(85f)))
        assertFalse(path.contains(Dp(141f), Dp(100f)))
        assertFalse(path.contains(Dp(100f), Dp(121f)))
    }

    @Test
    fun freeMindRoundTripKeepsOval() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        tree.addNode("a", tree.getRootNode().id, "first", NodeShape.OVAL)
        tree.addNode("b", "a", "second", NodeShape.OVAL, Color.Red, Color.White)

        val written = FreeMind.write(tree)
        assertTrue("<attribute NAME=\"shape\" VALUE=\"oval\"/>" in written)

        val read = FreeMind.read(written)
        val a = assertIs<OvalNodeData>(read.getNode("a"))
        assertEquals("first", a.description)
        assertEquals(NodeShape.OVAL.defaultColor, a.color)
        assertEquals(NodeShape.OVAL.defaultTextColor, a.textColor)
        assertEquals(listOf("b"), a.children)
        val b = assertIs<OvalNodeData>(read.getNode("b"))
        assertEquals(Color.Red, b.color)
        assertEquals(Color.White, b.textColor)
        assertEquals(written, FreeMind.write(read))
    }

    @Test
    fun draggingOvalOntoAnotherNodeReattachesIt() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.OVAL)
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(listOf(secondId), state.tree.getNode(firstId).children)
        assertIs<OvalNodeData>(state.tree.getNode(secondId))
        assertEquals(firstId, state.tree.getNode(secondId).parentId)
    }

    @Test
    fun linksAreFoundInRichText() {
        val html =
            "<p>see <a href=\"https://example.com/\">the <b>site</b></a> or " +
                "<a href=\"mailto:someone@example.com\">write</a></p>"
        assertEquals(
            listOf(
                RichTextLink("the site", "https://example.com/"),
                RichTextLink("write", "mailto:someone@example.com"),
            ),
            RichText.links(html),
        )
        assertEquals(emptyList(), RichText.links("<p>no <b>link</b> here</p>"))

        val text = RichText.parse(html)
        assertEquals("see the site or write", text.text)
        assertNull(RichText.linkAt(text, text.text.indexOf("see")))
        assertEquals("https://example.com/", RichText.linkAt(text, text.text.indexOf("the")))
        assertEquals("https://example.com/", RichText.linkAt(text, text.text.indexOf("site") + 3))
        assertNull(RichText.linkAt(text, text.text.indexOf(" or") + 1))
        assertEquals("mailto:someone@example.com", RichText.linkAt(text, text.text.length - 1))
        assertNull(RichText.linkAt(text, text.text.length))
    }

    @Test
    fun clickingALinkInANodeReportsIt() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("<p><a href=\"https://example.com/\">a link to click</a></p>")
        state.addNode("no link")
        waitForIdle()
        val (linkId, plainId) = state.tree.getRootNode().children

        clickNode(state.tree.getNode(plainId))
        assertEquals(emptyList(), linkClicks)

        // The click selects the node as well.
        clickNode(state.tree.getNode(linkId))
        assertEquals(listOf(linkId to "https://example.com/"), linkClicks)
        assertEquals(linkId, state.selectedNode?.id)

        // The padding around the text is not part of the link.
        onNodeWithTag(TAG).performTouchInput {
            val node = state.tree.getNode(linkId) as RectangleNodeData
            click(Offset(node.path.leftX().dpVal.dp.toPx() + 2f, node.path.centerY.dpVal.dp.toPx()))
        }
        waitForIdle()
        assertEquals(1, linkClicks.size)

        // A drag that starts on a link is not a click on it.
        onNodeWithTag(TAG).performTouchInput {
            val start = center(state.tree.getNode(linkId))
            swipe(start, start + Offset(0f, 150.dp.toPx()), durationMillis = 200)
        }
        waitForIdle()
        assertEquals(1, linkClicks.size)

        val read = FreeMind.read(FreeMind.write(state.tree))
        assertEquals(
            listOf(RichTextLink("a link to click", "https://example.com/")),
            RichText.links(read.getNode(linkId).description),
        )
    }

    @Test
    fun secondaryClickOnANodeSelectsItAndAsksForItsMenu() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        val rootId = state.tree.getRootNode().id
        val firstId = state.tree.getRootNode().children.single()
        assertEquals(rootId, state.selectedNode?.id)
        val position = with(density) { center(state.tree.getNode(firstId)) }

        onNodeWithTag(TAG).performMouseInput { rightClick(position) }
        waitForIdle()
        assertEquals(listOf(firstId to position), nodeMenus)
        assertEquals(firstId, state.selectedNode?.id)
        assertEquals(emptyList(), arrowLinkMenus)

        // Empty space has no menu, and the secondary button leaves the selection as it is there.
        onNodeWithTag(TAG).performMouseInput { rightClick(Offset(5f, 5f)) }
        waitForIdle()
        assertEquals(1, nodeMenus.size)
        assertEquals(firstId, state.selectedNode?.id)
    }

    @Test
    fun arrowLinksAreAddedChangedAndRemoved() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        state.addNode("second")
        waitForIdle()
        val (firstId, secondId) = state.tree.getRootNode().children

        assertNull(state.addArrowLink(firstId, firstId))
        assertNull(state.addArrowLink(firstId, "no such node"))
        val link = assertIs<ArrowLink>(state.addArrowLink(firstId, secondId))
        assertEquals(ArrowLink(link.id, firstId, secondId, ArrowLink.DEFAULT_COLOR, false, true), link)
        // There is one arrow link from a node to another, though there may be one back.
        assertEquals(link, state.addArrowLink(firstId, secondId))
        val back = assertIs<ArrowLink>(state.addArrowLink(secondId, firstId))
        assertEquals(listOf(link, back), state.arrowLinks)
        state.removeArrowLink(back.id)
        assertEquals(listOf(link), state.arrowLinks)

        // The nodes are above one another to the right of the root, so the curve goes around them on the right,
        // from below the middle of the upper one to above the middle of the lower one.
        waitForIdle()
        val curve = assertIs<ArrowLinkCurve>(with(density) { arrowLinkCurve(state.tree, link) })
        val first = state.tree.getNode(firstId) as RectangleNodeData
        val second = state.tree.getNode(secondId) as RectangleNodeData
        with(density) {
            assertEquals(first.path.rightX().dpVal.dp.toPx(), curve.start.x, 0.01f)
            assertEquals((first.path.centerY + first.path.height * 0.3f).dpVal.dp.toPx(), curve.start.y, 0.01f)
            assertEquals(second.path.rightX().dpVal.dp.toPx(), curve.end.x, 0.01f)
            assertEquals((second.path.centerY - second.path.height * 0.3f).dpVal.dp.toPx(), curve.end.y, 0.01f)
        }
        val middle = curve.pointAt(0.5f)
        assertTrue(middle.x > curve.start.x && middle.x > curve.end.x)
        assertEquals(0f, curve.distanceTo(middle), 0.5f)

        // The secondary button on the curve asks for its menu; the primary one does not, nor does either elsewhere.
        onNodeWithTag(TAG).performMouseInput { rightClick(middle) }
        waitForIdle()
        assertEquals(listOf(link.id to middle), arrowLinkMenus)

        // An arrow link from the root to a node beside it has its ends on the edges that face each other.
        val fromRoot = assertIs<ArrowLink>(state.addArrowLink(state.tree.getRootNode().id, firstId))
        val facing = assertIs<ArrowLinkCurve>(with(density) { arrowLinkCurve(state.tree, fromRoot) })
        with(density) {
            assertTrue(facing.start.x > state.tree.getRootNode().path.centerX.dpVal.dp.toPx())
            assertEquals(first.path.leftX().dpVal.dp.toPx(), facing.end.x, 0.01f)
        }
        state.removeArrowLink(fromRoot.id)

        onNodeWithTag(TAG).performMouseInput { click(middle) }
        onNodeWithTag(TAG).performMouseInput { rightClick(middle + Offset(60.dp.toPx(), 0f)) }
        waitForIdle()
        assertEquals(1, arrowLinkMenus.size)

        val changed = link.copy(color = Color.Red, hasStartArrow = true, hasEndArrow = false)
        state.updateArrowLink(changed)
        assertEquals(listOf(changed), state.arrowLinks)
        state.updateArrowLink(changed.copy(id = "no such link"))
        assertEquals(listOf(changed), state.arrowLinks)

        state.goToNode(secondId)
        assertEquals(secondId, state.selectedNode?.id)
    }

    @Test
    fun removingANodeRemovesTheArrowLinksOfItsBranch() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        state.addNode("second")
        state.addNode("third")
        waitForIdle()
        val root = state.tree.getRootNode()
        val (firstId, secondId, thirdId) = root.children
        clickNode(state.tree.getNode(firstId))
        state.addNode("below first")
        waitForIdle()
        val belowFirstId = state.tree.getNode(firstId).children.single()

        state.addArrowLink(firstId, secondId)
        state.addArrowLink(thirdId, belowFirstId)
        val kept = state.addArrowLink(secondId, thirdId)

        // A node that is dragged onto another keeps its arrow links.
        onNodeWithTag(TAG).performTouchInput {
            swipe(center(state.tree.getNode(belowFirstId)), center(state.tree.getNode(secondId)), durationMillis = 200)
        }
        waitForIdle()
        assertEquals(secondId, state.tree.getNode(belowFirstId).parentId)
        assertEquals(3, state.arrowLinks.size)

        clickNode(state.tree.getNode(firstId))
        state.removeNode()
        waitForIdle()
        assertEquals(2, state.arrowLinks.size)
        clickNode(state.tree.getNode(secondId))
        state.removeNode()
        waitForIdle()
        assertEquals(emptyList(), state.arrowLinks)
        assertTrue(kept != null)
    }

    @Test
    fun freeMindRoundTripKeepsArrowLinks() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("first", rootId, "first")
        tree.addNode("second", rootId, "second", side = LayoutSide.LEFT)
        tree.addNode("removed", rootId, "removed")
        val plain = ArrowLink("plain", "first", "second")
        val changed = ArrowLink(
            id = "changed",
            sourceId = "second",
            targetId = rootId,
            color = Color.Red,
            hasStartArrow = true,
            hasEndArrow = false,
            startInclination = ArrowLinkInclination(Dp(-120f), Dp(35f)),
            endInclination = ArrowLinkInclination(Dp(60f), Dp(-10f)),
        )
        tree.addArrowLink(plain)
        tree.addArrowLink(changed)
        tree.addArrowLink(ArrowLink("to removed", "first", "removed"))
        assertFailsWith<IllegalArgumentException> { tree.addArrowLink(ArrowLink("self", "first", "first")) }
        assertFailsWith<IllegalArgumentException> { tree.addArrowLink(ArrowLink("nowhere", "first", "no such node")) }
        // Only the node is detached here, as it is when it is dragged, so its arrow link is still there.
        tree.removeNode("removed")
        assertEquals(3, tree.arrowLinks.size)

        val written = FreeMind.write(tree)
        assertTrue("<arrowlink ID=\"plain\" DESTINATION=\"second\" COLOR=\"#b0b0b0\" STARTARROW=\"None\" ENDARROW=\"Default\"/>" in written)
        assertTrue("STARTINCLINATION=\"-120;35;\" ENDINCLINATION=\"60;-10;\"" in written)
        assertFalse("to removed" in written)
        assertEquals(listOf(plain, changed), FreeMind.read(written).arrowLinks)
    }

    @Test
    fun freeMindArrowLinksAreRead() {
        val content = """
            <map version="1.0.1">
            <node ID="ID_1" TEXT="root">
            <arrowlink DESTINATION="ID_3" ENDARROW="Default" ENDINCLINATION="57;0;" ID="Arrow_ID_7" STARTARROW="None" STARTINCLINATION="57;0;"/>
            <node ID="ID_2" POSITION="right" TEXT="first">
            <arrowlink COLOR="#ff0000" DESTINATION="ID_1" ENDARROW="None" ID="Arrow_ID_8" STARTARROW="Default"/>
            <arrowlink DESTINATION="ID_3"/>
            <arrowlink DESTINATION="ID_404" ID="Arrow_ID_9"/>
            <arrowlink DESTINATION="ID_2" ID="Arrow_ID_10"/>
            </node>
            <node ID="ID_3" POSITION="left" TEXT="second"/>
            </node>
            </map>
        """.trimIndent()

        val tree = FreeMind.read(content)
        val rootId = tree.getRootNode().id
        val (fromRoot, toRoot, bare) = tree.arrowLinks
        assertEquals(3, tree.arrowLinks.size)
        val inclination = ArrowLinkInclination(Dp(57f), Dp(0f))
        assertEquals(ArrowLink("Arrow_ID_7", rootId, "ID_3", ArrowLink.DEFAULT_COLOR, false, true, inclination, inclination), fromRoot)
        assertEquals(ArrowLink("Arrow_ID_8", "ID_2", rootId, Color.Red, true, false), toRoot)
        assertEquals(ArrowLink(bare.id, "ID_2", "ID_3"), bare)
    }

    @Test
    fun attributesMakeANodeLargerWhileTheyAreShown() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("child")
        state.addNode("sibling")
        waitForIdle()
        val (childId, siblingId) = state.tree.getRootNode().children
        fun child() = state.tree.getNode(childId) as RectangleNodeData
        val plain = child().path
        val apart = state.tree.getNode(siblingId).path.centerY.dpVal - plain.centerY.dpVal
        assertTrue(state.showAttributes)
        assertEquals(emptyList(), state.tree.attributesOf(childId))

        val attributes = listOf(NodeAttribute("priority", "high"), NodeAttribute("owner", "someone with a long name"))
        clickNode(child())
        state.editNodeAttributes(attributes)
        waitForIdle()
        assertEquals(attributes, state.tree.attributesOf(childId))
        assertEquals(emptyList(), state.tree.attributesOf(siblingId))
        assertTrue(child().path.height.dpVal > plain.height.dpVal + 20f)
        assertTrue(child().path.width.dpVal > plain.width.dpVal)
        // The sibling makes room for the taller node.
        assertTrue(state.tree.getNode(siblingId).path.centerY.dpVal - child().path.centerY.dpVal > apart)

        state.showAttributes = false
        waitForIdle()
        assertEquals(attributes, state.tree.attributesOf(childId))
        assertEquals(plain.width, child().path.width)
        assertEquals(plain.height, child().path.height)
        assertEquals(apart, state.tree.getNode(siblingId).path.centerY.dpVal - child().path.centerY.dpVal, 0.01f)

        state.showAttributes = true
        waitForIdle()
        assertTrue(child().path.height.dpVal > plain.height.dpVal + 20f)

        state.editNodeAttributes(emptyList())
        waitForIdle()
        assertEquals(emptyList(), state.tree.attributesOf(childId))
        assertEquals(plain.height, child().path.height)
    }

    @Test
    fun linkInANodeWithAttributesIsWhereItsTextIsDrawn() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        clickNode(state.tree.getRootNode())
        state.addNode("<p><a href=\"https://example.com/\">a link to click</a></p>")
        waitForIdle()
        val id = state.tree.getRootNode().children.single()
        clickNode(state.tree.getNode(id))
        state.editNodeAttributes(List(4) { NodeAttribute("name $it", "value $it") })
        waitForIdle()
        linkClicks.clear()

        // The text is at the top of the node now, and its attributes are in the middle.
        clickNode(state.tree.getNode(id))
        assertEquals(emptyList(), linkClicks)
        onNodeWithTag(TAG).performTouchInput {
            val node = state.tree.getNode(id) as RectangleNodeData
            click(Offset(node.path.centerX.dpVal.dp.toPx(), (node.path.topY() + Dp(17f)).dpVal.dp.toPx()))
        }
        waitForIdle()
        assertEquals(listOf(id to "https://example.com/"), linkClicks)
    }

    @Test
    fun freeMindRoundTripKeepsAttributes() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("first", rootId, "first", NodeShape.OVAL)
        tree.addNode("second", rootId, "second")
        val rootAttributes = listOf(NodeAttribute("status", "draft"))
        // A node may have attributes of one name, and one that is named as the one its shape is kept in.
        val firstAttributes = listOf(
            NodeAttribute("tag", "a <b> & \"c\""),
            NodeAttribute("tag", ""),
            NodeAttribute("shape", "circle"),
            NodeAttribute("shape", "no shape at all"),
        )
        tree.setAttributes(rootId, rootAttributes)
        tree.setAttributes("first", firstAttributes)
        assertFailsWith<IllegalArgumentException> { tree.setAttributes("no such node", rootAttributes) }

        val read = FreeMind.read(FreeMind.write(tree))
        assertEquals(rootAttributes, read.attributesOf(rootId))
        assertEquals(firstAttributes, read.attributesOf("first"))
        assertEquals(emptyList(), read.attributesOf("second"))
        assertIs<OvalNodeData>(read.getNode("first"))
        assertIs<RectangleNodeData>(read.getNode("second"))
    }

    @Test
    fun freeMindAttributesAreRead() {
        val content = """
            <map version="1.0.1">
            <attribute_registry SHOW_ATTRIBUTES="selected">
            <attribute_name NAME="priority" VISIBLE="true"><attribute_value VALUE="high"/></attribute_name>
            </attribute_registry>
            <node ID="ID_1" TEXT="root">
            <node ID="ID_2" POSITION="right" TEXT="first">
            <attribute_layout NAME_WIDTH="60" VALUE_WIDTH="80"/>
            <attribute NAME="priority" VALUE="high"/>
            <attribute NAME="shape" VALUE="not one of ours"/>
            <attribute NAME="owner" VALUE="Tom &amp; Jerry"/>
            </node>
            <node ID="ID_3" POSITION="right" TEXT="second">
            <attribute NAME="shape" VALUE="stadium"/>
            </node>
            </node>
            </map>
        """.trimIndent()

        val tree = FreeMind.read(content)
        assertEquals(
            listOf(
                NodeAttribute("priority", "high"),
                NodeAttribute("shape", "not one of ours"),
                NodeAttribute("owner", "Tom & Jerry"),
            ),
            tree.attributesOf("ID_2"),
        )
        assertIs<RectangleNodeData>(tree.getNode("ID_2"))
        assertEquals(emptyList(), tree.attributesOf("ID_3"))
        assertIs<StadiumNodeData>(tree.getNode("ID_3"))
        assertEquals(emptyList(), tree.attributesOf(tree.getRootNode().id))
    }

    @Test
    fun noteIsSetKeptThroughEditsAndCleared() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", note = "<p>remember <b>this</b></p>")
        waitForIdle()
        val child = state.tree.getNode(state.tree.getRootNode().children.single())
        assertEquals("<p>remember <b>this</b></p>", child.note)
        assertEquals("", state.tree.getRootNode().note)

        clickNode(child)
        state.editNodeText("changed")
        state.editNodeColor(Color.Red)
        state.editNodeTextColor(Color.White)
        waitForIdle()
        assertEquals("<p>remember <b>this</b></p>", state.tree.getNode(child.id).note)

        state.editNodeNote("<p>something else</p>")
        waitForIdle()
        assertEquals("<p>something else</p>", state.tree.getNode(child.id).note)
        assertEquals("<p>something else</p>", state.selectedNode?.note)
        assertEquals("changed", state.tree.getNode(child.id).description)

        state.editNodeNote("")
        waitForIdle()
        assertEquals("", state.tree.getNode(child.id).note)
    }

    @Test
    fun editNodeShapeChangesTheShapeAndKeepsTheRestOfTheNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", color = Color.Red, textColor = Color.White, note = "<p>a note</p>")
        waitForIdle()
        val child = assertIs<RectangleNodeData>(state.tree.getNode(state.tree.getRootNode().children.single()))
        clickNode(child)
        state.addNode("grandchild")
        waitForIdle()
        val grandchildId = state.tree.getNode(child.id).children.single()
        val grandchildX = state.tree.getNode(grandchildId).path.centerX

        state.editNodeShape(NodeShape.CIRCLE)
        waitForIdle()

        val circle = assertIs<CircleNodeData>(state.tree.getNode(child.id))
        assertEquals("child", circle.description)
        assertEquals("<p>a note</p>", circle.note)
        assertEquals(Color.Red, circle.color)
        assertEquals(Color.White, circle.textColor)
        assertEquals(state.tree.getRootNode().id, circle.parentId)
        assertEquals(listOf(grandchildId), circle.children)
        assertEquals(listOf(child.id), state.tree.getRootNode().children)
        assertEquals(child.path.centerY, circle.path.centerY)
        assertTrue(circle.path.radius.dpVal > 0f)
        assertIs<CircleNodeData>(state.selectedNode)

        // The nodes below it are arranged again around a node that takes another room.
        assertTrue(state.tree.getNode(grandchildId).path.centerX.dpVal != grandchildX.dpVal)
        assertTrue(
            state.tree.getNode(grandchildId).path.centerX.dpVal > (circle.path.centerX + circle.path.radius).dpVal
        )

        NodeShape.entries.forEach { shape ->
            state.editNodeShape(shape)
            waitForIdle()
            assertEquals(shape, state.tree.getNode(child.id).shape)
            assertEquals(listOf(grandchildId), state.tree.getNode(child.id).children)
        }
    }

    @Test
    fun editNodeShapeLeavesTheRootACircle() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        clickNode(state.tree.getRootNode())
        val root = state.tree.getRootNode()

        state.editNodeShape(NodeShape.RECTANGLE)
        waitForIdle()

        assertEquals(root, state.tree.getRootNode())
    }

    @Test
    fun noteDoesNotChangeTheSizeOfItsNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()
        val child = assertIs<RectangleNodeData>(state.tree.getNode(state.tree.getRootNode().children.single()))

        clickNode(child)
        state.editNodeNote("<p>a note that is a good deal longer than the text of its node</p>")
        waitForIdle()
        val withNote = assertIs<RectangleNodeData>(state.tree.getNode(child.id))
        assertEquals(child.path, withNote.path)
    }

    @Test
    fun noteIsKeptWhenItsNodeIsReattached() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.OVAL, note = "<p>kept</p>")
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(firstId, state.tree.getNode(secondId).parentId)
        assertEquals("<p>kept</p>", state.tree.getNode(secondId).note)
    }

    @Test
    fun freeMindRoundTripKeepsNotes() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.updateNodeNote(rootId, "<p>about the root</p>")
        tree.addNode("a", rootId, "first", note = "<p>a <b>note</b><br>in two lines</p>")
        tree.addNode("b", rootId, "second", NodeShape.STADIUM)

        val written = FreeMind.write(tree)
        assertTrue("<richcontent TYPE=\"NOTE\"><html><head></head><body><p>a <b>note</b><br/>in two lines</p></body></html></richcontent>" in written)
        assertEquals(2, Regex("TYPE=\"NOTE\"").findAll(written).count())

        val read = FreeMind.read(written)
        assertEquals("<p>about the root</p>", read.getRootNode().note)
        assertEquals("<p>a <b>note</b><br/>in two lines</p>", read.getNode("a").note)
        assertEquals("first", read.getNode("a").description)
        assertEquals("", read.getNode("b").note)
        assertEquals(written, FreeMind.write(read))
    }

    @Test
    fun freeMindRoundTripKeepsImagesInNotes() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val note = "<p>see <img src=\"https://e.org/x.png\" width=\"100.0\"></p>"
        tree.addNode("a", tree.getRootNode().id, "first", note = note)

        val written = FreeMind.write(tree)
        assertTrue("<p>see <img src=\"https://e.org/x.png\" width=\"100.0\"/></p>" in written)

        val read = FreeMind.read(written)
        assertEquals("<p>see <img src=\"https://e.org/x.png\" width=\"100.0\"/></p>", read.getNode("a").note)
        assertEquals(written, FreeMind.write(read))
    }

    @Test
    fun freeMindNotesAreRead() {
        val content = """
            <map version="1.0.1">
            <node ID="ID_1" TEXT="root">
            <node ID="ID_2" TEXT="child">
            <richcontent TYPE="NOTE"><html>
              <head>
              </head>
              <body>
                <p>
                  a note
                </p>
              </body>
            </html>
            </richcontent>
            </node>
            </node>
            </map>
        """.trimIndent()

        val tree = FreeMind.read(content)
        assertEquals("child", tree.getNode("ID_2").description)
        assertEquals("<p>a note</p>", tree.getNode("ID_2").note)
        assertEquals("", tree.getRootNode().note)
    }

    @Test
    fun noteMarkerSitsOnTheTopRightEdgeOfItsNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        NodeShape.entries.forEach {
            state.addNode("child", it)
            waitForIdle()
        }

        val root = state.tree.getRootNode()
        val rootMarker = density.noteMarkerCenter(root)
        val rootCenter = with(density) { center(root) }
        // The root is an oval: the marker is at 45 degrees round it, as far out as each radius has it.
        val expected = with(density) {
            Offset(root.path.radiusX.dpVal.dp.toPx(), -root.path.radiusY.dpVal.dp.toPx()) * 0.70710677f
        }
        assertEquals(expected.x, (rootMarker - rootCenter).x, 0.01f)
        assertEquals(expected.y, (rootMarker - rootCenter).y, 0.01f)
        assertTrue(rootMarker.x > rootCenter.x && rootMarker.y < rootCenter.y)

        root.children.map { state.tree.getNode(it) }.forEach { node ->
            val marker = density.noteMarkerCenter(node)
            val center = with(density) { center(node) }
            assertTrue(marker.x > center.x && marker.y < center.y)
            when (node) {
                is RectangleNodeData -> {
                    assertEquals(with(density) { node.path.rightX().dpVal.dp.toPx() }, marker.x, 0.01f)
                    assertEquals(with(density) { node.path.topY().dpVal.dp.toPx() }, marker.y, 0.01f)
                }
                is OvalNodeData -> {
                    val markerDp = with(density) { Dp(marker.x.toDp().value) to Dp(marker.y.toDp().value) }
                    assertTrue(node.path.contains(markerDp.first - 0.1f, markerDp.second + 0.1f))
                    assertFalse(node.path.contains(markerDp.first + 0.1f, markerDp.second - 0.1f))
                }
                is StadiumNodeData -> {
                    val markerDp = with(density) { Dp(marker.x.toDp().value) to Dp(marker.y.toDp().value) }
                    assertTrue(node.path.contains(markerDp.first - 0.1f, markerDp.second + 0.1f))
                    assertFalse(node.path.contains(markerDp.first + 0.1f, markerDp.second - 0.1f))
                }
                is CircleNodeData ->
                    assertEquals(with(density) { node.path.radius.dpVal.dp.toPx() }, (marker - center).getDistance(), 0.01f)
            }
        }
    }

    @Test
    fun hoveredNodeFollowsTheMouse() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()
        val child = state.tree.getNode(state.tree.getRootNode().children.single())
        assertNull(state.hoveredNode)

        onNodeWithTag(TAG).performMouseInput { moveTo(center(child)) }
        waitForIdle()
        assertEquals(child.id, state.hoveredNode?.id)
        val childCenter = with(density) { center(child) }
        assertEquals(childCenter, state.hoverPosition)

        onNodeWithTag(TAG).performMouseInput { moveTo(center(state.tree.getRootNode())) }
        waitForIdle()
        assertEquals(state.tree.getRootNode().id, state.hoveredNode?.id)

        onNodeWithTag(TAG).performMouseInput { moveTo(Offset(5f, 5f)) }
        waitForIdle()
        assertNull(state.hoveredNode)
    }

    @Test
    fun hoverEndsWhenTheMouseIsPressedOrLeaves() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        waitForIdle()
        val root = state.tree.getRootNode()

        onNodeWithTag(TAG).performMouseInput { moveTo(center(root)) }
        waitForIdle()
        assertEquals(root.id, state.hoveredNode?.id)
        onNodeWithTag(TAG).performMouseInput { press() }
        waitForIdle()
        assertNull(state.hoveredNode)
        onNodeWithTag(TAG).performMouseInput { release() }
        waitForIdle()

        onNodeWithTag(TAG).performMouseInput { moveBy(Offset(1f, 1f)) }
        waitForIdle()
        assertEquals(root.id, state.hoveredNode?.id)
        onNodeWithTag(TAG).performMouseInput { exit() }
        waitForIdle()
        assertNull(state.hoveredNode)
    }

    private fun MindMapState.arrowLinkEnds(): List<Pair<String, String>> = arrowLinks.map { it.sourceId to it.targetId }

    private fun ComposeUiTest.stateWithTwoChildren(): Triple<MindMapState, String, String> {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)
        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.OVAL)
        waitForIdle()
        val (firstId, secondId) = state.tree.getRootNode().children
        return Triple(state, firstId, secondId)
    }

    // The root with "first" and "second" below it, "below first" and "also below first" below "first" and
    // "lowest" below "below first".
    private class FoldTree(
        val state: MindMapState,
        val firstId: String,
        val secondId: String,
        val belowFirstId: String,
        val alsoBelowFirstId: String,
        val lowestId: String,
    )

    private fun ComposeUiTest.stateToFold(): FoldTree {
        val (state, firstId, secondId) = stateWithTwoChildren()
        clickNode(state.tree.getNode(firstId))
        state.addNode("below first")
        waitForIdle()
        state.addNode("also below first")
        waitForIdle()
        val (belowFirstId, alsoBelowFirstId) = state.tree.getNode(firstId).children
        clickNode(state.tree.getNode(belowFirstId))
        state.addNode("lowest")
        waitForIdle()
        return FoldTree(state, firstId, secondId, belowFirstId, alsoBelowFirstId, state.tree.getNode(belowFirstId).children.single())
    }

    private fun MindMapState.foldedIds(): Set<String> = tree.nodes.keys.filter { tree.isFolded(it) }.toSet()

    @Test
    fun foldingANodeHidesTheNodesBelowItAndClosesTheirRoom() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state
        val belowFirst = state.tree.getNode(fold.belowFirstId)
        val gap = state.tree.getNode(fold.secondId).path.centerY - state.tree.getNode(fold.firstId).path.centerY

        clickNode(state.tree.getNode(fold.firstId))
        state.toggleFold()
        waitForIdle()

        assertTrue(state.tree.isFolded(fold.firstId))
        assertFalse(state.tree.isVisible(fold.belowFirstId))
        assertFalse(state.tree.isVisible(fold.lowestId))
        assertTrue(state.tree.isVisible(fold.firstId))
        assertEquals(listOf(fold.belowFirstId, fold.alsoBelowFirstId), state.tree.getNode(fold.firstId).children)
        val foldedGap = state.tree.getNode(fold.secondId).path.centerY - state.tree.getNode(fold.firstId).path.centerY
        assertTrue(foldedGap.dpVal < gap.dpVal)

        // A hidden node is not there to click.
        clicks.clear()
        clickNode(belowFirst)
        assertTrue(clicks.none { it?.id == fold.belowFirstId })

        clickNode(state.tree.getNode(fold.firstId))
        state.toggleFold()
        waitForIdle()
        assertFalse(state.tree.isFolded(fold.firstId))
        assertEquals(gap, state.tree.getNode(fold.secondId).path.centerY - state.tree.getNode(fold.firstId).path.centerY)
        clickNode(state.tree.getNode(fold.belowFirstId))
        assertEquals(fold.belowFirstId, state.selectedNode?.id)
    }

    @Test
    fun rootAndNodeWithoutChildrenAreNotFolded() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state

        clickNode(state.tree.getRootNode())
        state.toggleFold()
        clickNode(state.tree.getNode(fold.secondId))
        state.toggleFold()
        waitForIdle()

        assertEquals(emptySet(), state.foldedIds())
    }

    @Test
    fun clickOnTheMarkerOfAFoldedNodeUnfoldsIt() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state
        clickNode(state.tree.getNode(fold.firstId))
        state.toggleFold()
        waitForIdle()
        clickNode(state.tree.getNode(fold.secondId))
        addChildClicks.clear()

        onNodeWithTag(TAG).performTouchInput { click(foldMarkerCenter(state.tree, state.tree.getNode(fold.firstId))) }
        waitForIdle()

        assertFalse(state.tree.isFolded(fold.firstId))
        assertEquals(fold.firstId, state.selectedNode?.id)
        // The marker is where the add button is while the node is not folded, and takes the click from it.
        assertTrue(addChildClicks.isEmpty())
    }

    @Test
    fun centeringANodeMovesItToTheMiddleOfTheView() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()
        fun onScreen(node: NodeData<*>) = with(density) {
            Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
        } * state.scaleFactor + state.currentPoint
        // The root starts out in the middle of the view.
        val middle = onScreen(state.tree.getRootNode())
        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()
        val child = state.tree.getNode(state.tree.getRootNode().children.first())
        assertNotEquals(middle, onScreen(child))

        state.centerNode(child.id)
        waitForIdle()
        assertEquals(middle.x, onScreen(child).x, 0.5f)
        assertEquals(middle.y, onScreen(child).y, 0.5f)
    }

    @Test
    fun goingToAHiddenNodeUnfoldsTheNodesAboveIt() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state
        clickNode(state.tree.getNode(fold.firstId))
        state.foldAll()
        waitForIdle()
        assertEquals(setOf(fold.firstId, fold.belowFirstId), state.foldedIds())

        state.goToNode(fold.lowestId)
        waitForIdle()
        assertEquals(emptySet(), state.foldedIds())
        assertEquals(fold.lowestId, state.selectedNode?.id)

        clickNode(state.tree.getRootNode())
        state.foldAll()
        state.goToNode(fold.lowestId)
        waitForIdle()
        // The nodes are arranged again, so the node is where it is clicked.
        clickNode(state.tree.getNode(fold.lowestId))
        assertEquals(fold.lowestId, clicks.last()?.id)
    }

    @Test
    fun foldCommandsFoldAndUnfoldByLevel() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state
        clickNode(state.tree.getRootNode())

        state.foldOneLevel()
        assertEquals(setOf(fold.belowFirstId), state.foldedIds())
        state.foldOneLevel()
        assertEquals(setOf(fold.belowFirstId, fold.firstId), state.foldedIds())
        state.foldOneLevel()
        assertEquals(setOf(fold.belowFirstId, fold.firstId), state.foldedIds())

        state.unfoldOneLevel()
        assertEquals(setOf(fold.belowFirstId), state.foldedIds())
        state.unfoldOneLevel()
        assertEquals(emptySet(), state.foldedIds())

        state.toggleChildrenFold()
        assertEquals(setOf(fold.firstId), state.foldedIds())
        state.toggleChildrenFold()
        assertEquals(emptySet(), state.foldedIds())

        state.foldAll()
        assertEquals(setOf(fold.firstId, fold.belowFirstId), state.foldedIds())
        state.unfoldAll()
        assertEquals(emptySet(), state.foldedIds())
        waitForIdle()
    }

    @Test
    fun foldedNodeUnfoldsForANewChildAndHidesTheArrowLinksOfItsHiddenNodes() = runComposeUiTest {
        val fold = stateToFold()
        val state = fold.state
        val link = assertIs<ArrowLink>(state.addArrowLink(fold.secondId, fold.lowestId))
        clickNode(state.tree.getNode(fold.firstId))
        state.toggleFold()
        waitForIdle()
        assertNull(with(density) { arrowLinkCurve(state.tree, link) })

        state.addNode("new below first")
        waitForIdle()

        assertFalse(state.tree.isFolded(fold.firstId))
        assertEquals(3, state.tree.getNode(fold.firstId).children.size)
        assertIs<ArrowLinkCurve>(with(density) { arrowLinkCurve(state.tree, link) })

        clickNode(state.tree.getNode(fold.firstId))
        state.toggleFold()
        state.removeNode()
        waitForIdle()
        assertEquals(emptySet(), state.foldedIds())
    }

    @Test
    fun freeMindRoundTripKeepsFolds() {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "first")
        tree.addNode("b", rootId, "second")
        tree.addNode("c", "a", "third")
        tree.setFolded("a", true)
        tree.setFolded("b", true)
        tree.setFolded(rootId, true)

        val written = FreeMind.write(tree)
        assertEquals(1, Regex("FOLDED=\"true\"").findAll(written).count())

        val read = FreeMind.read(written)
        assertTrue(read.isFolded("a"))
        assertFalse(read.isFolded("b"))
        assertFalse(read.isFolded(rootId))
        assertEquals(listOf("c"), read.getNode("a").children)
        assertEquals(emptyList(), read.visibleChildren(read.getNode("a")))
        assertEquals(written, FreeMind.write(read))
    }

    @Test
    fun ctrlDraggingNodeOntoAnotherLinksThemWithoutMovingThem() = runComposeUiTest {
        val (state, firstId, secondId) = stateWithTwoChildren()
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)

        ctrlDrag(first) { center(second) }

        assertEquals(listOf(firstId to secondId), state.arrowLinkEnds())
        assertEquals(listOf(Triple(firstId, secondId, true)), linkChanges)
        assertTrue(moves.isEmpty())
        assertEquals(listOf(firstId, secondId), state.tree.getRootNode().children)
        assertEquals(first.path, state.tree.getNode(firstId).path)
        assertEquals(second.path, state.tree.getNode(secondId).path)
    }

    @Test
    fun repeatingTheCtrlDragRemovesTheLink() = runComposeUiTest {
        val (state, firstId, secondId) = stateWithTwoChildren()
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)

        ctrlDrag(first) { center(second) }
        ctrlDrag(second) { center(first) }
        assertEquals(listOf(firstId to secondId, secondId to firstId), state.arrowLinkEnds())

        ctrlDrag(first) { center(second) }
        assertEquals(listOf(secondId to firstId), state.arrowLinkEnds())
        assertEquals(Triple(firstId, secondId, false), linkChanges.last())
    }

    @Test
    fun ctrlDragThatEndsOnNoOtherNodeLinksNothing() = runComposeUiTest {
        val (state, firstId, _) = stateWithTwoChildren()
        val first = state.tree.getNode(firstId)

        ctrlDrag(first) { Offset(5f, 5f) }
        ctrlDrag(first) { center(first) + Offset(3f, 3f) }

        assertTrue(state.arrowLinks.isEmpty())
        assertTrue(linkChanges.isEmpty())
        assertEquals(first.path, state.tree.getNode(firstId).path)
    }

    @Test
    fun rootCanBeLinked() = runComposeUiTest {
        val (state, firstId, _) = stateWithTwoChildren()
        val root = state.tree.getRootNode()

        ctrlDrag(root) { center(state.tree.getNode(firstId)) }

        assertEquals(listOf(root.id to firstId), state.arrowLinkEnds())
        assertEquals(root.path, state.tree.getRootNode().path)
    }

    @Test
    fun linkIsKeptWhenItsNodeIsReattached() = runComposeUiTest {
        val (state, firstId, secondId) = stateWithTwoChildren()
        assertIs<ArrowLink>(state.addArrowLink(secondId, state.tree.getRootNode().id))

        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(firstId, state.tree.getNode(secondId).parentId)
        assertEquals(listOf(secondId to state.tree.getRootNode().id), state.arrowLinkEnds())
    }

    @Test
    fun removingNodeRemovesTheLinksOfItAndTheNodesBelowIt() = runComposeUiTest {
        val (state, firstId, secondId) = stateWithTwoChildren()
        val rootId = state.tree.getRootNode().id
        clickNode(state.tree.getNode(firstId))
        state.addNode("below first")
        waitForIdle()
        val belowFirstId = state.tree.getNode(firstId).children.single()
        assertIs<ArrowLink>(state.addArrowLink(belowFirstId, secondId))
        assertIs<ArrowLink>(state.addArrowLink(secondId, firstId))
        assertIs<ArrowLink>(state.addArrowLink(rootId, secondId))

        clickNode(state.tree.getNode(firstId))
        assertEquals(firstId, state.selectedNode?.id)
        state.removeNode()
        waitForIdle()

        assertEquals(listOf(secondId), state.tree.getRootNode().children)
        assertEquals(listOf(rootId to secondId), state.arrowLinkEnds())
    }

    @Test
    fun draggingNodeOntoAnotherReattachesIt() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second")
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        assertTrue(second.path.centerY.dpVal > first.path.centerY.dpVal)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(listOf(firstId), state.tree.getRootNode().children)
        assertEquals(listOf(secondId), state.tree.getNode(firstId).children)
        assertEquals(firstId, state.tree.getNode(secondId).parentId)
        assertTrue(state.tree.getNode(secondId).path.centerX.dpVal > state.tree.getNode(firstId).path.centerX.dpVal)
    }

    @Test
    fun dragOnEmptySpacePansAndFitScreenResets() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        onNodeWithTag(TAG).performTouchInput {
            swipe(Offset(700.dp.toPx(), 500.dp.toPx()), Offset(600.dp.toPx(), 450.dp.toPx()), durationMillis = 200)
        }
        waitForIdle()
        assertEquals(with(density) { Offset(-100.dp.toPx(), -50.dp.toPx()) }, state.currentPoint)

        state.fitScreen()
        waitForIdle()
        assertEquals(Offset.Zero, state.currentPoint)
        assertEquals(1f, state.scaleFactor)
    }

    private companion object {
        const val TAG = "mindMap"
    }
}
