package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.layout.CellRect
import com.corbettcode.mindmapview.library.layout.TableCells
import com.corbettcode.mindmapview.library.layout.TableLine
import kotlin.test.Test
import kotlin.test.assertEquals

class TableCellsTest {
    @Test
    fun edgesTheCellsShareAreDrawnOnceAndThoseAlongOneLineAsOne() {
        // Two cells side by side over one as wide as both.
        val cells = TableCells.of(
            listOf(CellRect(0f, 0f, 10f, 5f), CellRect(10f, 0f, 30f, 5f), CellRect(0f, 5f, 30f, 12f))
        )

        assertEquals(
            setOf(
                TableLine(0f, 0f, 0f, 12f), TableLine(10f, 0f, 10f, 5f), TableLine(30f, 0f, 30f, 12f)
                , TableLine(0f, 0f, 30f, 0f), TableLine(0f, 5f, 30f, 5f), TableLine(0f, 12f, 30f, 12f)
            )
            , cells.lines.toSet()
        )
        assertEquals(6, cells.lines.size)
    }

    @Test
    fun ruleIsDrawnHeavierInPlaceOfTheEdgesAlongIt() {
        val rule = TableLine(0f, 5f, 30f, 5f)
        val cells = TableCells.of(
            listOf(CellRect(0f, 0f, 30f, 5f), CellRect(0f, 5f, 10f, 12f), CellRect(10f, 5f, 30f, 12f))
            , listOf(rule)
        )

        assertEquals(listOf(rule.copy(isHeavy = true)), cells.lines.filter { it.y1 == 5f && it.y2 == 5f })
        assertEquals(1, cells.lines.count { it.isHeavy })
    }

    @Test
    fun boundsTakeInEveryCell() {
        val bounds = TableCells.of(listOf(CellRect(-4f, 2f, 6f, 8f), CellRect(6f, -1f, 20f, 2f))).bounds

        assertEquals(listOf(-4f, -1f, 20f, 8f), listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))
    }
}
