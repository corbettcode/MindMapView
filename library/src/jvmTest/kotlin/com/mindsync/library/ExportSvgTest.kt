package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.io.XmlElement
import com.mindsync.library.io.XmlParser
import com.mindsync.library.util.Dp
import kotlin.math.abs
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExportSvgTest {
    private fun ComposeUiTest.shownState(tree: Tree<Any>): MindMapState {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    // The root with [childCount] children, "child0" first.
    private fun treeWithChildren(childCount: Int = 2): Tree<Any> = Tree<Any>(Dp(150f), Dp(300f)).apply {
        repeat(childCount) { addNode("child$it", getRootNode().id, "child $it") }
    }

    private fun svgOf(state: MindMapState): XmlElement =
        XmlParser(assertNotNull(state.exportSvg())).parse().single { it.name == "svg" }

    private fun XmlElement.all(): List<XmlElement> = listOf(this) + children.flatMap { it.all() }

    private fun XmlElement.texts(): String = all().filter { it.name == "text" }.joinToString("\n") { it.text }

    @Test
    fun thereIsNoSvgBeforeTheMapIsShown() {
        assertNull(MindMapState(treeWithChildren()).exportSvg())
    }

    @Test
    fun rootOnlyMapIsOneShape() = runComposeUiTest {
        val tree = Tree<Any>(Dp(150f), Dp(300f)).apply {
            setRootNode(getRootNode().copy(description = "Lonely"))
        }

        val svg = svgOf(shownState(tree))

        assertTrue("Lonely" in svg.texts(), svg.texts())
    }

    @Test
    fun svgIsTheSizeOfTheMapInDp() = runComposeUiTest {
        val state = shownState(treeWithChildren())
        val image = assertNotNull(state.exportImage(pixelsPerDp = 1f))

        val svg = svgOf(state)

        val width = assertNotNull(svg.attributes["width"]).toFloat()
        val height = assertNotNull(svg.attributes["height"]).toFloat()
        assertTrue(abs(width - image.width) <= 1f, "$width wide against ${image.width}")
        assertTrue(abs(height - image.height) <= 1f, "$height high against ${image.height}")
    }

    @Test
    fun nodeTextsAreText() = runComposeUiTest {
        val svg = svgOf(shownState(treeWithChildren()))

        listOf("child 0", "child 1").forEach { text -> assertTrue(text in svg.texts(), "$text in ${svg.texts()}") }
    }

    @Test
    fun foldedNodesAreNotDrawn() = runComposeUiTest {
        val tree = treeWithChildren(childCount = 1).apply {
            addNode("hidden", "child0", "hidden one")
            setFolded("child0", true)
        }

        val svg = svgOf(shownState(tree))

        assertTrue("child 0" in svg.texts())
        assertFalse("hidden one" in svg.texts())
    }

    @Test
    fun arrowLinksAreDrawn() = runComposeUiTest {
        val state = shownState(treeWithChildren())
        val paths = svgOf(state).all().count { it.name == "path" }

        state.tree.addArrowLink(ArrowLink("link", "child0", "child1"))
        state.animateTreeChange()
        waitForIdle()

        assertTrue(svgOf(state).all().count { it.name == "path" } > paths)
    }

    // The SVG drawn by Skia's own SVG renderer, as a browser would show it.
    private fun renderedPixels(svg: String): IntArray {
        val dom = SVGDOM(Data.makeFromBytes(svg.encodeToByteArray()))
        val root = assertNotNull(dom.root)
        val size = root.width.value.toInt() to root.height.value.toInt()
        val bitmap = Bitmap().apply { allocN32Pixels(size.first, size.second) }
        val canvas = Canvas(bitmap)
        canvas.clear(0xFFFFFFFF.toInt())
        dom.setContainerSize(size.first.toFloat(), size.second.toFloat())
        dom.render(canvas)
        return IntArray(size.first * size.second) { bitmap.getColor(it % size.first, it / size.first) }
    }

    @Test
    fun arrowLinksAreDrawnBesideALineNode() = runComposeUiTest {
        val chosen = 0xFFFF0000.toInt()
        val tree = treeWithChildren().apply {
            addNode("line", getRootNode().id, "a line node", shape = NodeShape.LINE)
            addArrowLink(ArrowLink("link", "child0", "child1", color = Color(chosen)))
        }

        val pixels = renderedPixels(assertNotNull(shownState(tree).exportSvg()))

        assertTrue(pixels.count { it == chosen } > 20, "${pixels.count { it == chosen }} pixels of the link")
    }

    @Test
    fun backgroundIsWhite() = runComposeUiTest {
        val svg = svgOf(shownState(treeWithChildren()))

        val first = svg.all().drop(1).first { it.name in setOf("rect", "path") }
        assertTrue(first.attributes["fill"]?.uppercase() in setOf("#FFFFFF", "WHITE"), first.attributes.toString())
    }
}
