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
import com.mindsync.library.model.MapStructure
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
    fun featureTourAsAnOrgChartLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/org-chart-feature-tour.png"))
        val tree = MindMapViewFile.read(File("../desktop/feature_tour.ccmm").readText())
        tree.structure = MapStructure.ORG_DOWN
        val state = shownState(tree)

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
    fun featureTourAsAFishboneLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/fishbone-feature-tour.png"))
        val tree = MindMapViewFile.read(File("../desktop/feature_tour.ccmm").readText())
        tree.structure = MapStructure.FISHBONE_RIGHT
        val state = shownState(tree)

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

    // A project by rows Plan, Build and Ship, their topics in quarters by their labels, Ideas with none, and a branch
    // in a cell.
    private fun projectMatrix(): Tree<Any> = Tree<Any>().apply {
        val root = getRootNode()
        setRootNode(root.copy(description = "<p>Project</p>"))
        listOf("Plan", "Build", "Ship").forEach { addNode(it, root.id, it) }
        mapOf(
            "Goals" to ("Plan" to "Q1"), "Risks" to ("Plan" to "Q2"), "Library" to ("Build" to "Q1")
            , "App" to ("Build" to "Q1"), "Docs" to ("Build" to "Q2"), "Ideas" to ("Build" to null)
            , "Release" to ("Ship" to "Q3")
        ).forEach { (id, place) ->
            addNode(id, place.first, id)
            setLabel(id, place.second)
        }
        addNode("Beta", "Release", "Beta")
        addNode("Launch", "Release", "Launch")
        structure = MapStructure.MATRIX_ROWS
    }

    @Test
    fun projectMatrixLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/matrix-project.png"))
        val state = shownState(projectMatrix())

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

    // The costs of a trip as a tree table: leaves at two depths, a summary past the table.
    private fun tripCosts(): Tree<Any> = Tree<Any>().apply {
        val root = getRootNode()
        setRootNode(root.copy(description = "<p>Trip costs</p>"))
        addNode("Travel", root.id, "Travel")
        addNode("Flights", "Travel", "Flights")
        addNode("Out", "Flights", "Out: 320")
        addNode("Back", "Flights", "Back: 290")
        addNode("Train", "Travel", "Train: 45")
        addNode("Stay", root.id, "Stay")
        addNode("Hotel", "Stay", "Hotel, 4 nights: 560")
        addNode("Most", "Stay", "Most")
        setSummary("Most", setOf("Hotel"))
        addNode("Food", root.id, "Food: 200")
        structure = MapStructure.TREE_TABLE
    }

    @Test
    fun tripCostsTreeTableLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/tree-table-costs.png"))
        val state = shownState(tripCosts())

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

    // A launch plan as a grid of three columns: five main topics, a summary in a cell and one below the grid.
    private fun launchPlan(): Tree<Any> = Tree<Any>().apply {
        val root = getRootNode()
        setRootNode(root.copy(description = "<p>Launch plan</p>"))
        addNode("Research", root.id, "Research")
        addNode("Users", "Research", "Talk to users")
        addNode("Market", "Research", "Market size")
        addNode("Build", root.id, "Build")
        addNode("App", "Build", "App")
        addNode("iOS", "App", "iOS")
        addNode("API", "Build", "API")
        addNode("Core", "Build", "Core")
        setSummary("Core", setOf("App", "API"))
        addNode("Marketing", root.id, "Marketing")
        addNode("Site", "Marketing", "Web site")
        addNode("Sales", root.id, "Sales")
        addNode("Support", root.id, "Support")
        addNode("Docs", "Support", "Docs")
        addNode("Everything", root.id, "Everything")
        setSummary("Everything", setOf("Research", "Support"))
        setGridColumns(root.id, 3)
        structure = MapStructure.GRID
    }

    @Test
    fun launchPlanGridLooksAsBefore() = runComposeUiTest {
        val golden: BufferedImage = ImageIO.read(File("src/jvmTest/resources/grid-plan.png"))
        val state = shownState(launchPlan())

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
