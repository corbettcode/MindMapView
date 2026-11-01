package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapThemes
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapThemeTreeTest {
    private val coral = Color(0xFFFF7F50)
    private val ocean = MapThemes.byId("ocean")
    private val night = MapThemes.byId("night")
    private val paper = MapThemes.byId("paper")

    // The root, a main topic "main" and a subtopic "sub" below it, in their shapes' own colors.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("main", getRootNode().id, "Main")
        addNode("sub", "main", "Sub")
        addNode("circle", "main", "Circle", NodeShape.CIRCLE)
    }

    private fun stateOf(tree: Tree<Any>): MindMapState = MindMapState(tree).apply { setTree(tree) }

    @Test
    fun untouchedNodesFollowTheTheme() {
        val tree = tree().apply { theme = ocean }

        assertEquals(ocean.root.fill, tree.fillOf(tree.getRootNode().id))
        assertEquals(ocean.mainTopic.fill, tree.fillOf("main"))
        assertEquals(ocean.subtopic.fill, tree.fillOf("sub"))
        assertEquals(ocean.subtopic.text, tree.textColorOf("sub"))
        assertEquals(ocean.subtopic.fill, tree.fillOf("circle"))
    }

    @Test
    fun handColorsWin() {
        val tree = tree().apply { updateNodeColor("sub", coral) }

        MapThemes.all.forEach { theme ->
            tree.theme = theme
            assertEquals(coral, tree.fillOf("sub"), theme.id)
        }
    }

    @Test
    fun classicGivesTheStoredColors() {
        val tree = tree()

        listOf(tree.getRootNode().id, "main", "circle").forEach { id ->
            val node = tree.getNode(id)
            assertEquals(node.color, tree.fillOf(id), id)
            assertEquals(node.textColor, tree.textColorOf(id), id)
        }
    }

    @Test
    fun ownBorderWinsAndNoneTakesTheThemes() {
        val red = NodeBorder(NodeBorderStyle.LINE, color = Color.Red)
        val tree = tree().apply {
            theme = paper
            setBorder("main", red)
        }

        assertEquals(paper.subtopic.border, tree.drawnBorderOf("sub"))
        assertEquals(red, tree.drawnBorderOf("main"))
    }

    @Test
    fun lineNodeTextFollowsTheTheme() {
        val tree = tree().apply {
            addNode("line", "main", "Line", NodeShape.LINE)
            theme = night
        }

        assertEquals(night.lineNodeText, tree.textColorOf("line"))
    }

    @Test
    fun handColoredFillKeepsItsTextColor() {
        val tree = tree().apply {
            updateNodeColor("sub", coral)
            theme = night
        }

        assertEquals(tree.getNode("sub").textColor, tree.textColorOf("sub"))
    }

    @Test
    fun lineNodesTakeNoThemeBorder() {
        val tree = tree().apply {
            addNode("line", "main", "Line", NodeShape.LINE)
            theme = paper
        }

        assertTrue(tree.drawnBorderOf("line").isNone)
    }

    @Test
    fun handColorsSurviveThemeChanges() {
        val tree = tree().apply { updateNodeColor("sub", coral) }
        val state = stateOf(tree)

        listOf(ocean, night, MapThemes.CLASSIC).forEach { state.setTheme(it) }

        assertEquals(coral, state.tree.getNode("sub").color)
        assertEquals(coral, state.tree.fillOf("sub"))
    }

    @Test
    fun themeChangeIsOneUndoStep() {
        val state = stateOf(tree())

        state.setTheme(ocean)
        assertTrue(state.canUndo)
        state.undo()
        assertEquals(MapThemes.CLASSIC, state.tree.theme)
        state.redo()
        assertEquals(ocean, state.tree.theme)
    }

    @Test
    fun themeChangeMarksTheMapModified() {
        val state = stateOf(tree())
        state.markSaved()

        state.setTheme(ocean)
        assertTrue(state.isModified)
        state.undo()
        assertFalse(state.isModified)
    }

    @Test
    fun replacingTheNodesKeepsTheTheme() {
        val state = stateOf(tree())
        state.setTheme(night)

        state.replaceTree(tree())

        assertEquals(night, state.tree.theme)
    }
}
