package com.mindsync.library

import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.Tree
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingFilesTest {
    // The root with "a" and "f" below it, and "g" below "f", which floats.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("f", rootId, "F")
        addNode("g", "f", "G")
        setFloating("f", DpOffset(Dp(120.5f), Dp(-80f)))
    }

    private fun Tree<*>.idOf(text: String) = nodes.values.single { it.description == text }.id

    @Test
    fun floatingTopicIsKeptWithItsOffset() {
        val read = MindMapViewFile.read(MindMapViewFile.write(tree()))

        val id = read.idOf("F")
        assertEquals(DpOffset(Dp(120.5f), Dp(-80f)), read.floatingOffsetOf(id))
        assertEquals(listOf(read.idOf("A"), id), read.getRootNode().children)
        assertEquals(listOf(read.idOf("G")), read.getNode(id).children)
        assertFalse(read.isFloating(read.idOf("A")))
    }

    @Test
    fun mapWithoutFloatingTopicsHasNoneWritten() {
        val text = MindMapViewFile.write(tree().apply { setFloating("f", null) })

        assertFalse("floating" in text)
        assertFalse(" dx=" in text)
    }

    @Test
    fun floatingWhereItCannotBeIsIgnored() {
        val text = MindMapViewFile.write(tree())
            .replace("floating=\"true\" dx=\"120.5\" dy=\"-80.0\"", "")
            .replace("<node id=\"g\"", "<node id=\"g\" floating=\"true\" dx=\"1\" dy=\"2\"")
        assertTrue("floating" in text)

        val read = MindMapViewFile.read(text)

        assertTrue(read.nodes.keys.none { read.isFloating(it) })
    }

    @Test
    fun floatingWithoutAnOffsetIsAttached() {
        val text = MindMapViewFile.write(tree()).replace(" dx=\"120.5\" dy=\"-80.0\"", "")

        val read = MindMapViewFile.read(text)

        assertFalse(read.isFloating(read.idOf("F")))
    }
}
