package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMap
import com.mindsync.library.MindMapState
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

class PdfExportTest {
    private fun svg(
        width: Int
        , height: Int
        , text: String = "Hello map"
        , fontFamily: String = "Inter"
    ): String = """
        <?xml version="1.0" encoding="utf-8" ?>
        <svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height">
            <rect fill="white" width="$width" height="$height"/>
            <text x="20" y="40" font-family="$fontFamily" font-size="16" fill="#000000">$text</text>
        </svg>
    """.trimIndent()

    private fun <T> pdf(
        svg: String
        , read: (PDDocument) -> T
    ): T = Loader.loadPDF(pdfFromSvg(svg)).use(read)

    @Test
    fun pdfHasOnePageOfTheDrawingsSize() {
        pdf(svg(400, 300)) { document ->
            assertEquals(1, document.numberOfPages)
            val box = document.getPage(0).mediaBox
            // A pixel of the SVG is 0.75 points, at 96 pixels an inch.
            assertTrue(abs(box.width - 300f) <= 1f, "${box.width} wide")
            assertTrue(abs(box.height - 225f) <= 1f, "${box.height} high")
        }
    }

    @Test
    fun textIsSearchable() {
        val text = pdf(svg(400, 300)) { PDFTextStripper().getText(it) }

        assertTrue("Hello map" in text, text)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun textOfAMapIsSearchable() = runComposeUiTest {
        val tree = Tree<Any>(Dp(150f), Dp(300f)).apply {
            addNode("a", getRootNode().id, "first child")
            addNode("b", getRootNode().id, "second child")
        }
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()

        val text = pdf(assertNotNull(state.exportSvg())) { PDFTextStripper().getText(it) }

        assertTrue("first child" in text && "second child" in text, text)
    }

    @Test
    fun fontsAreEmbedded() {
        pdf(svg(400, 300)) { document ->
            val resources = document.getPage(0).resources
            val fonts = resources.fontNames.map { resources.getFont(it) }
            assertTrue(fonts.isNotEmpty())
            fonts.forEach { assertTrue(it.isEmbedded, "${it.name} is embedded") }
        }
    }

    @Test
    fun largeMapIsScaledToTheLargestPage() {
        pdf(svg(30_000, 1_000)) { document ->
            val box = document.getPage(0).mediaBox
            assertTrue(max(box.width, box.height) <= 14_400f, "${box.width} by ${box.height}")
            assertTrue(abs(box.width / box.height - 30f) <= 0.3f, "${box.width} by ${box.height}")
        }
    }

    @Test
    fun textWithoutABundledFontStillMakesAPdf() {
        pdf(svg(400, 300, text = "运维工作 😀", fontFamily = "Noto Color Emoji")) { document ->
            assertEquals(1, document.numberOfPages)
        }
    }
}
