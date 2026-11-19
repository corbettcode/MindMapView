package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.braceOf
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BraceMapTest {
    private val density = Density(1f)

    // The root with "a", "b" and "c" below it, and "d" and "e" below "b".
    private fun tree(structure: MapStructure = MapStructure.BRACE_RIGHT): Tree<Any> =
        Tree<Any>(Dp(400f), Dp(300f)).apply {
            val rootId = getRootNode().id
            listOf("a", "b", "c").forEach { addNode(it, rootId, it.uppercase()) }
            addNode("d", "b", "D")
            addNode("e", "b", "E")
            this.structure = structure
        }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.left(id: String) = getNode(id).let { x(id) - halfWidth(it).dpVal }
    private fun Tree<*>.right(id: String) = getNode(id).let { x(id) + halfWidth(it).dpVal }
    private fun Tree<*>.top(id: String) = getNode(id).let { y(id) - halfHeight(it).dpVal }
    private fun Tree<*>.bottom(id: String) = getNode(id).let { y(id) + halfHeight(it).dpVal }

    @Test
    fun braceSpansTheChildrenWithItsPointToTheParent() {
        val tree = tree().arranged()

        val brace = assertNotNull(with(density) { braceOf(tree, tree.getNode("b")) })

        assertEquals(tree.top("d"), brace.top, 0.01f)
        assertEquals(tree.bottom("e"), brace.bottom, 0.01f)
        assertTrue(brace.backX < tree.left("d"))
        assertTrue(brace.pointX < brace.backX, "the point faces the parent")
        assertEquals(tree.right("b"), brace.nodeX, 0.01f)
        assertEquals(tree.y("b"), brace.pointY, 0.01f)
    }

    @Test
    fun aTopicWithoutChildrenHasNoBrace() {
        val tree = tree().arranged()

        assertNull(with(density) { braceOf(tree, tree.getNode("a")) })
        assertNull(with(density) { braceOf(tree, tree.getNode("b").also { tree.setFolded("b", true) }) })
    }

    private fun ComposeUiTest.svgOf(structure: MapStructure): String {
        val state = MindMapState(tree(structure))
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return assertNotNull(state.exportSvg())
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    @Test
    fun aBraceForEachParentTakesThePlaceOfItsLines() {
        var logic = ""
        var brace = ""
        runComposeUiTest { logic = svgOf(MapStructure.LOGIC_RIGHT) }
        runComposeUiTest { brace = svgOf(MapStructure.BRACE_RIGHT) }

        // Five lines to the five children, in place of two braces: one from the root, one from "b".
        assertEquals(logic.pathCount() - 5 + 2, brace.pathCount())
    }
}
