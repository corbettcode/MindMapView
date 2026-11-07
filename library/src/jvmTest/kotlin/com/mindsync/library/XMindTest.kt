package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.Inflater
import com.mindsync.library.io.XMind
import com.mindsync.library.io.ZipArchive
import com.mindsync.library.util.DescriptionEmoji
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XMindTest {
    private fun Tree<Any>.childrenOf(node: NodeData<*>): List<NodeData<*>> = node.children.map { getNode(it) }

    private fun Tree<Any>.outline(
        node: NodeData<*> = getRootNode()
        , depth: Int = 0
    ): String = "  ".repeat(depth) + node.description + "\n" +
        childrenOf(node).joinToString("") { outline(it, depth + 1) }

    private fun Tree<Any>.nodeNamed(description: String): NodeData<*> = nodes.values.single {
        it.description == description
    }

    private fun zip(
        vararg files: Pair<String, String>
        , isStored: Boolean = false
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                val data = content.encodeToByteArray()
                val entry = ZipEntry(name)
                if (isStored) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun deflate(
        data: ByteArray
        , level: Int
        , strategy: Int = Deflater.DEFAULT_STRATEGY
    ): ByteArray {
        val deflater = Deflater(level, true)
        deflater.setStrategy(strategy)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    @Test
    fun inflatesWhatTheJdkDeflates() {
        val random = Random(42)
        val text = buildString { repeat(4000) { append("topic ").append(random.nextInt(500)).append('\n') } }
        val inputs = listOf(
            ByteArray(0)
            , "a".encodeToByteArray()
            , text.encodeToByteArray()
            , random.nextBytes(70_000)
        )
        val settings = listOf(
            Deflater.NO_COMPRESSION to Deflater.DEFAULT_STRATEGY
            , Deflater.BEST_SPEED to Deflater.DEFAULT_STRATEGY
            , Deflater.BEST_COMPRESSION to Deflater.DEFAULT_STRATEGY
            , Deflater.DEFAULT_COMPRESSION to Deflater.HUFFMAN_ONLY
            , Deflater.DEFAULT_COMPRESSION to Deflater.FILTERED
        )
        inputs.forEach { input ->
            settings.forEach { (level, strategy) ->
                val deflated = deflate(input, level, strategy)
                assertContentEquals(input, Inflater(deflated, 0, deflated.size, input.size).inflate())
            }
        }
    }

    @Test
    fun readsStoredAndDeflatedFilesOfAnArchive() {
        val files = arrayOf("a.txt" to "first", "dir/b.txt" to "second ".repeat(100))
        listOf(true, false).forEach { isStored ->
            val archive = ZipArchive(zip(*files, isStored = isStored))

            assertEquals(setOf("a.txt", "dir/b.txt"), archive.names)
            files.forEach { (name, content) -> assertEquals(content, archive.read(name)?.decodeToString()) }
            assertEquals(null, archive.read("missing"))
        }
    }

    @Test
    fun readsTheTopicsOfAMapOfXMind2020() {
        val content = """
            [{
              "id": "sheet1", "class": "sheet", "title": "Sheet 1",
              "rootTopic": {
                "id": "root", "title": "Trip",
                "children": {
                  "detached": [{ "id": "float", "title": "Floating" }],
                  "attached": [
                    { "id": "a", "title": "Pack", "children": { "attached": [{ "id": "a1", "title": "Tent" }] } },
                    { "id": "b", "title": "Drive" }
                  ]
                }
              }
            }]
        """.trimIndent()

        val tree = XMind.read(zip("content.json" to content, "content.xml" to "<xmap-content/>"), title = "trip")

        assertEquals("Trip\n  Pack\n    Tent\n  Drive\n  Floating\n", tree.outline())
        assertTrue(tree.getRootNode() is OvalNodeData)
    }

    @Test
    fun keepsNotesLabelsMarkersLinksStylesAndFolding() {
        val content = """
            [{
              "title": "Sheet 1",
              "rootTopic": {
                "id": "root", "title": "Root",
                "notes": { "plain": { "content": "Root note\nsecond line" } },
                "children": { "attached": [
                  {
                    "id": "a", "title": "Styled",
                    "notes": {
                      "plain": { "content": "plain" },
                      "realHTML": { "content": "<div><b>rich</b><img src=\"xap:resources/a.png\"></div>" }
                    },
                    "labels": ["one", "two"],
                    "markers": [{ "markerId": "priority-1" }, { "markerId": "task-done" }, { "markerId": "unknown" }],
                    "style": { "properties": {
                      "svg:fill": "#112233", "fo:color": "#445566FF", "fo:font-weight": "bold",
                      "shape-class": "org.xmind.topicShape.ellipse"
                    } },
                    "branch": "folded",
                    "children": { "attached": [{ "id": "a1", "title": "Hidden" }] }
                  },
                  { "id": "b", "title": "Site", "href": "https://example.com/?a=1&b=2" },
                  { "id": "c", "title": "Topic link", "href": "xmind:#a" }
                ] }
              },
              "relationships": [{ "end1Id": "b", "end2Id": "a" }, { "end1Id": "a", "end2Id": "gone" }]
            }]
        """.trimIndent()

        val tree = XMind.read(zip("content.json" to content), title = "map")
        val root = tree.getRootNode()
        val styled = tree.childrenOf(root)[0]
        val site = tree.childrenOf(root)[1]

        assertEquals("Root note<br>second line", root.note)
        assertEquals("<p>1️⃣ ✅ <b>Styled</b></p>", styled.description)
        assertEquals("1️⃣", DescriptionEmoji.split(styled.description).first)
        assertEquals("<div><b>rich</b></div>", styled.note)
        assertEquals(listOf(NodeAttribute("Labels", "one, two")), tree.attributesOf(styled.id))
        assertEquals(Color(0xFF112233), styled.color)
        assertEquals(Color(0xFF445566), styled.textColor)
        assertTrue(styled is OvalNodeData)
        assertTrue(tree.isFolded(styled.id))
        assertEquals("<a href=\"https://example.com/?a=1&amp;b=2\">Site</a>", site.description)
        assertEquals("Topic link", tree.childrenOf(root)[2].description)
        assertEquals(listOf(site.id to styled.id), tree.arrowLinks.map { it.sourceId to it.targetId })
    }

    @Test
    fun mapWithSeveralSheetsIsNamedAfterItsTitle() {
        val content = """
            [
              { "title": "First", "rootTopic": { "id": "r1", "title": "One" } },
              { "title": "Second", "rootTopic": { "id": "r2", "title": "" } }
            ]
        """.trimIndent()

        assertEquals("plans\n  One\n  Second\n", XMind.read(zip("content.json" to content), title = "plans").outline())
    }

    @Test
    fun readsAMapOfXMind8WithItsStyles() {
        val content = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <xmap-content xmlns="urn:xmind:xmap:xmlns:content:2.0" xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:xhtml="http://www.w3.org/1999/xhtml" version="2.0">
              <sheet id="s1">
                <topic id="root" structure-class="org.xmind.ui.map.unbalanced">
                  <title>Rocks &amp; minerals</title>
                  <children>
                    <topics type="detached"><topic id="f"><title>Loose</title></topic></topics>
                    <topics type="attached">
                      <topic id="a" style-id="st1" branch="folded" xlink:href="http://rocks.example">
                        <title><![CDATA[Igneous <hot>]]></title>
                        <notes><plain>Cooled magma</plain><html><xhtml:p>Cooled magma</xhtml:p></html></notes>
                        <labels><label>volcanic</label></labels>
                        <marker-refs><marker-ref marker-id="flag-red"/></marker-refs>
                        <children><topics type="attached"><topic id="a1"><title>Granite</title></topic></topics></children>
                      </topic>
                    </topics>
                  </children>
                </topic>
                <relationships><relationship id="r" end1="f" end2="a"/></relationships>
                <title>Sheet 1</title>
              </sheet>
            </xmap-content>
        """.trimIndent()
        val styles = """
            <xmap-styles xmlns:svg="http://www.w3.org/2000/svg" xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <styles><style id="st1" type="topic"><topic-properties svg:fill="#FF0000" fo:font-style="italic"/></style></styles>
            </xmap-styles>
        """.trimIndent()

        val tree = XMind.read(zip("content.xml" to content, "styles.xml" to styles), title = "rocks")
        val igneous = tree.childrenOf(tree.getRootNode()).first()

        assertEquals(
            "Rocks &amp; minerals\n  <p>🚩 <a href=\"http://rocks.example\"><i>Igneous &lt;hot&gt;</i></a></p>\n    Granite\n  Loose\n"
            , tree.outline()
        )
        assertEquals("Cooled magma", igneous.note)
        assertEquals(listOf(NodeAttribute("Labels", "volcanic")), tree.attributesOf(igneous.id))
        assertEquals(Color(0xFFFF0000), igneous.color)
        assertTrue(tree.isFolded(igneous.id))
        assertEquals(listOf(tree.nodeNamed("Loose").id to igneous.id), tree.arrowLinks.map { it.sourceId to it.targetId })
    }

    private fun Tree<Any>.boundaryTexts(): Set<List<String>> =
        boundaries.map { boundary -> boundaryChildIds(boundary).map { getNode(it).description } }.toSet()

    @Test
    fun boundariesOfXMindAreKept() {
        val content = """
            [{ "title": "s", "rootTopic": {
              "id": "r", "title": "Root",
              "boundaries": [{ "id": "b1", "range": "(0,1)", "title": "Dropped" }],
              "children": {
                "detached": [{ "id": "f", "title": "Floating" }],
                "attached": [
                  { "id": "a", "title": "A",
                    "boundaries": [{ "id": "b2", "range": "master" }, { "id": "b3", "range": "(1,5)" }],
                    "children": { "attached": [
                      { "id": "a1", "title": "A1" }, { "id": "a2", "title": "A2" }, { "id": "a3", "title": "A3" }
                    ] } },
                  { "id": "b", "title": "B" },
                  { "id": "c", "title": "C" }
                ]
              }
            } }]
        """.trimIndent()

        val tree = XMind.read(zip("content.json" to content), title = "map")

        // A range past the attached children ends at the last of them, and floating topics are in none.
        assertEquals(setOf(listOf("A", "B"), listOf("A"), listOf("A2", "A3")), tree.boundaryTexts())
        assertTrue(tree.boundaries.all { it.color == null })
    }

    @Test
    fun boundariesOfXMind8AreKept() {
        val content = """
            <xmap-content xmlns="urn:xmind:xmap:xmlns:content:2.0" version="2.0">
              <sheet id="s1">
                <topic id="root">
                  <title>Root</title>
                  <children>
                    <topics type="attached">
                      <topic id="a"><title>A</title></topic>
                      <topic id="b"><title>B</title></topic>
                      <topic id="c"><title>C</title></topic>
                    </topics>
                  </children>
                  <boundaries>
                    <boundary id="x" range="(1,2)"/>
                    <boundary id="y" range="master"/>
                    <boundary id="z" range="nonsense"/>
                  </boundaries>
                </topic>
              </sheet>
            </xmap-content>
        """.trimIndent()

        val tree = XMind.read(zip("content.xml" to content), title = "map")

        // The root is in no boundary, as it has no parent.
        assertEquals(setOf(listOf("B", "C")), tree.boundaryTexts())
    }

    @Test
    fun refusesWhatIsNotAnXMindMap() {
        assertFailsWith<IllegalArgumentException> { XMind.read("# Markdown".encodeToByteArray(), title = "x") }
        assertFailsWith<IllegalArgumentException> { XMind.read(zip("other.txt" to "x"), title = "x") }
        val encrypted = zip("content.json" to "garbled", "manifest.json" to """{"encryption-data": {}}""")
        val error = assertFailsWith<IllegalArgumentException> { XMind.read(encrypted, title = "x") }
        assertEquals("The map is password-protected", error.message)
    }
}
