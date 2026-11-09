package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.io.XmlElement
import com.mindsync.library.io.XmlParser
import com.mindsync.library.model.MapThemes
import com.mindsync.library.util.Dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ThemeDrawingTest {
    private fun ComposeUiTest.shownState(tree: Tree<Any>): MindMapState {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(1200.dp, 900.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun treeWithChildren(): Tree<Any> = Tree<Any>(Dp(150f), Dp(300f)).apply {
        addNode("child0", getRootNode().id, "child 0")
        addNode("child1", getRootNode().id, "child 1")
        addNode("grandchild", "child0", "grandchild")
    }

    private fun ImageBitmap.argbPixels(): Set<Int> {
        val image = toAwtImage()
        return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }.toSet()
    }

    private fun Set<Int>.has(color: Color): Boolean = color.toArgb() in this

    @Test
    fun classicFeatureTourLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/classic-feature-tour.png"))
        val state = shownState(MindMapViewFile.read(File("../desktop/feature_tour.ccmm").readText()))

        val image = assertNotNull(state.exportImage(pixelsPerDp = 1f)).toAwtImage()

        assertEquals(golden.width, image.width)
        assertEquals(golden.height, image.height)
        var differing = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val a = golden.getRGB(x, y)
            val b = image.getRGB(x, y)
            val isDifferent = listOf(0, 8, 16, 24).any { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) > 8 }
            if (isDifferent) differing++
        }
        assertTrue(differing <= image.width * image.height / 200, "$differing pixels differ")
    }

    @Test
    fun oceanColorsTheNodes() = runComposeUiTest {
        val state = shownState(treeWithChildren())
        val ocean = MapThemes.byId("ocean")
        val classic = assertNotNull(state.exportImage()).argbPixels()

        state.setTheme(ocean)
        val pixels = assertNotNull(state.exportImage()).argbPixels()

        assertTrue(pixels.has(assertNotNull(ocean.mainTopic.fill)))
        assertTrue(pixels.has(assertNotNull(ocean.root.fill)))
        assertFalse(classic.has(assertNotNull(ocean.mainTopic.fill)))
        assertFalse(classic.has(assertNotNull(ocean.root.fill)))
    }

    @Test
    fun handColoredNodeKeepsItsColor() = runComposeUiTest {
        val coral = Color(0xFFFF7F50)
        val state = shownState(treeWithChildren().apply { updateNodeColor("child1", coral) })

        state.setTheme(MapThemes.byId("night"))

        assertTrue(assertNotNull(state.exportImage()).argbPixels().has(coral))
    }

    @Test
    fun exportUsesTheThemesBackground() = runComposeUiTest {
        val night = MapThemes.byId("night")
        val state = shownState(treeWithChildren())
        state.setTheme(night)

        val image = assertNotNull(state.exportImage()).toAwtImage()
        assertEquals(night.background.toArgb(), image.getRGB(0, 0))
        val svg = XmlParser(assertNotNull(state.exportSvg())).parse().single { it.name == "svg" }
        fun XmlElement.all(): List<XmlElement> = listOf(this) + children.flatMap { it.all() }
        val background = svg.all().drop(1).first { it.name in setOf("rect", "path") }
        assertEquals("#1E1F26", background.attributes["fill"]?.uppercase(), background.attributes.toString())
    }

    @Test
    fun themeLineColorsAreDrawn() = runComposeUiTest {
        val paper = MapThemes.byId("paper")
        val state = shownState(treeWithChildren())
        val firstBranch = MapThemes.CLASSIC.branchColors.first()
        assertTrue(assertNotNull(state.exportImage()).argbPixels().has(firstBranch))

        state.setTheme(paper)
        val pixels = assertNotNull(state.exportImage()).argbPixels()

        assertFalse(pixels.has(firstBranch))
        assertTrue(pixels.has(paper.lineColor))
    }
}
