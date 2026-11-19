package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MapStructureFilesTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply { addNode("a", getRootNode().id, "A") }

    @Test
    fun everyStructureIsKept() {
        MapStructure.entries.forEach { structure ->
            val text = MindMapViewFile.write(tree().apply { this.structure = structure })

            assertEquals(structure, MindMapViewFile.read(text).structure, structure.name)
        }
    }

    @Test
    fun mindMapIsWrittenAsBefore() {
        assertFalse("structure" in MindMapViewFile.write(tree()))
    }

    @Test
    fun unknownStructureIsAMindMap() {
        val text = MindMapViewFile.write(tree().apply { structure = MapStructure.ORG_DOWN })
            .replace("org-chart-down", "tree-table")

        assertEquals(MapStructure.MIND_MAP, MindMapViewFile.read(text).structure)
    }

    @Test
    fun xmindNamesAreRead() {
        assertEquals(MapStructure.LOGIC_RIGHT, MapStructure.fromXMindClass("org.xmind.ui.logic.right"))
        assertEquals(MapStructure.ORG_DOWN, MapStructure.fromXMindClass("org.xmind.ui.org-chart.down"))
        assertEquals(MapStructure.TIMELINE, MapStructure.fromXMindClass("org.xmind.ui.timeline.horizontal"))
        assertEquals(MapStructure.MIND_MAP, MapStructure.fromXMindClass("org.xmind.ui.map.clockwise"))
        assertEquals(MapStructure.FISHBONE_LEFT, MapStructure.fromXMindClass("org.xmind.ui.fishbone.leftHeaded"))
        assertEquals(MapStructure.FISHBONE_RIGHT, MapStructure.fromXMindClass("org.xmind.ui.fishbone.rightHeaded"))
        assertEquals(MapStructure.MATRIX_ROWS, MapStructure.fromXMindClass("org.xmind.ui.spreadsheet"))
        assertEquals(MapStructure.MATRIX_COLUMNS, MapStructure.fromXMindClass("org.xmind.ui.spreadsheet.column"))
        assertEquals(MapStructure.MIND_MAP, MapStructure.fromXMindClass("org.xmind.ui.treetable"))
        assertEquals(MapStructure.MIND_MAP, MapStructure.fromXMindClass(null))
    }
}
