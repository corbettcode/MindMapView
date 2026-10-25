package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.ArrowLinkLineStyle
import com.mindsync.library.data.ArrowLinkThickness
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
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

    fun write(tree: Tree<*>): String = buildString {
        append("<map version=\"1.0.1\">\n")
        append("<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->\n")
        // A node that was removed is not written, so neither is an arrow link to one.
        val writtenIds = mutableSetOf<String>()
        tree.doPreorderTraversal { node -> writtenIds.add(node.id) }
        val arrowLinks = tree.arrowLinks.filter { it.targetId in writtenIds }.groupBy { it.sourceId }
        writeNode(tree, tree.getRootNode(), depth = 0, arrowLinks)
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
        val children = rootElement.childNodes().map { element ->
            readNode(tree, element, root.id, usedIds, arrowLinks).also { id ->
                if (element.attributes["POSITION"] == "left") leftChildren.add(id)
            }
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
        leftChildren.forEach { tree.setRootChildSide(it, LayoutSide.LEFT) }
        val usedArrowLinkIds = mutableSetOf<String>()
        arrowLinks
            // The root is given this library's own id, whatever id it has in the file.
            .map { if (it.targetId == rootElement.attributes["ID"]) it.copy(targetId = root.id) else it }
            .filter { it.targetId in tree.nodes && it.targetId != it.sourceId }
            .forEach { link ->
                tree.addArrowLink(if (usedArrowLinkIds.add(link.id)) link else link.copy(id = IdGenerator.makeRandomNodeId()))
            }
        return tree
    }

    private fun StringBuilder.writeNode(
        tree: Tree<*>,
        node: NodeData<*>,
        depth: Int,
        arrowLinks: Map<String, List<ArrowLink>>,
    ) {
        val indent = "  ".repeat(depth)
        val plainText = plainTextOrNull(node.description)

        append(indent).append("<node ID=\"").append(escapeXml(node.id)).append('"')
        if (plainText != null) append(" TEXT=\"").append(escapeXml(plainText)).append('"')
        if (depth == 1) append(" POSITION=\"").append(tree.sideOf(node.id).horizontal.name.lowercase()).append('"')
        if (tree.isFolded(node.id)) append(" FOLDED=\"true\"")
        append(" BACKGROUND_COLOR=\"").append(node.color.toHex()).append('"')
        append(" COLOR=\"").append(node.textColor.toHex()).append("\">\n")
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
        node.children.forEach { writeNode(tree, tree.getNode(it), depth + 1, arrowLinks) }
        append(indent).append("</node>\n")
    }

    private fun readNode(
        tree: Tree<Any>,
        element: XmlElement,
        parentId: String,
        usedIds: MutableSet<String>,
        arrowLinks: MutableList<ArrowLink>,
    ): String {
        val id = element.attributes["ID"]?.takeIf { it.isNotEmpty() && usedIds.add(it) }
            ?: IdGenerator.makeRandomNodeId().also { usedIds.add(it) }
        element.readArrowLinks(id, arrowLinks)
        val shape = element.shapeAttribute()?.shape() ?: NodeShape.RECTANGLE
        val children = element.childNodes().map { readNode(tree, it, id, usedIds, arrowLinks) }
        val color = element.color("BACKGROUND_COLOR") ?: shape.defaultColor
        val textColor = element.color("COLOR") ?: shape.defaultTextColor
        val description = element.description()
        val note = element.note()

        val node = unplacedNode(shape, id, parentId, description, children, color, textColor, note)
        tree.setNode(id, node)
        tree.setAttributes(id, element.nodeAttributes())
        tree.setFolded(id, element.attributes["FOLDED"] == "true")
        return id
    }

    private fun NodeData<*>.shape(): NodeShape = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
        is SquareNodeData -> NodeShape.SQUARE
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

    private fun ArrowLinkInclination.toFreeMind(): String = "${x.dpVal.roundToInt()};${y.dpVal.roundToInt()};"

    private fun XmlElement.color(attribute: String): Color? {
        val hex = attributes[attribute]?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
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
