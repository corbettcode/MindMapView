package com.mindsync.library.io

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.util.IdGenerator

/**
 * Reads a Markdown document as a mind map.
 *
 * Headings and list items become nodes: a heading holds the headings of lower levels after it, and the list
 * items up to the next heading; a list item holds the items indented under it. Other text becomes the note of
 * the heading or list item it follows. A document with a single top heading becomes a map with that heading as
 * its root; any other document gets a root named after its title, with the text before the first heading as
 * its note. Bold, italic, struck-through text and links are kept; images are kept as their alternative text.
 * A paragraph that starts with bold text becomes a node of its own, named after that text, with the rest of the
 * paragraph as its note.
 *
 * A table becomes a branch of the heading or list item it follows, named after the header of its first column.
 * Each row is a node under that branch, named after its first cell, with the other cells as its attributes,
 * named after their column headers. A table whose first header is empty puts its rows straight under the heading.
 * Node positions are not stored: a map that was read needs to be arranged, for example with
 * `MindMapState.animateTreeChange()`.
 */
object Markdown {
    const val EXTENSION = "md"

    private const val TAB_WIDTH = 4

    // The depth of the lowest nodes that are written as headings: the root is at depth 0.
    private const val HEADING_DEPTH = 2

    private val HEADING = Regex("""^ {0,3}(#{1,6})(?:\s+(.*?))?(?:\s+#+)?\s*$""")
    private val SETEXT_UNDERLINE = Regex("""^ {0,3}(=+|-+)\s*$""")
    private val LIST_ITEM = Regex("""^(\s*)(?:[-*+]|\d{1,9}[.)])\s+(.*)$""")
    private val TASK = Regex("""^\[([ xX])]\s+""")
    private val THEMATIC_BREAK = Regex("""^ {0,3}([-*_])(?:\s*\1){2,}\s*$""")
    private val FENCE = Regex("""^\s*(```|~~~)""")
    private val BLOCK_QUOTE = Regex("""^\s*(?:>\s?)+""")
    private val TABLE_DIVIDER = Regex("""^\s*\|?\s*:?-+:?\s*(?:\|\s*:?-+:?\s*)*\|?\s*$""")
    private val LINK = Regex("""(!?)\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)|<(https?://[^>\s]+)>""")
    private val BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC = Regex("""\*([^*\s][^*]*?)\*|(?<![\p{L}\p{N}])_([^_\s][^_]*?)_(?![\p{L}\p{N}])""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val HARD_BREAK = Regex("""( {2,}|\\)$""")
    private val CELL_SEPARATOR = Regex("""(?<!\\)\|""")
    private val TAG = Regex("""<[^>]+>""")
    private val BOLD_LEAD = Regex("""^<b>(.+?)</b>(?:\s|<br>)*(.*)$""", RegexOption.DOT_MATCHES_ALL)

    private class Section(
        val text: String
        , val level: Int
    ) {
        val children = mutableListOf<Section>()
        val paragraphs = mutableListOf<String>()
        val attributes = mutableListOf<NodeAttribute>()
    }

    private class ListLevel(
        val indent: Int
        , val section: Section
    )

    /**
     * Reads [content] as a mind map whose root is named [title] when the document has no single top heading to
     * name it.
     */
    fun read(
        content: String
        , title: String
    ): Tree<Any> {
        val document = Section(inline(title), level = 0)
        val headings = mutableListOf(document)
        val lists = mutableListOf<ListLevel>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var isInFence = false
        var isAfterBlankLine = false

        fun target(): Section = lists.lastOrNull()?.section ?: headings.last()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString("")
            paragraph.clear()
            val lead = BOLD_LEAD.matchEntire(text)?.takeIf { it.groupValues[1].isNotBlank() }
            if (lead == null) {
                target().paragraphs.add(text)
                return
            }
            val section = Section(lead.groupValues[1].trim(), level = Int.MAX_VALUE)
            lead.groupValues[2].takeIf { it.isNotBlank() }?.let { section.paragraphs.add(it) }
            target().children.add(section)
        }

        fun addHeading(
            level: Int
            , text: String
        ) {
            flushParagraph()
            lists.clear()
            while (headings.last().level >= level) headings.removeAt(headings.lastIndex)
            val section = Section(inline(text), level)
            headings.last().children.add(section)
            headings.add(section)
        }

        fun addTable(
            header: List<String>
            , rows: List<List<String>>
        ) {
            flushParagraph()
            val branch = header.first().takeIf { it.isNotBlank() }?.let { Section(inline(it), level = Int.MAX_VALUE) }
            branch?.let { target().children.add(it) }
            rows.forEach { row ->
                val item = Section(inline(row.first()), level = Int.MAX_VALUE)
                header.zip(row).drop(1)
                    .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }
                    .forEach { (name, value) -> item.attributes.add(NodeAttribute(plainText(name), plainText(value))) }
                (branch ?: target()).children.add(item)
            }
        }

        val lines = withoutFrontMatter(content.replace("\r\n", "\n").replace('\r', '\n').lines())
        var index = 0
        while (index < lines.size) {
            val line = lines[index++]
            if (FENCE.containsMatchIn(line)) {
                flushParagraph()
                if (isInFence) target().paragraphs.add(code.joinToString("<br>"))
                code.clear()
                isInFence = !isInFence
                continue
            }
            if (isInFence) {
                code.add(escape(line.trimEnd()))
                continue
            }
            if (line.isBlank()) {
                flushParagraph()
                isAfterBlankLine = true
                continue
            }
            val text = line.replace(BLOCK_QUOTE, "")
            if (isTableHeader(text, lines.getOrNull(index))) {
                val header = cells(text)
                val rows = mutableListOf<List<String>>()
                index++
                while (index < lines.size && lines[index].isNotBlank() && '|' in lines[index]) {
                    val row = cells(lines[index++].replace(BLOCK_QUOTE, ""))
                    if (row.any { it.isNotBlank() }) rows.add(List(header.size) { row.getOrElse(it) { "" } })
                }
                addTable(header, rows)
                isAfterBlankLine = false
                continue
            }
            val heading = HEADING.matchEntire(text)
            val listItem = LIST_ITEM.matchEntire(text)
            val underline = SETEXT_UNDERLINE.matchEntire(text)
            when {
                heading != null -> addHeading(heading.groupValues[1].length, heading.groupValues[2])
                underline != null && paragraph.size == 1 && !isAfterBlankLine -> {
                    val level = if (underline.groupValues[1].startsWith("=")) 1 else 2
                    val headingText = paragraph.single().removeSuffix("<br>").trim()
                    paragraph.clear()
                    addHeading(level, headingText)
                }
                THEMATIC_BREAK.matches(text) -> flushParagraph()
                listItem != null -> {
                    flushParagraph()
                    val indent = indentOf(listItem.groupValues[1])
                    while (lists.isNotEmpty() && lists.last().indent >= indent) lists.removeAt(lists.lastIndex)
                    val section = Section(inline(task(listItem.groupValues[2])), level = Int.MAX_VALUE)
                    target().children.add(section)
                    lists.add(ListLevel(indent, section))
                }
                TABLE_DIVIDER.matches(text) && '|' in text -> Unit
                else -> {
                    // Text that is not indented under a list item after a blank line belongs to the heading.
                    if (isAfterBlankLine && lists.isNotEmpty() && indentOf(text) <= lists.first().indent) {
                        lists.clear()
                    }
                    val isHardBreak = HARD_BREAK.containsMatchIn(text)
                    val words = inline(text.replace(HARD_BREAK, "").trim())
                    if (paragraph.isNotEmpty() && !paragraph.last().endsWith("<br>")) paragraph.add(" ")
                    paragraph.add(if (isHardBreak) "$words<br>" else words)
                }
            }
            isAfterBlankLine = false
        }
        flushParagraph()
        if (code.isNotEmpty()) target().paragraphs.add(code.joinToString("<br>"))

        val isTitledByHeading = document.children.size == 1 && document.paragraphs.isEmpty()
        val root = if (isTitledByHeading) document.children.single() else document
        return toTree(root)
    }

    /**
     * Writes [tree] as a Markdown document that [read] reads back as the same tree. The root is the one top
     * heading, the nodes of the two levels below it are headings too, and the nodes below those are nested list
     * items. A note is written as paragraphs after its node. Bold, italic, struck-through text and links are kept;
     * shapes, colors, borders, lines, arrow links, folds and the sides of the root are not.
     *
     * Markdown can only give attributes to the rows of a table, which have no children and no notes. So the
     * children of a node that have attributes and no children of their own are written as a table right after it,
     * and come back first among its children, without their notes. The attributes of a node with children are
     * left out. A line break in the text of a node is written as a space, as a heading or list item is one line.
     */
    fun write(tree: Tree<*>): String = buildString {
        val root = tree.getRootNode()
        append("# ${MarkdownText.inline(root.description)}\n")
        writeNote(root.note, indent = null)
        writeChildren(tree, root, depth = 0)
    }

    private fun StringBuilder.writeChildren(
        tree: Tree<*>
        , node: NodeData<*>
        , depth: Int
    ) {
        val children = node.children.map { tree.getNode(it) }
        val (rows, others) = children.partition { isTableRow(tree, it) }
        // The importer gives a table's rows to the heading or list item read last, so the table has to come first.
        writeTable(tree, rows)
        others.forEach { writeNode(tree, it, depth + 1) }
    }

    private fun StringBuilder.writeNode(
        tree: Tree<*>
        , node: NodeData<*>
        , depth: Int
    ) {
        val text = MarkdownText.inline(node.description)
        if (depth <= HEADING_DEPTH) {
            append("\n${"#".repeat(depth + 1)} $text\n")
            writeNote(node.note, indent = null)
        } else {
            val indent = " ".repeat(TAB_WIDTH * (depth - HEADING_DEPTH - 1))
            append("$indent- $text\n")
            writeNote(node.note, indent = "$indent${" ".repeat(TAB_WIDTH)}")
        }
        writeChildren(tree, node, depth)
    }

    // A note under a list item is indented to belong to it; under a heading, it belongs to the heading anyway.
    private fun StringBuilder.writeNote(
        note: String
        , indent: String?
    ) {
        MarkdownText.paragraphs(note).forEach { paragraph ->
            append("\n${indent.orEmpty()}$paragraph\n")
        }
        if (indent != null && note.isNotEmpty()) append("\n")
    }

    private fun isTableRow(
        tree: Tree<*>
        , node: NodeData<*>
    ): Boolean = node.children.isEmpty() && tree.attributesOf(node.id).any { it.value.isNotBlank() }

    // A table whose first header is empty puts its rows straight under the node before it, each named after its
    // first cell. An attribute name used more than once in a row has a column for each time.
    private fun StringBuilder.writeTable(
        tree: Tree<*>
        , rows: List<NodeData<*>>
    ) {
        if (rows.isEmpty()) return
        val rowAttributes = rows.map { row -> tree.attributesOf(row.id).filter { it.value.isNotBlank() } }
        val columns = mutableListOf<Pair<String, Int>>()
        rowAttributes.forEach { attributes ->
            val counts = mutableMapOf<String, Int>()
            attributes.forEach { attribute ->
                val occurrence = counts.getOrElse(attribute.name) { 0 }
                counts[attribute.name] = occurrence + 1
                if (attribute.name to occurrence !in columns) columns.add(attribute.name to occurrence)
            }
        }
        append("\n|  | ${columns.joinToString(" | ") { MarkdownText.cell(it.first) }} |\n")
        append("| --- |${" --- |".repeat(columns.size)}\n")
        rows.zip(rowAttributes).forEach { (row, attributes) ->
            val values = columns.map { (name, occurrence) ->
                attributes.filter { it.name == name }.getOrNull(occurrence)?.value.orEmpty()
            }
            val name = MarkdownText.inline(row.description).replace("|", "\\|")
            append("| $name | ${values.joinToString(" | ") { MarkdownText.cell(it) }} |\n")
        }
        append("\n")
    }

    private fun toTree(rootSection: Section): Tree<Any> {
        val tree = Tree<Any>()
        val root = tree.getRootNode()
        tree.setRootNode(
            root.copy(
                children = rootSection.children.map { addNode(tree, it, root.id) }
                , description = rootSection.text
                , note = note(rootSection)
            )
        )
        return tree
    }

    private fun addNode(
        tree: Tree<Any>
        , section: Section
        , parentId: String
    ): String {
        val id = IdGenerator.makeRandomNodeId()
        val children = section.children.map { addNode(tree, it, id) }
        val shape = NodeShape.RECTANGLE
        val node = unplacedNode(
            shape
            , id
            , parentId
            , section.text
            , children
            , shape.defaultColor
            , shape.defaultTextColor
            , note(section)
        )
        tree.setNode(id, node)
        tree.setAttributes(id, section.attributes)
        return id
    }

    private fun note(section: Section): String = when (section.paragraphs.size) {
        0 -> ""
        1 -> section.paragraphs.single()
        else -> section.paragraphs.joinToString("") { "<p>$it</p>" }
    }

    // YAML front matter holds settings for the programs that publish the document, not its text.
    private fun withoutFrontMatter(lines: List<String>): List<String> {
        if (lines.firstOrNull()?.trim() != "---") return lines
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
        return if (end < 0) lines else lines.drop(end + 2)
    }

    private fun isTableHeader(
        line: String
        , nextLine: String?
    ): Boolean {
        val divider = nextLine?.replace(BLOCK_QUOTE, "") ?: return false
        return '|' in line && '|' in divider && TABLE_DIVIDER.matches(divider)
    }

    private fun cells(line: String): List<String> = line
        .trim()
        .removePrefix("|")
        .let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }
        .split(CELL_SEPARATOR)
        .map { it.trim().replace("\\|", "|") }

    // Attributes are plain text, so formatting is dropped from them.
    private fun plainText(markdown: String): String = decodeEntities(inline(markdown).replace(TAG, ""))

    private fun indentOf(text: String): Int = text
        .takeWhile { it == ' ' || it == '\t' }
        .sumOf { if (it == '\t') TAB_WIDTH else 1 }

    private fun task(text: String): String = TASK.find(text)?.let { match ->
        (if (match.groupValues[1].isBlank()) "☐ " else "☑ ") + text.substring(match.range.last + 1)
    } ?: text

    // Code spans are kept as they are written, so the marks in them are not read as formatting.
    private fun inline(text: String): String = text
        .split('`')
        .mapIndexed { index, part -> if (index % 2 == 1) escape(part) else links(part) }
        .joinToString("")

    private fun links(text: String): String = buildString {
        var start = 0
        LINK.findAll(text).forEach { match ->
            append(emphasis(text.substring(start, match.range.first)))
            val (image, label, url, autoLink) = match.destructured
            when {
                autoLink.isNotEmpty() -> append(link(autoLink, escape(autoLink)))
                image.isNotEmpty() -> append(emphasis(label))
                else -> append(link(url, emphasis(label)))
            }
            start = match.range.last + 1
        }
        append(emphasis(text.substring(start)))
    }

    private fun link(
        url: String
        , label: String
    ): String = "<a href=\"${escape(url).replace("\"", "&quot;")}\">$label</a>"

    private fun emphasis(text: String): String = escape(text)
        .replace(BOLD) { "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>" }
        .replace(ITALIC) { "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>" }
        .replace(STRIKE) { "<s>${it.groupValues[1]}</s>" }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
