package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MindMapTest {
    private val clicks = mutableListOf<NodeData<*>?>()
    private val doubleClicks = mutableListOf<String>()
    private val moves = mutableListOf<Pair<String, String>>()
    private val addChildClicks = mutableListOf<String>()

    private fun ComposeUiTest.setMindMap(state: MindMapState) {
        setContent {
            MindMap(
                state = state,
                modifier = Modifier.size(800.dp, 600.dp).testTag(TAG),
                onNodeClick = { clicks.add(it) },
                onNodeDoubleClick = { doubleClicks.add(it.id) },
                onNodeMove = { _, target, parent -> moves.add(target.id to parent.id) },
                onAddChildClick = { addChildClicks.add(it.id) },
            )
        }
    }

    private fun ComposeUiTest.clickNode(node: NodeData<*>) {
        onNodeWithTag(TAG).performTouchInput { click(center(node)) }
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
    fun rootIsAGrayStadiumWithBlackText() {
        val root: NodeData<*> = Tree<Any>(Dp(150f), Dp(300f)).getRootNode()
        assertIs<StadiumNodeData>(root)
        assertEquals(Color.Gray, root.color)
        assertEquals(Color.Black, root.textColor)
    }

    @Test
    fun clickSelectsNodeAndEmptySpaceClearsSelection() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        assertIs<StadiumNodeData>(state.selectedNode)
        assertIs<StadiumNodeData>(clicks.last())

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
        assertTrue(child.path.leftX().dpVal > root.path.rightX().dpVal)
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
    fun addEditAndRemoveStadiumNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", NodeShape.STADIUM)
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<StadiumNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertTrue(child.path.leftX().dpVal > root.path.rightX().dpVal)
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
        assertTrue((second.path.centerX - second.path.radius).dpVal > root.path.rightX().dpVal)

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
        assertEquals(Color.Gray, root.color)

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
    fun descriptionMarkupIsParsedAsRichText() {
        val richText = RichText.parse("a <b>bold</b> &amp; <i>italic</i> word")

        assertEquals("a bold & italic word", richText.text)
        assertTrue(richText.spanStyles.any { it.item.fontWeight == FontWeight.Bold && richText.text.substring(it.start, it.end) == "bold" })
        assertTrue(richText.spanStyles.any { it.item.fontStyle == FontStyle.Italic && richText.text.substring(it.start, it.end) == "italic" })
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
        assertEquals("root", root.description)
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
        assertEquals("Tom &amp; Jerry<br>second line", root.description)
        assertEquals(Color.Gray, root.color)
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
        assertTrue(a.path.leftX().dpVal > root.path.rightX().dpVal)
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
