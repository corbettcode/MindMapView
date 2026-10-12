package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.CirclePath
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.RectanglePath
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.IdGenerator

/**
 * Reads and writes mind maps in the FreeMind `.mm` format.
 *
 * FreeMind has no notion of this library's node shapes, so a node's shape is kept in an
 * `<attribute NAME="shape">` child, which FreeMind preserves. The side of the root a child of the root is
 * on is kept in FreeMind's own `POSITION`. Node positions are not stored: a map that
 * was read needs to be arranged, for example with `MindMapState.animateTreeChange()`.
 */
object FreeMind {
    private const val SHAPE_ATTRIBUTE = "shape"

    fun write(tree: Tree<*>): String = buildString {
        append("<map version=\"1.0.1\">\n")
        append("<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->\n")
        writeNode(tree, tree.getRootNode(), depth = 0)
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
        val children = rootElement.childNodes().map { element ->
            readNode(tree, element, root.id, usedIds).also { id ->
                if (element.attributes["POSITION"] == "left") leftChildren.add(id)
            }
        }
        tree.setRootNode(
            root.copy(
                children = children,
                color = rootElement.color("BACKGROUND_COLOR") ?: root.color,
                description = rootElement.description(),
                textColor = rootElement.color("COLOR") ?: root.textColor,
            ),
        )
        leftChildren.forEach { tree.setRootChildSide(it, LayoutSide.LEFT) }
        return tree
    }

    private fun StringBuilder.writeNode(tree: Tree<*>, node: NodeData<*>, depth: Int) {
        val indent = "  ".repeat(depth)
        val plainText = plainTextOrNull(node.description)

        append(indent).append("<node ID=\"").append(escapeXml(node.id)).append('"')
        if (plainText != null) append(" TEXT=\"").append(escapeXml(plainText)).append('"')
        if (depth == 1) append(" POSITION=\"").append(tree.sideOf(node.id).name.lowercase()).append('"')
        append(" BACKGROUND_COLOR=\"").append(node.color.toHex()).append('"')
        append(" COLOR=\"").append(node.textColor.toHex()).append("\">\n")
        if (plainText == null) {
            append(indent).append("  <richcontent TYPE=\"NODE\"><html><head></head><body>")
            append(toXhtml(node.description))
            append("</body></html></richcontent>\n")
        }
        append(indent).append("  <attribute NAME=\"").append(SHAPE_ATTRIBUTE).append("\" VALUE=\"")
        append(node.shape().name.lowercase()).append("\"/>\n")
        node.children.forEach { writeNode(tree, tree.getNode(it), depth + 1) }
        append(indent).append("</node>\n")
    }

    private fun readNode(
        tree: Tree<Any>,
        element: XmlElement,
        parentId: String,
        usedIds: MutableSet<String>,
    ): String {
        val id = element.attributes["ID"]?.takeIf { it.isNotEmpty() && usedIds.add(it) }
            ?: IdGenerator.makeRandomNodeId().also { usedIds.add(it) }
        val shape = element.children
            .firstOrNull { it.name == "attribute" && it.attributes["NAME"] == SHAPE_ATTRIBUTE }
            ?.let { attribute -> NodeShape.entries.firstOrNull { it.name.equals(attribute.attributes["VALUE"], ignoreCase = true) } }
            ?: NodeShape.RECTANGLE
        val children = element.childNodes().map { readNode(tree, it, id, usedIds) }
        val color = element.color("BACKGROUND_COLOR") ?: shape.defaultColor
        val textColor = element.color("COLOR") ?: shape.defaultTextColor
        val description = element.description()

        val node = when (shape) {
            NodeShape.CIRCLE -> CircleNodeData(
                id = id,
                parentId = parentId,
                path = CirclePath(Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
            )
            NodeShape.RECTANGLE -> RectangleNodeData(
                id = id,
                parentId = parentId,
                path = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
            )
            NodeShape.STADIUM -> StadiumNodeData(
                id = id,
                parentId = parentId,
                path = StadiumPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
                description = description,
                children = children,
                color = color,
                textColor = textColor,
            )
        }
        tree.setNode(id, node)
        return id
    }

    private fun NodeData<*>.shape(): NodeShape = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
        is StadiumNodeData -> NodeShape.STADIUM
    }

    private fun XmlElement.childNodes() = children.filter { it.name == "node" }

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
