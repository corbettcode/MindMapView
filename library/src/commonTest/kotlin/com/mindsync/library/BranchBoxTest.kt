package com.mindsync.library

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.BranchBox
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.layout.OwnedBranches
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BranchBoxTest {
    // A box as an org chart's row below a topic would have: as far left as right, and far below it.
    private val rowBox = BranchBox(left = Dp(200f), right = Dp(200f), top = Dp(20f), bottom = Dp(300f))

    private class StubBranches(private val box: BranchBox) : OwnedBranches {
        val arranged = mutableListOf<String>()

        override fun boxOf(
            owner: NodeData<*>
            , tree: Tree<*>
        ): BranchBox = box

        override fun arrangeBelow(
            owner: NodeData<*>
            , tree: Tree<*>
        ) {
            arranged.add(owner.id)
        }
    }

    // The root with "a", "b" and "c" below it on its right, and "d" below "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(600f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B")
        addNode("c", rootId, "C")
        addNode("d", "b", "D")
    }

    private fun Tree<*>.center(id: String) = getNode(id).path.let { it.centerX.dpVal to it.centerY.dpVal }

    @Test
    fun branchesWithoutAStructureOfTheirOwnAreLaidOutAsBefore() {
        val plain = tree().also { MindMapLayoutManager(LayoutSide.RIGHT).arrangeChildren(it.getRootNode(), it) }
        val hooked = tree().also { tree ->
            MindMapLayoutManager(LayoutSide.RIGHT)
                .apply { ownedBranches = StubBranches(rowBox) }
                .arrangeChildren(tree.getRootNode(), tree)
        }

        listOf("a", "b", "c", "d").forEach { assertEquals(plain.center(it), hooked.center(it)) }
    }

    @Test
    fun aBranchWithAStructureOfItsOwnTakesTheRoomOfItsBox() {
        val tree = tree().apply { setBranchStructure("b", MapStructure.ORG_DOWN) }
        val branches = StubBranches(rowBox)
        val manager = MindMapLayoutManager(LayoutSide.RIGHT).apply { ownedBranches = branches }

        manager.arrangeChildren(tree.getRootNode(), tree)

        val root = tree.getRootNode()
        val (bx, by) = tree.center("b")
        // Its row reaches back 200 from it, so it is that far past the root's edge and the gap.
        assertEquals((root.path.centerX + halfWidth(root)).dpVal + 50f + 200f, bx, 0.01f)
        // Its siblings are clear of the whole box, 20 above and 300 below it.
        assertTrue(tree.center("a").second + halfHeight(tree.getNode("a")).dpVal + 50f <= by - 20f + 0.01f)
        assertTrue(tree.center("c").second - halfHeight(tree.getNode("c")).dpVal - 50f >= by + 300f - 0.01f)
        // What is below it is left to its own structure.
        assertEquals(listOf("b"), branches.arranged)
    }

    @Test
    fun downwardBranchesReadTheBoxTurned() {
        val tree = tree().apply { setBranchStructure("b", MapStructure.LOGIC_RIGHT) }
        val box = BranchBox(left = Dp(10f), right = Dp(250f), top = Dp(15f), bottom = Dp(15f))
        val manager = MindMapLayoutManager(LayoutSide.RIGHT, GrowDirection.DOWN, isAllRootChildren = true)
            .apply { ownedBranches = StubBranches(box) }

        manager.arrangeChildren(tree.getRootNode(), tree)

        // Side by side below the root, "c" is clear of the 250 "b" reaches to its right.
        val (bx, _) = tree.center("b")
        val cLeft = tree.center("c").first - halfWidth(tree.getNode("c")).dpVal
        assertEquals(bx + 250f + 20f, cLeft, 0.01f)
    }
}
