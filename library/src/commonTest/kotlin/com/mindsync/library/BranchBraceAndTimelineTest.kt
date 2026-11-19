package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BranchBraceAndTimelineTest {
    // The root with "a", "b" and "c" below it, "d" and "e" below "b", and "f" and "g" below "d" and "e".
    private fun tree(structure: MapStructure = MapStructure.MIND_MAP): Tree<Any> =
        Tree<Any>(Dp(400f), Dp(500f)).apply {
            val rootId = getRootNode().id
            listOf("a", "b", "c").forEach { addNode(it, rootId, "Topic ${it.uppercase()}") }
            addNode("d", "b", "Event D")
            addNode("e", "b", "Event E")
            addNode("f", "d", "Detail F")
            addNode("g", "e", "Detail G")
            this.structure = structure
        }

    private fun ComposeUiTest.svgOf(tree: Tree<Any>): String {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return assertNotNull(state.exportSvg())
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    private fun laidOut(tree: Tree<Any>): Tree<Any> = tree.apply {
        MindMapManager().apply {
            setTree(tree)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal

    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal

    private fun Tree<*>.left(id: String) = x(id) - halfWidth(getNode(id)).dpVal

    private fun Tree<*>.right(id: String) = x(id) + halfWidth(getNode(id)).dpVal

    private fun Tree<*>.top(id: String) = y(id) - halfHeight(getNode(id)).dpVal

    private fun Tree<*>.bottom(id: String) = y(id) + halfHeight(getNode(id)).dpVal

    @Test
    fun braceIsDrawnOnlyInTheBraceBranch() {
        var logic = ""
        var braced = ""
        runComposeUiTest { logic = svgOf(tree(MapStructure.LOGIC_RIGHT)) }
        runComposeUiTest {
            braced = svgOf(tree(MapStructure.LOGIC_RIGHT).apply { setBranchStructure("b", MapStructure.BRACE_RIGHT) })
        }

        // "b"'s two lines, and the one line below each of "d" and "e", are braces in a brace branch.
        assertEquals(logic.pathCount() - 4 + 3, braced.pathCount())
    }

    @Test
    fun logicBranchOfABraceMapHasItsLines() {
        var brace = ""
        var mixed = ""
        runComposeUiTest { brace = svgOf(tree(MapStructure.BRACE_RIGHT)) }
        runComposeUiTest {
            mixed = svgOf(tree(MapStructure.BRACE_RIGHT).apply { setBranchStructure("b", MapStructure.LOGIC_RIGHT) })
        }

        // "b", "d" and "e" have lines in place of their three braces.
        assertEquals(brace.pathCount() - 3 + 4, mixed.pathCount())
    }

    @Test
    fun timelineBranchHasItsAxisFromItsTopicAndItsBranchesByTurns() {
        val tree = laidOut(tree().apply { setBranchStructure("b", MapStructure.TIMELINE) })

        assertEquals(tree.y("b"), tree.y("d"), 0.01f)
        assertEquals(tree.y("b"), tree.y("e"), 0.01f)
        assertTrue(tree.left("d") > tree.right("b") && tree.left("e") > tree.right("d"))
        assertEquals(GrowDirection.UP, tree.directionOf("d"))
        assertEquals(GrowDirection.DOWN, tree.directionOf("e"))
        assertTrue(tree.bottom("f") < tree.top("d") && tree.top("g") > tree.bottom("e"))
        val (start, end) = assertNotNull(with(Density(1f)) { timelineAxis(tree, tree.getNode("b")) })
        assertEquals(tree.right("b"), start.x, 0.5f)
        assertEquals(tree.x("e"), end.x, 0.5f)
        assertEquals(tree.y("b"), start.y, 0.5f)
    }

    @Test
    fun timelineBranchIsClearOfItsSiblings() {
        val tree = laidOut(tree().apply { setBranchStructure("b", MapStructure.TIMELINE) })

        val rootRight = tree.right(tree.getRootNode().id)
        listOf("b", "d", "e", "f", "g").forEach { id ->
            assertTrue(tree.left(id) > rootRight, "$id is right of the root")
            assertTrue(tree.top(id) > tree.bottom("a") && tree.bottom(id) < tree.top("c"), "$id is between a and c")
        }
    }
}
