package com.mindsync.mindmapview.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import kotlin.math.max
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.configuration.DefaultConfigurationBuilder
import org.apache.fop.svg.PDFTranscoder

// A PDF viewer shows no page larger than this many points on a side.
private const val MAX_PAGE_SIDE = 14_400f

// The size of a pixel of an SVG in points, at the 96 pixels an inch the SVG is drawn at.
private const val POINTS_PER_PIXEL = 0.75f

private val SVG_TAG = Regex("""<svg\s[^>]*>""")
private val SVG_WIDTH = Regex("""\swidth="([\d.]+)"""")
private val SVG_HEIGHT = Regex("""\sheight="([\d.]+)"""")

// The fonts nodes can be drawn in are copied out of the app's resources, as the PDF library reads fonts from files.
// The color emoji font is left out, as its glyphs are pictures a PDF font cannot hold.
private val bundledFontFolder: File by lazy {
    Files.createTempDirectory("mindmapview-fonts").toFile().apply {
        deleteOnExit()
        NodeFont.entries.flatMap { it.files }.forEach { face ->
            val bytes = object {}
                .javaClass
                .getResourceAsStream("/${face.file}")
                ?.use { it.readBytes() }
                ?: return@forEach
            File(this, face.file).apply {
                writeBytes(bytes)
                deleteOnExit()
            }
        }
    }
}

/**
 * Returns the SVG drawing [svg] as a PDF document of one page the size of the drawing, or as large as a PDF viewer
 * shows it, with the text kept as text in fonts embedded in the document. The fonts are the app's own and those of
 * the computer, which a node in the default font is drawn in.
 *
 * @throws IOException when the PDF cannot be made.
 */
internal fun pdfFromSvg(svg: String): ByteArray {
    val transcoder = PDFTranscoder()
    val configuration = """
        <fop>
            <fonts>
                <auto-detect/>
                <directory>${bundledFontFolder.absolutePath}</directory>
            </fonts>
        </fop>
    """.trimIndent()
    transcoder.configure(DefaultConfigurationBuilder().build(configuration.byteInputStream()))
    pageWidth(svg)?.let { transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, it) }
    val output = ByteArrayOutputStream()
    try {
        transcoder.transcode(TranscoderInput(StringReader(svg)), TranscoderOutput(output))
    } catch (e: TranscoderException) {
        throw IOException(e.message, e)
    }
    return output.toByteArray()
}

// The width in pixels that keeps the page within what a viewer shows, or null when the drawing is small enough.
private fun pageWidth(svg: String): Float? {
    val tag = SVG_TAG.find(svg)?.value ?: return null
    val width = SVG_WIDTH.find(tag)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    val height = SVG_HEIGHT.find(tag)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    val largestSide = max(width, height) * POINTS_PER_PIXEL
    if (largestSide <= MAX_PAGE_SIDE) return null
    return width * MAX_PAGE_SIDE / largestSide
}
