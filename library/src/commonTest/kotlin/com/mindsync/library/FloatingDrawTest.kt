package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class FloatingDrawTest {
    // The root with "p" and "f" on its right; "g" and "h" below "f". "f" floats when [isFloating].
    private fun ComposeUiTest.svgOf(isFloating: Boolean): String {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("f", rootId, "F")
            listOf("g", "h").forEach { addNode(it, "f", it.uppercase()) }
            if (isFloating) setFloating("f", DpOffset(Dp(0f), Dp(400f)))
        }
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return assertNotNull(state.exportSvg())
    }

    private fun String.pathCount() = Regex("<path").findAll(this).count()

    @Test
    fun floatingTopicHasNoLineFromTheRoot() {
        var attached = ""
        var floating = ""
        runComposeUiTest { attached = svgOf(isFloating = false) }
        runComposeUiTest { floating = svgOf(isFloating = true) }

        assertEquals(attached.pathCount() - 1, floating.pathCount())
    }
}
