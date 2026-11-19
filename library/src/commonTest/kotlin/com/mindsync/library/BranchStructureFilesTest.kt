package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BranchStructureFilesTest {
    // The root with "a" below it and "b" below "a".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
    }

    @Test
    fun everyBranchStructureIsKept() {
        MapStructure.entries.filter { it != MapStructure.MIND_MAP }.forEach { structure ->
            val text = MindMapViewFile.write(tree().apply { setBranchStructure("b", structure) })

            val read = MindMapViewFile.read(text)
            assertEquals(structure, read.branchStructureOf("b"), structure.name)
            assertNull(read.branchStructureOf("a"))
        }
    }

    @Test
    fun branchesLaidOutAsTheirParentsAreWrittenAsBefore() {
        assertFalse("structure" in MindMapViewFile.write(tree()))
    }

    @Test
    fun unknownStructureAndTheMindMapAreReadAsNone() {
        val text = MindMapViewFile.write(tree().apply { setBranchStructure("b", MapStructure.TIMELINE) })

        listOf("tree-table", "mind-map").forEach { name ->
            val read = MindMapViewFile.read(text.replace("structure=\"timeline\"", "structure=\"$name\""))
            assertNull(read.branchStructureOf("b"), name)
        }
    }
}
