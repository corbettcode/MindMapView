package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GridDrawTest {
    // Research with Users and Size below it, and Build with App (and iOS below that) and API.
    private fun tree(structure: MapStructure): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("research", rootId, "Research")
        addNode("users", "research", "Users")
        addNode("size", "research", "Size")
        addNode("build", rootId, "Build")
        addNode("app", "build", "App")
        addNode("ios", "app", "iOS")
        addNode("api", "build", "API")
        this.structure = structure
    }

    private fun ComposeUiTest.shown(tree: Tree<Any>): MindMapState {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    @Test
    fun cellsAreDrawnInPlaceOfTheLinesToTheMainTopicsAndToWhatIsBelowThem() {
        var logic = ""
        var grid = ""
        var tableLines = 0
        runComposeUiTest { logic = assertNotNull(shown(tree(MapStructure.LOGIC_RIGHT)).exportSvg()) }
        runComposeUiTest {
            val state = shown(tree(MapStructure.GRID))
            tableLines = assertNotNull(state.mindMapManager.tableCellsOf(state.tree.getRootNode())).lines.size
            grid = assertNotNull(state.exportSvg())
        }

        // Across: the header's top, the rule under it, the edge between the cells' parts and the bottom. Down: the
        // two sides and the edge between the two cells.
        assertEquals(7, tableLines)
        // Of the seven lines, only App's to iOS is left.
        assertEquals(logic.pathCount() - 6 + tableLines, grid.pathCount())
    }

    @Test
    fun exportTakesInTheWholeGrid() = runComposeUiTest {
        val state = shown(tree(MapStructure.GRID))
        val cells = assertNotNull(state.mindMapManager.tableCellsOf(state.tree.getRootNode())).bounds

        val bounds = with(Density(1f)) { exportBounds(state.tree, state.mindMapManager.tableBounds()) }
        assertTrue(bounds.left < cells.left && bounds.right > cells.right)
        assertTrue(bounds.top < cells.top && bounds.bottom > cells.bottom)
    }
}
