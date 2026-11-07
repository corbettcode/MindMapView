package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.BoundaryStyle
import com.mindsync.library.data.ArrowLinkLineStyle
import com.mindsync.library.data.ArrowLinkThickness
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.IdGenerator
import com.mindsync.library.util.RichText
import com.mindsync.library.util.RichTextSize
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads and writes mind maps in the FreeMind `.mm` format.
 *
 * FreeMind has no notion of this library's node shapes, so a node's shape is kept in an
 * `<attribute NAME="shape">` child, which FreeMind preserves. It is written in front of the node's own
 * attributes, which are FreeMind's `<attribute>` children as well: the first one named `shape` that holds a
 * shape is read as the shape, and every other one as an attribute. The side of the root a child of the root is
 * on is kept in FreeMind's own `POSITION`, which only has a left and a right: a child of the root above or
 * below it is kept on the side it is to the left or right of. Arrow links are kept as FreeMind's own `<arrowlink>` children of
 * the node they start at. Node positions are not stored: a map that
 * was read needs to be arranged, for example with `MindMapState.animateTreeChange()`.
 *
 * A boundary is kept as FreeMind's own `<cloud>`, which is around one node and the nodes below it. FreeMind has
 * nothing around several nodes, so a boundary around several is written as a cloud around each of them, and each
 * cloud is read as a boundary of its own. A cloud's color is the color it is filled with, which is pale; a pale
 * one is read as a boundary in the color of its branch, and a darker one as a boundary in that color.
 *
 * A summary is kept as Freeplane keeps one, which FreeMind shows as an ordinary node: a `<hook NAME="SummaryNode"/>`
 * on a sibling written right after those it is about. Freeplane takes a summary to be about the siblings on its
 * side back to the summary before it, or to the first of them; where the group begins later, an empty node with a
 * `<hook NAME="FirstGroupNode"/>` is written before it, which Freeplane hides. Freeplane's groups do not overlap,
 * so of summaries that would, the first is kept and the others are written as ordinary nodes. When a map is read,
 * the empty nodes are left out, a summary whose text is in a node of its own below it is read as that node, and a
 * summary of summaries, which this library has not, is read as an ordinary node.
 */
object FreeMind {
    private const val SHAPE_ATTRIBUTE = "shape"
    private const val ARROW = "Default"
    private const val NO_ARROW = "None"
    private const val DASHES = "7 7"
    private const val DOTS = "2 7"
    private const val CLOSE_DOTS = "3 3"
    private const val DASHES_AND_DOTS = "7 7 2 7"
    private const val DEFAULT_FONT_POINTS = 12f
    // FreeMind fills a cloud with its color, and its default one is a pale gray, which would hardly show as the line
    // of a boundary.
    private const val PALE_CLOUD_LUMINANCE = 0.8f
    private const val SUMMARY_HOOK = "SummaryNode"
    private const val FIRST_GROUP_HOOK = "FirstGroupNode"

    fun write(tree: Tree<*>): String = buildString {
        append("<map version=\"1.0.1\">\n")
        append("<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->\n")
        // A node that was removed is not written, so neither is an arrow link to one.
        val writtenIds = mutableSetOf<String>()
        tree.doPreorderTraversal { node -> writtenIds.add(node.id) }
        val arrowLinks = tree.arrowLinks.filter { it.targetId in writtenIds }.groupBy { it.sourceId }
        // A node has one cloud, so of the boundaries around a node, the first is its cloud.
        val clouds = mutableMapOf<String, Boundary>()
        tree.boundaries.forEach { boundary ->
            tree.boundaryChildIds(boundary).forEach { clouds.getOrPut(it) { boundary } }
        }
        writeNode(tree, tree.getRootNode(), depth = 0, arrowLinks, clouds, isSummary = false)
        append("</map>\n")
    }

    /**
     * @throws IllegalArgumentException when [content] is not a FreeMind map.
     */
    fun read(content: String): Tree<Any> {
        val map = XmlParser(content).parse().firstOrNull { it.name == "map" }
            ?: throw IllegalArgumentException("Not a FreeMind map: no <map> element")
        val rootElement = map.children.firstOrNull { it.name == "node" }
            ?: throw IllegalArgumentException("Not a FreeMind map: no root <node> element")

        val tree = Tree<Any>()
        val root = tree.getRootNode()
        val usedIds = mutableSetOf(root.id)
        val leftChildren = mutableListOf<String>()
        // The node an arrow link leads to may be read after the link, so the links are added once all nodes are.
        val arrowLinks = mutableListOf<ArrowLink>()
        rootElement.readArrowLinks(root.id, arrowLinks)
        // A node is only in the tree once the nodes above it are, so the clouds are added once all nodes are too.
        val clouds = mutableListOf<Boundary>()
        // A summary is about siblings that are only all in the tree once the whole tree is.
        val summaries = mutableListOf<Pair<String, Set<String>>>()
        val children = readChildren(tree, rootElement, root.id, usedIds, arrowLinks, clouds, summaries, isRoot = true)
            .map { (element, id) ->
                if (element.attributes["POSITION"] == "left") leftChildren.add(id)
                id
            }
        tree.setRootNode(
            root.copy(
                children = children,
                color = rootElement.color("BACKGROUND_COLOR") ?: root.color,
                description = rootElement.description(),
                note = rootElement.note(),
                textColor = rootElement.color("COLOR") ?: root.textColor,
            ),
        )
        tree.setAttributes(root.id, rootElement.nodeAttributes())
        tree.setLine(root.id, rootElement.line())
        leftChildren.forEach { tree.setRootChildSide(it, LayoutSide.LEFT) }
        val usedArrowLinkIds = mutableSetOf<String>()
        arrowLinks
            // The root is given this library's own id, whatever id it has in the file.
            .map { if (it.targetId == rootElement.attributes["ID"]) it.copy(targetId = root.id) else it }
            .filter { it.targetId in tree.nodes && it.targetId != it.sourceId }
            .forEach { link ->
                tree.addArrowLink(if (usedArrowLinkIds.add(link.id)) link else link.copy(id = IdGenerator.makeRandomNodeId()))
            }
        clouds.forEach { tree.addBoundary(it) }
        summaries.forEach { (summaryId, childIds) ->
            try {
                tree.setSummary(summaryId, childIds)
            } catch (e: IllegalArgumentException) {
                return@forEach
            }
        }
        return tree
    }

    private fun StringBuilder.writeNode(
        tree: Tree<*>,
        node: NodeData<*>,
        depth: Int,
        arrowLinks: Map<String, List<ArrowLink>>,
        clouds: Map<String, Boundary>,
        isSummary: Boolean,
    ) {
        val indent = "  ".repeat(depth)
        val plainText = plainTextOrNull(node.description)

        append(indent).append("<node ID=\"").append(escapeXml(node.id)).append('"')
        if (plainText != null) append(" TEXT=\"").append(escapeXml(plainText)).append('"')
        if (depth == 1) append(" POSITION=\"").append(tree.sideOf(node.id).horizontal.name.lowercase()).append('"')
        if (tree.isFolded(node.id)) append(" FOLDED=\"true\"")
        // FreeMind has no themes, so a node is written in the colors it is drawn in.
        append(" BACKGROUND_COLOR=\"").append(tree.fillOf(node.id).toHex()).append('"')
        append(" COLOR=\"").append(tree.textColorOf(node.id).toHex()).append("\">\n")
        if (plainText == null) {
            append(indent).append("  <richcontent TYPE=\"NODE\"><html><head></head><body>")
            append(toXhtml(node.description))
            append("</body></html></richcontent>\n")
        }
        if (node.note.isNotBlank()) {
            append(indent).append("  <richcontent TYPE=\"NOTE\"><html><head></head><body>")
            append(toXhtml(node.note))
            append("</body></html></richcontent>\n")
        }
        append(indent).append("  <attribute NAME=\"").append(SHAPE_ATTRIBUTE).append("\" VALUE=\"")
        append(node.shape().name.lowercase()).append("\"/>\n")
        tree.attributesOf(node.id).forEach { attribute ->
            append(indent).append("  <attribute NAME=\"").append(escapeXml(attribute.name)).append("\" VALUE=\"")
            append(escapeXml(attribute.value)).append("\"/>\n")
        }
        val line = tree.lineOf(node.id)
        if (!line.isEmpty) {
            append(indent).append("  <edge")
            line.style?.let { append(" STYLE=\"").append(it.freeMindName).append('"') }
            line.color?.let { append(" COLOR=\"").append(it.toHex()).append('"') }
            line.width?.let { append(" WIDTH=\"").append(it.label).append('"') }
            append("/>\n")
        }
        if (isSummary) append(indent).append("  <hook NAME=\"").append(SUMMARY_HOOK).append("\"/>\n")
        clouds[node.id]?.let { cloud ->
            append(indent).append("  <cloud")
            cloud.color?.let { append(" COLOR=\"").append(it.toHex()).append('"') }
            append("/>\n")
        }
        arrowLinks[node.id]?.forEach { link ->
            append(indent).append("  <arrowlink ID=\"").append(escapeXml(link.id)).append('"')
            append(" DESTINATION=\"").append(escapeXml(link.targetId)).append('"')
            append(" COLOR=\"").append(link.color.toHex()).append('"')
            append(" STARTARROW=\"").append(if (link.hasStartArrow) ARROW else NO_ARROW).append('"')
            append(" ENDARROW=\"").append(if (link.hasEndArrow) ARROW else NO_ARROW).append('"')
            link.lineStyle.toDash()?.let { append(" DASH=\"").append(it).append('"') }
            if (link.thickness != ArrowLinkThickness.NORMAL) {
                append(" WIDTH=\"").append(link.thickness.width.dpVal.roundToInt()).append('"')
            }
            link.startInclination?.let { append(" STARTINCLINATION=\"").append(it.toFreeMind()).append('"') }
            link.endInclination?.let { append(" ENDINCLINATION=\"").append(it.toFreeMind()).append('"') }
            append("/>\n")
        }
        writtenChildren(tree, node).forEach { child ->
            when (child) {
                is WrittenChild.Node ->
                    writeNode(tree, tree.getNode(child.id), depth + 1, arrowLinks, clouds, child.isSummary)
                is WrittenChild.GroupStart -> {
                    append(indent).append("  <node ID=\"").append(escapeXml(child.firstId)).append("_group\" TEXT=\"\"")
                    if (depth == 0) append(" POSITION=\"").append(child.side.name.lowercase()).append('"')
                    append(">\n")
                    append(indent).append("    <hook NAME=\"").append(FIRST_GROUP_HOOK).append("\"/>\n")
                    append(indent).append("  </node>\n")
                }
            }
        }
        append(indent).append("</node>\n")
    }

    private fun readNode(
        tree: Tree<Any>,
        element: XmlElement,
        parentId: String,
        usedIds: MutableSet<String>,
        arrowLinks: MutableList<ArrowLink>,
        clouds: MutableList<Boundary>,
        summaries: MutableList<Pair<String, Set<String>>>,
    ): String {
        val id = element.attributes["ID"]?.takeIf { it.isNotEmpty() && usedIds.add(it) }
            ?: IdGenerator.makeRandomNodeId().also { usedIds.add(it) }
        element.readArrowLinks(id, arrowLinks)
        element.children.firstOrNull { it.name == "cloud" }?.let { cloud ->
            val color = cloud.color("COLOR")?.takeIf { it.luminance() < PALE_CLOUD_LUMINANCE }
            clouds.add(Boundary(IdGenerator.makeRandomNodeId(), parentId, setOf(id), color, BoundaryStyle.SOLID))
        }
        val shape = element.shapeAttribute()?.shape() ?: NodeShape.RECTANGLE
        val children = readChildren(tree, element, id, usedIds, arrowLinks, clouds, summaries, isRoot = false)
            .map { it.second }
        val color = element.color("BACKGROUND_COLOR") ?: shape.defaultColor
        val textColor = element.color("COLOR") ?: shape.defaultTextColor
        val description = element.description()
        val note = element.note()

        val node = unplacedNode(shape, id, parentId, description, children, color, textColor, note)
        tree.setNode(id, node)
        tree.setAttributes(id, element.nodeAttributes())
        tree.setLine(id, element.line())
        tree.setFolded(id, element.attributes["FOLDED"] == "true")
        return id
    }

    // A child as it is written: a node of the tree, or an empty node that marks where a group of siblings a
    // summary is about begins.
    private sealed interface WrittenChild {
        class Node(val id: String, val isSummary: Boolean) : WrittenChild
        // Its id is made from that of the first node of its group, so a map is written the same each time.
        class GroupStart(val side: LayoutSide, val firstId: String) : WrittenChild
    }

    // The children of the node in the order Freeplane takes its summaries from: each summary right after the
    // siblings it is about, with the start of a group marked where it is not the start of the siblings on its side
    // nor right after another summary. Freeplane has a left and a right of the root, on which the children of the
    // root's sides are written together, side by side, so no side splits a group of another.
    private fun writtenChildren(
        tree: Tree<*>
        , node: NodeData<*>
    ): List<WrittenChild> {
        val children = node.children
        if (children.none { tree.isSummary(it) }) return children.map { WrittenChild.Node(it, isSummary = false) }
        val sequences =
            if (node.parentId == null) {
                children
                    .groupBy { tree.sideOf(it).horizontal }
                    .map { (side, ids) -> side to ids.sortedBy { tree.sideOf(it).ordinal } }
            }
            else {
                listOf(LayoutSide.RIGHT to children)
            }
        return sequences.flatMap { (side, ids) -> writtenSequence(tree, side, ids) }
    }

    private fun writtenSequence(
        tree: Tree<*>
        , side: LayoutSide
        , ids: List<String>
    ): List<WrittenChild> {
        val plainIds = ids.filterNot { tree.isSummary(it) }
        val keptRanges = mutableListOf<Pair<String, List<String>>>()
        val coveredIds = mutableSetOf<String>()
        ids
            .filter { tree.isSummary(it) }
            .map { it to tree.summaryChildIds(it) }
            .sortedBy { (_, range) -> plainIds.indexOf(range.first()) }
            .forEach { (summaryId, range) ->
                if (range.none { it in coveredIds }) {
                    keptRanges.add(summaryId to range)
                    coveredIds.addAll(range)
                }
            }
        val summaryAfter = keptRanges.associate { (summaryId, range) -> range.last() to summaryId }
        val groupStarts = keptRanges.map { (_, range) -> range.first() }.toSet()
        val written = mutableListOf<WrittenChild>()
        var isAfterSummary = true
        plainIds.forEach { id ->
            if (id in groupStarts && !isAfterSummary) written.add(WrittenChild.GroupStart(side, id))
            written.add(WrittenChild.Node(id, isSummary = false))
            isAfterSummary = false
            summaryAfter[id]?.let {
                written.add(WrittenChild.Node(it, isSummary = true))
                isAfterSummary = true
            }
        }
        // The summaries written as ordinary nodes go last, past every group, so no summary is about them.
        val keptIds = keptRanges.map { it.first }.toSet()
        ids
            .filter { tree.isSummary(it) && it !in keptIds }
            .forEach { written.add(WrittenChild.Node(it, isSummary = false)) }
        return written
    }

    // Reads the child nodes of [parent], and the summaries among them, as Freeplane takes them. Returns each node
    // read with the element it was read from; the empty nodes that mark where a group begins are not read.
    private fun readChildren(
        tree: Tree<Any>,
        parent: XmlElement,
        parentId: String,
        usedIds: MutableSet<String>,
        arrowLinks: MutableList<ArrowLink>,
        clouds: MutableList<Boundary>,
        summaries: MutableList<Pair<String, Set<String>>>,
        isRoot: Boolean,
    ): List<Pair<XmlElement, String>> {
        val elements = parent.childNodes()
        val ids = elements.associateWith { element ->
            if (element.isGroupStart()) return@associateWith null
            val textElement =
                if (element.hasHook(SUMMARY_HOOK) && element.isEmptyText()) element.childNodes().lastOrNull() else null
            if (textElement == null) {
                readNode(tree, element, parentId, usedIds, arrowLinks, clouds, summaries)
            }
            else {
                // Freeplane may keep a summary's text in a node below an empty one, which it shows in its place.
                readNode(tree, textElement, parentId, usedIds, arrowLinks, clouds, summaries).also { id ->
                    element.childNodes().dropLast(1).forEach { other ->
                        tree.attachNode(readNode(tree, other, id, usedIds, arrowLinks, clouds, summaries), id)
                    }
                }
            }
        }
        val sides = if (isRoot) elements.groupBy { it.attributes["POSITION"] == "left" }.values else listOf(elements)
        sides.forEach { side -> summaries.addAll(summariesOf(side, ids)) }
        return elements.mapNotNull { element -> ids[element]?.let { element to it } }
    }

    // The summaries of one side of a node's children, as Freeplane's SummaryLevels finds them. A summary right after
    // other summaries is a summary of them, which this library has not, so only those of nodes are kept.
    private fun summariesOf(
        elements: List<XmlElement>
        , ids: Map<XmlElement, String?>
    ): List<Pair<String, Set<String>>> {
        val levels = IntArray(elements.size)
        var level = 0
        var isSummaryAnItem = false
        elements.forEachIndexed { index, element ->
            val isItem = !element.hasHook(SUMMARY_HOOK) || isSummaryAnItem
            if (isItem) {
                if (level > 0) isSummaryAnItem = true
                level = 0
                if (!element.isGroupStart()) isSummaryAnItem = false
            }
            else {
                level++
            }
            levels[index] = level
        }
        return elements.indices.filter { levels[it] == 1 }.mapNotNull { summaryIndex ->
            val summaryId = ids[elements[summaryIndex]] ?: return@mapNotNull null
            val childIds = mutableSetOf<String>()
            for (index in summaryIndex - 1 downTo 0) {
                if (levels[index] >= 1 || elements[index].hasHook(FIRST_GROUP_HOOK)) break
                ids[elements[index]]?.let { childIds.add(it) }
            }
            childIds.takeIf { it.isNotEmpty() }?.let { summaryId to it }
        }
    }

    private fun XmlElement.hasHook(name: String): Boolean =
        children.any { it.name == "hook" && it.attributes["NAME"] == name }

    private fun XmlElement.isEmptyText(): Boolean = attributes["TEXT"].isNullOrEmpty()
        && children.none { it.name == "richcontent" && it.attributes["TYPE"] == "NODE" }

    // Freeplane hides an empty node that marks the start of a group, which is there for nothing else.
    private fun XmlElement.isGroupStart(): Boolean =
        hasHook(FIRST_GROUP_HOOK) && isEmptyText() && childNodes().isEmpty()

    private fun NodeData<*>.shape(): NodeShape = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
        is SquareNodeData -> NodeShape.SQUARE
        is LineNodeData -> NodeShape.LINE
        is StadiumNodeData -> NodeShape.STADIUM
        is OvalNodeData -> NodeShape.OVAL
    }

    private fun XmlElement.childNodes() = children.filter { it.name == "node" }

    private fun XmlElement.shape(): NodeShape? {
        return NodeShape.entries.firstOrNull { it.name.equals(attributes["VALUE"], ignoreCase = true) }
    }

    // The attribute that holds the shape of the node, which is none of the node's own attributes.
    private fun XmlElement.shapeAttribute(): XmlElement? {
        return children.firstOrNull {
            it.name == "attribute" && it.attributes["NAME"] == SHAPE_ATTRIBUTE && it.shape() != null
        }
    }

    private fun XmlElement.nodeAttributes(): List<NodeAttribute> {
        val shapeAttribute = shapeAttribute()
        return children
            .filter { it.name == "attribute" && it !== shapeAttribute }
            .map { NodeAttribute(it.attributes["NAME"].orEmpty(), it.attributes["VALUE"].orEmpty()) }
    }

    // FreeMind draws an arrowhead unless it is told there is none, at the end; at the start it draws none.
    private fun XmlElement.readArrowLinks(
        sourceId: String,
        arrowLinks: MutableList<ArrowLink>,
    ) {
        children.filter { it.name == "arrowlink" }.forEach { element ->
            val targetId = element.attributes["DESTINATION"]?.takeIf { it.isNotEmpty() } ?: return@forEach
            arrowLinks.add(
                ArrowLink(
                    id = element.attributes["ID"]?.takeIf { it.isNotEmpty() } ?: IdGenerator.makeRandomNodeId(),
                    sourceId = sourceId,
                    targetId = targetId,
                    color = element.color("COLOR") ?: ArrowLink.DEFAULT_COLOR,
                    hasStartArrow = element.attributes["STARTARROW"].equals(ARROW, ignoreCase = true),
                    hasEndArrow = !element.attributes["ENDARROW"].equals(NO_ARROW, ignoreCase = true),
                    lineStyle = lineStyleOfDash(element.attributes["DASH"]),
                    thickness = thicknessOfWidth(element.attributes["WIDTH"]),
                    startInclination = element.inclination("STARTINCLINATION"),
                    endInclination = element.inclination("ENDINCLINATION"),
                ),
            )
        }
    }

    // FreeMind has no dashed arrow links. Freeplane keeps the pattern in DASH, which FreeMind ignores.
    private fun ArrowLinkLineStyle.toDash(): String? = when (this) {
        ArrowLinkLineStyle.SOLID -> null
        ArrowLinkLineStyle.DASHED -> DASHES
        ArrowLinkLineStyle.DOTTED -> DOTS
        ArrowLinkLineStyle.DASH_DOT -> DASHES_AND_DOTS
    }

    // Freeplane has more kinds of dashes than there are line styles, so dots close together are dots too,
    // and any other pattern is dashes.
    private fun lineStyleOfDash(
        dash: String?
    ): ArrowLinkLineStyle = when (dash?.trim()) {
        null, "" -> ArrowLinkLineStyle.SOLID
        DOTS, CLOSE_DOTS -> ArrowLinkLineStyle.DOTTED
        DASHES_AND_DOTS -> ArrowLinkLineStyle.DASH_DOT
        else -> ArrowLinkLineStyle.DASHED
    }

    // Freeplane keeps the width of a line in pixels. A width between two thicknesses is the closest of them.
    private fun thicknessOfWidth(
        width: String?
    ): ArrowLinkThickness {
        val pixels = width?.trim()?.toFloatOrNull() ?: return ArrowLinkThickness.NORMAL
        return ArrowLinkThickness.entries.minBy { abs(it.width.dpVal - pixels) }
    }

    // FreeMind writes an inclination as "x;y;".
    private fun XmlElement.inclination(attribute: String): ArrowLinkInclination? {
        val parts = attributes[attribute]?.split(';')?.mapNotNull { it.trim().toFloatOrNull() } ?: return null
        return if (parts.size >= 2) ArrowLinkInclination(Dp(parts[0]), Dp(parts[1])) else null
    }

    private fun XmlElement.line(): NodeLine {
        val edge = children.firstOrNull { it.name == "edge" } ?: return NodeLine()
        return NodeLine(
            NodeLineStyle.entries.firstOrNull { it.freeMindName == edge.attributes["STYLE"] },
            edge.attributes["COLOR"]?.let { hexColor(it.trim()) },
            edge.attributes["WIDTH"]?.let { nearestWidth(it.trim()) },
        )
    }

    // FreeMind writes a thin line as "thin" or 0, and a line as wide as its parent's as a negative width, which is
    // inherited here. Ties go to the wider.
    private fun nearestWidth(
        value: String,
    ): NodeLineWidth? {
        if (value == NodeLineWidth.THIN.label) return NodeLineWidth.THIN
        val number = value.toIntOrNull() ?: return null
        if (number < 0) return null
        if (number == 0) return NodeLineWidth.THIN
        return NodeLineWidth.entries
            .filter { it != NodeLineWidth.THIN }
            .sortedByDescending { it.label.toInt() }
            .minByOrNull { abs(it.label.toInt() - number) }
    }

    private fun ArrowLinkInclination.toFreeMind(): String = "${x.dpVal.roundToInt()};${y.dpVal.roundToInt()};"

    private fun XmlElement.color(attribute: String): Color? = attributes[attribute]?.let { hexColor(it) }

    private fun hexColor(value: String): Color? {
        val hex = value.removePrefix("#").takeIf { it.length == 6 } ?: return null
        val rgb = hex.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or rgb)
    }

    // FreeMind keeps a node's text in TEXT when it is plain, and in a <richcontent> child when it is formatted.
    // The <font> of a node styles all of its text, which a description holds as the rich text of its own.
    private fun XmlElement.description(): String {
        val richContent = children.firstOrNull { it.name == "richcontent" && it.attributes["TYPE"] == "NODE" }?.rawContent
        val description = richContent?.let { htmlBody(it) } ?: RichText.fromPlainText(attributes["TEXT"].orEmpty())
        val style = children.firstOrNull { it.name == "font" }?.fontStyle() ?: return description
        return RichText.withStyle(description, style)
    }

    // The style of FreeMind's <font>, or null when it is the style text has anyway. Its size is in points, of
    // which FreeMind's default has 12, and is the nearest size text can have. Its NAME is not kept: nodes share
    // their font.
    private fun XmlElement.fontStyle(): SpanStyle? {
        val size = attributes["SIZE"]?.toFloatOrNull()?.let { RichTextSize.of((it / DEFAULT_FONT_POINTS).em) }
        val style = SpanStyle(
            fontWeight = if (attributes["BOLD"] == "true") FontWeight.Bold else null
            , fontStyle = if (attributes["ITALIC"] == "true") FontStyle.Italic else null
        ).merge(size?.spanStyle)
        return style.takeIf { it != SpanStyle() }
    }

    // FreeMind keeps a node's note in a <richcontent> child of its own.
    private fun XmlElement.note(): String {
        val richContent = children.firstOrNull { it.name == "richcontent" && it.attributes["TYPE"] == "NOTE" }?.rawContent
        return richContent?.let { htmlBody(it) }.orEmpty()
    }

    private fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')

    // The description without markup, or null when it needs markup to be represented.
    private fun plainTextOrNull(description: String): String? {
        val trimmed = description.trim()
        val isSingleParagraph = trimmed.startsWith("<p>") && trimmed.endsWith("</p>") && trimmed.indexOf("<p>", 3) == -1
        val inner = if (isSingleParagraph) trimmed.substring(3, trimmed.length - 4) else trimmed
        return if ('<' in inner) null else decodeEntities(inner)
    }

    // <richcontent> holds XHTML, so the HTML of a description has to be made well-formed XML.
    private fun toXhtml(html: String): String {
        return html
            .replace(Regex("<br\\s*>"), "<br/>")
            .replace(Regex("<img(\\s[^>]*?)?\\s*/?>"), "<img$1/>")
            .replace("&nbsp;", "&#160;")
    }

    private fun htmlBody(richContent: String): String {
        val bodyStart = richContent.indexOf("<body")
        val contentStart = if (bodyStart == -1) -1 else richContent.indexOf('>', bodyStart)
        val contentEnd = richContent.lastIndexOf("</body>")
        val body = if (contentStart != -1 && contentEnd > contentStart) richContent.substring(contentStart + 1, contentEnd) else richContent
        // FreeMind indents the markup it writes; that whitespace is not part of the text.
        return body
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(<(?:p|li|ul|ol|div)(?: [^>]*)?>) "), "$1")
            .replace(Regex(" (</?(?:p|li|ul|ol|div)[ >])"), "$1")
            .trim()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "&#xa;")
    }
}
