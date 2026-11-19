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

        listOf("bubble", "mind-map").forEach { name ->
            val read = MindMapViewFile.read(text.replace("structure=\"timeline\"", "structure=\"$name\""))
            assertNull(read.branchStructureOf("b"), name)
        }
    }

    @Test
    fun gridColumnsOfTheRootAndOfABranchAreKept() {
        val tree = tree().apply {
            structure = MapStructure.GRID
            setGridColumns(getRootNode().id, 3)
            setBranchStructure("a", MapStructure.GRID)
            setGridColumns("a", 2)
        }

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))
        assertEquals(3, read.gridColumnsOf(read.getRootNode().id))
        assertEquals(2, read.gridColumnsOf("a"))
        assertNull(read.gridColumnsOf("b"))
    }

    @Test
    fun gridsOfAsManyColumnsAsFitAreWrittenAsBeforeAndNoCountIsReadAsThat() {
        val text = MindMapViewFile.write(tree().apply { setBranchStructure("a", MapStructure.GRID) })
        assertFalse("columns" in text)

        listOf("0", "two").forEach { count ->
            val read = MindMapViewFile.read(text.replace("structure=\"grid\"", "structure=\"grid\" columns=\"$count\""))
            assertNull(read.gridColumnsOf("a"), count)
        }
    }
}
