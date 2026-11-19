package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class TreeTableDrawTest {
    // Travel with Flights (Out, Back) and Train, and Food on its own: seven topics below the root.
    private fun tree(structure: MapStructure): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("travel", rootId, "Travel")
        addNode("flights", "travel", "Flights")
        addNode("out", "flights", "Out")
        addNode("back", "flights", "Back")
        addNode("train", "travel", "Train")
        addNode("food", rootId, "Food")
        addNode("snacks", "food", "Snacks")
        this.structure = structure
    }

    // The SVG the map exports, with how many lines its tables are drawn with.
    private fun ComposeUiTest.svgOf(tree: Tree<Any>): Pair<String, Int> {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        val tableLines = state.mindMapManager.tableCellsOf(tree.getRootNode())?.lines?.size ?: 0
        return assertNotNull(state.exportSvg()) to tableLines
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    @Test
    fun tableIsDrawnInPlaceOfTheLinesBetweenItsTopics() {
        var logic = "" to 0
        var table = "" to 0
        runComposeUiTest { logic = svgOf(tree(MapStructure.LOGIC_RIGHT)) }
        runComposeUiTest { table = svgOf(tree(MapStructure.TREE_TABLE)) }

        // Across: the header's top, the rule under it, the edges between the four rows and the bottom. Down: the two
        // sides, the right of the first column, and the right of Flights, beside Out and Back.
        assertEquals(10, table.second)
        assertEquals(logic.first.pathCount() - 7 + table.second, table.first.pathCount())
    }

    @Test
    fun topicWithAStructureOfItsOwnKeepsItsLines() {
        var logic = "" to 0
        var table = "" to 0
        runComposeUiTest {
            logic = svgOf(tree(MapStructure.LOGIC_RIGHT).apply { setBranchStructure("flights", MapStructure.ORG_DOWN) })
        }
        runComposeUiTest {
            table = svgOf(tree(MapStructure.TREE_TABLE).apply { setBranchStructure("flights", MapStructure.ORG_DOWN) })
        }

        // Of the seven lines, Flights keeps its two, to Out and Back.
        assertEquals(logic.first.pathCount() - 5 + table.second, table.first.pathCount())
    }
}
