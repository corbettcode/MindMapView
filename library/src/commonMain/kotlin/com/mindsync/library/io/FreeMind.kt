package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.CirclePath
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalPath
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.RectanglePath
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.IdGenerator
import kotlin.math.roundToInt

/**
 * Reads and writes mind maps in the FreeMind `.mm` format.
 *
 * FreeMind has no notion of this library's node shapes, so a node's shape is kept in an
 * `<attribute NAME="shape">` child, which FreeMind preserves. It is written in front of the node's own
 * attributes, which are FreeMind's `<attribute>` children as well: the first one named `shape` that holds a
 * shape is read as the shape, and every other one as an attribute. The side of the root a child of the root is
 * on is kept in FreeMind's own `POSITION`. Arrow links are kept as FreeMind's own `<arrowlink>` children of
 * the node they start at. Node positions are not stored: a map that
 * was read needs to be arranged, for example with `MindMapState.animateTreeChange()`.
 */
object FreeMind {
    private const val SHAPE_ATTRIBUTE = "shape"
    private const val ARROW = "Default"
    private const val NO_ARROW = "None"

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
        if (depth == 1) append(" POSITION=\"").append(tree.sideOf(node.id).name.lowercase()).append('"')
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

        val node = when (shape) {
            NodeShape.CIRCLE -> CircleNodeData(
                id = id,
                parentId = parentId,
                path = CirclePath(Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
                note = note,
            )
            NodeShape.RECTANGLE -> RectangleNodeData(
                id = id,
                parentId = parentId,
                path = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
                note = note,
            )
            NodeShape.STADIUM -> StadiumNodeData(
                id = id,
                parentId = parentId,
                path = StadiumPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
                note = note,
            )
            NodeShape.OVAL -> OvalNodeData(
                id = id,
                parentId = parentId,
                path = OvalPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
                note = note,
            )
        }
        tree.setNode(id, node)
        tree.setAttributes(id, element.nodeAttributes())
        tree.setFolded(id, element.attributes["FOLDED"] == "true")
        return id
    }

    private fun NodeData<*>.shape(): NodeShape = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
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
                    startInclination = element.inclination("STARTINCLINATION"),
                    endInclination = element.inclination("ENDINCLINATION"),
                ),
            )
        }
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
    private fun XmlElement.description(): String {
        val richContent = children.firstOrNull { it.name == "richcontent" && it.attributes["TYPE"] == "NODE" }?.rawContent
        if (richContent != null) return htmlBody(richContent)
        return escapeHtml(attributes["TEXT"].orEmpty())
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
        return html.replace(Regex("<br\\s*>"), "<br/>").replace("&nbsp;", "&#160;")
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

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
    }
}

private class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val rawContent: String?,
)

private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

private fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x") -> entity.substring(2).toIntOrNull(16)?.let(::codePointToString)
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePointToString)
            entity == "amp" -> "&"
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity == "nbsp" -> "\u00A0"
            else -> null
        } ?: match.value
    }
}

private fun codePointToString(codePoint: Int): String? {
    return when {
        codePoint in 0..0xFFFF -> codePoint.toChar().toString()
        codePoint <= 0x10FFFF -> {
            val offset = codePoint - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
        else -> null
    }
}

/**
 * Just enough of an XML parser for FreeMind maps: elements and attributes, with text content ignored. The
 * content of a <richcontent> element is HTML, so it is kept as it is written instead of being parsed.
 */
private class XmlParser(private val source: String) {
    private var position = 0

    fun parse(): List<XmlElement> = parseElements()

    private fun parseElements(): List<XmlElement> {
        val elements = mutableListOf<XmlElement>()
        while (true) {
            position = source.indexOf('<', position)
            if (position == -1) {
                position = source.length
                return elements
            }
            when {
                source.startsWith("<!--", position) -> skipPast("-->")
                source.startsWith("<?", position) -> skipPast("?>")
                source.startsWith("<!", position) -> skipPast(">")
                source.startsWith("</", position) -> {
                    skipPast(">")
                    return elements
                }
                else -> elements.add(parseElement())
            }
        }
    }

    private fun parseElement(): XmlElement {
        position++
        val name = readWhile { !it.isWhitespace() && it != '/' && it != '>' }
        val attributes = mutableMapOf<String, String>()
        while (true) {
            skipWhitespace()
            when (peek()) {
                '>' -> {
                    position++
                    break
                }
                '/' -> {
                    skipPast(">")
                    return XmlElement(name, attributes, emptyList(), null)
                }
                else -> {
                    val attributeName = readWhile { !it.isWhitespace() && it != '=' && it != '/' && it != '>' }
                    skipWhitespace()
                    if (peek() != '=') continue
                    position++
                    skipWhitespace()
                    val quote = peek()
                    if (quote != '"' && quote != '\'') throw malformed()
                    position++
                    val end = source.indexOf(quote, position)
                    if (end == -1) throw malformed()
                    attributes[attributeName] = decodeEntities(source.substring(position, end))
                    position = end + 1
                }
            }
        }
        if (name == "richcontent") {
            val end = source.indexOf("</richcontent>", position)
            if (end == -1) throw malformed()
            val rawContent = source.substring(position, end)
            position = end + "</richcontent>".length
            return XmlElement(name, attributes, emptyList(), rawContent)
        }
        return XmlElement(name, attributes, parseElements(), null)
    }

    private fun peek(): Char = if (position < source.length) source[position] else throw malformed()

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun readWhile(predicate: (Char) -> Boolean): String {
        val start = position
        while (position < source.length && predicate(source[position])) position++
        if (position == start) throw malformed()
        return source.substring(start, position)
    }

    private fun skipPast(terminator: String) {
        val end = source.indexOf(terminator, position)
        if (end == -1) throw malformed()
        position = end + terminator.length
    }

    private fun malformed() = IllegalArgumentException("Not a FreeMind map: malformed XML near character $position")
}
