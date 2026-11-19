package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.FISHBONE_SLANT
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class FishboneDrawTest {
    // The effect with "a" and "b" on it, "a1" a rib of "a" and "b1" one of "b".
    private fun tree(structure: MapStructure = MapStructure.FISHBONE_LEFT): Tree<Any> =
        Tree<Any>(Dp(400f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "Cause A")
            addNode("b", rootId, "Cause B")
            addNode("a1", "a", "Rib A1")
            addNode("b1", "b", "Rib B1")
            this.structure = structure
        }

    private fun laidOut(tree: Tree<Any>): Tree<Any> = tree.apply {
        MindMapManager().apply {
            setTree(tree)
            arrangeTree()
        }
    }

    @Test
    fun spineRunsFromTheHeadToPastTheLastJoint() {
        val tree = laidOut(tree())
        val head = tree.getRootNode()
        val lines = assertNotNull(with(Density(1f)) { fishboneLines(tree, head) })

        val (start, end) = lines.spine
        assertEquals((head.path.centerX + halfWidth(head)).dpVal, start.x, 0.5f)
        assertEquals(head.path.centerY.dpVal, start.y, 0.5f)
        assertEquals(lines.bones.maxOf { it.second.first.x } + 40f, end.x, 0.5f)
    }

    @Test
    fun bonesSlantFromTheSpineToTheirCausesAndRibsRunAcrossToTheirTopics() {
        val tree = laidOut(tree())
        val lines = assertNotNull(with(Density(1f)) { fishboneLines(tree, tree.getRootNode()) })
        val spineY = tree.getRootNode().path.centerY.dpVal

        val (id, bone) = lines.bones.first()
        val cause = tree.getNode(id)
        assertEquals("a", id)
        assertEquals(spineY, bone.first.y, 0.5f)
        assertEquals((cause.path.centerY + halfHeight(cause)).dpVal, bone.second.y, 0.5f)
        assertEquals(abs(bone.second.y - bone.first.y) * FISHBONE_SLANT, bone.second.x - bone.first.x, 0.5f)
        val (ribId, rib) = lines.ribs.first()
        val ribTopic = tree.getNode(ribId)
        assertEquals("a1", ribId)
        assertEquals(rib.first.y, rib.second.y, 0.01f)
        assertEquals((ribTopic.path.centerX - halfWidth(ribTopic)).dpVal, rib.second.x, 0.5f)
        assertEquals(bone.first.x + abs(spineY - rib.first.y) * FISHBONE_SLANT, rib.first.x, 0.5f)
    }

    private fun ComposeUiTest.svgOf(tree: Tree<Any>): String {
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return assertNotNull(state.exportSvg())
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    @Test
    fun exportHasTheSpineBonesAndRibsInPlaceOfLines() {
        var logic = ""
        var fishbone = ""
        runComposeUiTest { logic = svgOf(tree(MapStructure.LOGIC_RIGHT)) }
        runComposeUiTest { fishbone = svgOf(tree()) }

        // Four lines, in place of a spine, two bones and two ribs.
        assertEquals(logic.pathCount() - 4 + 5, fishbone.pathCount())
    }
}
