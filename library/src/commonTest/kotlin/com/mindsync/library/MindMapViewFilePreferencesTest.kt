package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MindMapViewFilePreferencesTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("main", getRootNode().id, "Main")
    }

    @Test
    fun preferencesRoundTrip() {
        val preferences = mapOf("nodeColors" to "#123456,#FFFFFF", "backgroundColor" to "", "odd" to "a\"<b>&c")

        val written = MindMapViewFile.write(tree(), preferences = preferences)

        assertEquals(preferences, MindMapViewFile.readPreferences(written))
        assertEquals("Main", MindMapViewFile.read(written).getNode("main").description)
    }

    @Test
    fun fileWithoutPreferencesHasNone() {
        assertNull(MindMapViewFile.readPreferences(MindMapViewFile.write(tree())))
    }

    @Test
    fun contentThatIsNoMapHasNoPreferences() {
        assertNull(MindMapViewFile.readPreferences("<map/>"))
    }
}
