package com.mindsync.library

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.Markdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {
    private fun Tree<Any>.childrenOf(node: NodeData<*>): List<NodeData<*>> = node.children.map { getNode(it) }

    private fun Tree<Any>.outline(
        node: NodeData<*> = getRootNode()
        , depth: Int = 0
    ): String = "  ".repeat(depth) + node.description + "\n" +
        childrenOf(node).joinToString("") { outline(it, depth + 1) }

    @Test
    fun singleTopHeadingIsTheRootAndLowerHeadingsAreItsBranches() {
        val tree = Markdown.read(
            """
            # Rocks
            ## Igneous
            ### Granite
            ## Sedimentary
            """.trimIndent()
            , title = "rocks"
        )

        assertEquals("Rocks\n  Igneous\n    Granite\n  Sedimentary\n", tree.outline())
    }

    @Test
    fun documentWithSeveralTopHeadingsIsNamedAfterItsTitle() {
        val tree = Markdown.read("Intro text\n\n# One\n# Two", title = "notes")

        assertEquals("notes\n  One\n  Two\n", tree.outline())
        assertEquals("Intro text", tree.getRootNode().note)
    }

    @Test
    fun listItemsAreNodesNestedByTheirIndent() {
        val tree = Markdown.read(
            """
            # Plan
            - Design
              - Sketch
              - Review
            * Build
            1. Ship
            """.trimIndent()
            , title = "plan"
        )

        assertEquals("Plan\n  Design\n    Sketch\n    Review\n  Build\n  Ship\n", tree.outline())
    }

    @Test
    fun textBecomesTheNoteOfTheNodeItFollows() {
        val tree = Markdown.read(
            """
            # Root
            ## Topic
            First line
            goes on.

            Second paragraph.
            - Item
              about the item

            Back on the topic.
            """.trimIndent()
            , title = "doc"
        )
        val topic = tree.childrenOf(tree.getRootNode()).single()
        val item = tree.childrenOf(topic).single()

        assertEquals("<p>First line goes on.</p><p>Second paragraph.</p><p>Back on the topic.</p>", topic.note)
        assertEquals("about the item", item.note)
    }

    @Test
    fun inlineFormattingIsKeptAsRichText() {
        val tree = Markdown.read(
            "# **Bold** *italic* ~~gone~~ `a*b*c` [site](https://example.com) ![logo](logo.png) 1 < 2"
            , title = "doc"
        )

        assertEquals(
            "<b>Bold</b> <i>italic</i> <s>gone</s> a*b*c <a href=\"https://example.com\">site</a> logo 1 &lt; 2"
            , tree.getRootNode().description
        )
    }

    @Test
    fun codeBlocksFrontMatterTasksAndSetextHeadingsAreRead() {
        val tree = Markdown.read(
            """
            ---
            title: ignored
            ---
            Root
            ====
            Section
            -------
            - [ ] todo
            - [x] done
            ```
            a < b
              c
            ```
            """.trimIndent()
            , title = "doc"
        )
        val section = tree.childrenOf(tree.getRootNode()).single()

        assertEquals("Root\n  Section\n    ☐ todo\n    ☑ done\n", tree.outline())
        assertEquals("a &lt; b<br>  c", tree.childrenOf(section).last().note)
    }

    @Test
    fun nodesAreRectangles() {
        val tree = Markdown.read("# A\n- b", title = "doc")

        assertTrue(tree.childrenOf(tree.getRootNode()).all { it is RectangleNodeData })
    }

    @Test
    fun tableIsABranchOfItsFirstColumnWithTheOtherColumnsAsAttributes() {
        val tree = Markdown.read(
            """
            # Project
            | Task | Owner | Status |
            |------|:-----:|--------|
            | Design | **Ann** | Done |
            | Build | Bob | |

            After the table.
            """.trimIndent()
            , title = "doc"
        )
        val task = tree.childrenOf(tree.getRootNode()).single()
        val (design, build) = tree.childrenOf(task)

        assertEquals("Project\n  Task\n    Design\n    Build\n", tree.outline())
        assertEquals(
            listOf(NodeAttribute("Owner", "Ann"), NodeAttribute("Status", "Done"))
            , tree.attributesOf(design.id)
        )
        assertEquals(listOf(NodeAttribute("Owner", "Bob")), tree.attributesOf(build.id))
        assertEquals("After the table.", tree.getRootNode().note)
    }

    @Test
    fun tableWithoutAFirstHeaderPutsItsRowsUnderTheHeading() {
        val tree = Markdown.read(
            """
            # Sizes
            |   | Width |
            |---|---|
            | Small | 1 \| 2 |
            """.trimIndent()
            , title = "doc"
        )
        val small = tree.childrenOf(tree.getRootNode()).single()

        assertEquals("Small", small.description)
        assertEquals(listOf(NodeAttribute("Width", "1 | 2")), tree.attributesOf(small.id))
    }

    @Test
    fun paragraphLedByBoldTextIsANodeWithTheRestAsItsNote() {
        val tree = Markdown.read(
            """
            # Design
            **Event sourcing.** Every record is its own event stream.
            State is rebuilt by folding the events.

            Plain text stays on the heading.
            """.trimIndent()
            , title = "doc"
        )
        val design = tree.getRootNode()
        val eventSourcing = tree.childrenOf(design).single()

        assertEquals("Event sourcing.", eventSourcing.description)
        assertEquals(
            "Every record is its own event stream. State is rebuilt by folding the events."
            , eventSourcing.note
        )
        assertEquals("Plain text stays on the heading.", design.note)
    }
}
