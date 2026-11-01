package com.mindsync.library

import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.MapThemes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OldDefaultColorsTest {
    @Test
    fun mapsSavedWithEarlierDefaultsFollowTheTheme() {
        val ocean = MapThemes.byId("ocean")
        listOf("weekly_planner.mmv", "testwith_6_axis.mmv").forEach { name ->
            val tree = MindMapViewFile.read(File("../desktop/$name").readText()).apply { theme = ocean }

            tree.doPreorderTraversal { node ->
                val expected = tree.levelStyleOf(node.id).fill
                assertEquals(expected, tree.fillOf(node.id), "$name: ${node.description}")
            }
        }
    }
}
