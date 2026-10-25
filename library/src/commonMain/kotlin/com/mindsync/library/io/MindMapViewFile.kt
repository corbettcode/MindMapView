package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.ArrowLinkLineStyle
import com.mindsync.library.data.ArrowLinkThickness
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.data.shape
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.IdGenerator

/**
 * Reads and writes mind maps in this library's own `.mmv` format, which keeps all there is of a [Tree]:
 * each node's shape, colors, rich text, note, attributes and whether it is folded, the side of the root each
 * of its children is on, the arrow links, and where the root is when the user dragged it there.
 *
 * It is XML. The root `<mindmapview>` element holds the root `<node>`, which holds the nodes below it in
 * order, and then the `<arrowlink>` elements. A node's text and note are the HTML they are kept as. Where the
 * other nodes are is not stored: a map that was read needs to be arranged, as one read with [FreeMind] does.
 */
object MindMapViewFile {
    const val EXTENSION = "mmv"

    private const val FORMAT_VERSION = 1
    private const val ROOT_ELEMENT = "mindmapview"

    fun write(tree: Tree<*>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<").append(ROOT_ELEMENT).append(" version=\"").append(FORMAT_VERSION).append("\">\n")
        val root = tree.getRootNode()
        if (tree.isRootDragged) {
            append("  <rootposition x=\"").append(root.path.centerX.dpVal)
            append("\" y=\"").append(root.path.centerY.dpVal).append("\"/>\n")
        }
        writeNode(tree, root, depth = 1)
        // A node that was removed is not written, so neither is an arrow link to or from one.
        val writtenIds = mutableSetOf<String>()
        tree.doPreorderTraversal { node -> writtenIds.add(node.id) }
        tree.arrowLinks
            .filter { it.sourceId in writtenIds && it.targetId in writtenIds }
            .forEach { writeArrowLink(it) }
        append("</").append(ROOT_ELEMENT).append(">\n")
    }

    /**
     * @throws IllegalArgumentException when [content] is not a mind map in this format.
     */
    fun read(content: String): Tree<Any> {
        val map = XmlParser(content).parse().firstOrNull { it.name == ROOT_ELEMENT }
            ?: throw IllegalArgumentException("Not a MindMapView map: no <$ROOT_ELEMENT> element")
        val rootElement = map.children.firstOrNull { it.name == "node" }
            ?: throw IllegalArgumentException("Not a MindMapView map: no root <node> element")

        val tree = Tree<Any>()
        val root = tree.getRootNode()
        val usedIds = mutableSetOf(root.id)
        // The root is given this library's own id, whatever id it has in the file.
        val fileIds = mutableMapOf(rootElement.attributes["id"].orEmpty() to root.id)
        val children = rootElement.childNodes().map { readNode(tree, it, root.id, usedIds, fileIds) }
        tree.setRootNode(
            root.copy(
                children = children
                , color = rootElement.color("color") ?: root.color
                , description = rootElement.attributes["text"].orEmpty()
                , note = rootElement.attributes["note"].orEmpty()
                , textColor = rootElement.color("textColor") ?: root.textColor
            )
        )
        tree.setAttributes(root.id, rootElement.nodeAttributes())
        rootElement.childNodes().zip(children).forEach { (element, id) ->
            val side = LayoutSide.entries.firstOrNull { it.name.equals(element.attributes["side"], ignoreCase = true) }
            if (side != null) tree.setRootChildSide(id, side)
        }
        map.children.firstOrNull { it.name == "rootposition" }?.let { position ->
            val x = position.attributes["x"]?.toFloatOrNull()
            val y = position.attributes["y"]?.toFloatOrNull()
            if (x != null && y != null) tree.moveRoot(Dp(x), Dp(y))
        }
        val usedArrowLinkIds = mutableSetOf<String>()
        map.children.filter { it.name == "arrowlink" }.forEach { element ->
            val sourceId = fileIds[element.attributes["source"]] ?: return@forEach
            val targetId = fileIds[element.attributes["target"]] ?: return@forEach
            if (sourceId == targetId) return@forEach
            val id = element.attributes["id"]?.takeIf { it.isNotEmpty() && usedArrowLinkIds.add(it) }
                ?: IdGenerator.makeRandomNodeId()
            tree.addArrowLink(
                ArrowLink(
                    id = id
                    , sourceId = sourceId
                    , targetId = targetId
                    , color = element.color("color") ?: ArrowLink.DEFAULT_COLOR
                    , hasStartArrow = element.attributes["startArrow"] == "true"
                    , hasEndArrow = element.attributes["endArrow"] != "false"
                    , lineStyle = ArrowLinkLineStyle
                        .entries
                        .firstOrNull { it.name.lowercase() == element.attributes["lineStyle"] }
                        ?: ArrowLinkLineStyle.SOLID
                    , thickness = ArrowLinkThickness
                        .entries
                        .firstOrNull { it.name.lowercase() == element.attributes["thickness"] }
                        ?: ArrowLinkThickness.NORMAL
                    , startInclination = element.inclination("startInclination")
                    , endInclination = element.inclination("endInclination")
                )
            )
        }
        return tree
    }

    private fun StringBuilder.writeNode(
        tree: Tree<*>
        , node: NodeData<*>
        , depth: Int
    ) {
        val indent = "  ".repeat(depth)
        append(indent).append("<node")
        attribute("id", node.id)
        // The root is always an oval.
        if (node.parentId != null) attribute("shape", node.shape.name.lowercase())
        if (node.parentId == tree.getRootNode().id) attribute("side", tree.sideOf(node.id).name.lowercase())
        if (tree.isFolded(node.id)) attribute("folded", "true")
        attribute("color", node.color.toHex())
        attribute("textColor", node.textColor.toHex())
        attribute("text", node.description)
        if (node.note.isNotEmpty()) attribute("note", node.note)
        val attributes = tree.attributesOf(node.id)
        if (attributes.isEmpty() && node.children.isEmpty()) {
            append("/>\n")
            return
        }
        append(">\n")
        attributes.forEach { nodeAttribute ->
            append(indent).append("  <attribute")
            attribute("name", nodeAttribute.name)
            attribute("value", nodeAttribute.value)
            append("/>\n")
        }
        node.children.forEach { writeNode(tree, tree.getNode(it), depth + 1) }
        append(indent).append("</node>\n")
    }

    private fun StringBuilder.writeArrowLink(link: ArrowLink) {
        append("  <arrowlink")
        attribute("id", link.id)
        attribute("source", link.sourceId)
        attribute("target", link.targetId)
        attribute("color", link.color.toHex())
        attribute("startArrow", link.hasStartArrow.toString())
        attribute("endArrow", link.hasEndArrow.toString())
        attribute("lineStyle", link.lineStyle.name.lowercase())
        attribute("thickness", link.thickness.name.lowercase())
        link.startInclination?.let { attribute("startInclination", "${it.x.dpVal};${it.y.dpVal}") }
        link.endInclination?.let { attribute("endInclination", "${it.x.dpVal};${it.y.dpVal}") }
        append("/>\n")
    }

    private fun StringBuilder.attribute(
        name: String
        , value: String
    ) {
        append(' ').append(name).append("=\"").append(escapeAttribute(value)).append('"')
    }

    private fun readNode(
        tree: Tree<Any>
        , element: XmlElement
        , parentId: String
        , usedIds: MutableSet<String>
        , fileIds: MutableMap<String, String>
    ): String {
        val fileId = element.attributes["id"].orEmpty()
        val id = fileId.takeIf { it.isNotEmpty() && usedIds.add(it) }
            ?: IdGenerator.makeRandomNodeId().also { usedIds.add(it) }
        if (fileId.isNotEmpty() && fileId !in fileIds) fileIds[fileId] = id
        val shape = NodeShape.entries.firstOrNull { it.name.equals(element.attributes["shape"], ignoreCase = true) }
            ?: NodeShape.RECTANGLE
        val children = element.childNodes().map { readNode(tree, it, id, usedIds, fileIds) }
        val node = unplacedNode(
            shape
            , id
            , parentId
            , element.attributes["text"].orEmpty()
            , children
            , element.color("color") ?: shape.defaultColor
            , element.color("textColor") ?: shape.defaultTextColor
            , element.attributes["note"].orEmpty()
        )
        tree.setNode(id, node)
        tree.setAttributes(id, element.nodeAttributes())
        tree.setFolded(id, element.attributes["folded"] == "true")
        return id
    }

    private fun XmlElement.childNodes() = children.filter { it.name == "node" }

    private fun XmlElement.nodeAttributes(): List<NodeAttribute> =
        children
            .filter { it.name == "attribute" }
            .map { NodeAttribute(it.attributes["name"].orEmpty(), it.attributes["value"].orEmpty()) }

    private fun XmlElement.inclination(attribute: String): ArrowLinkInclination? {
        val parts = attributes[attribute]?.split(';')?.mapNotNull { it.trim().toFloatOrNull() } ?: return null
        return if (parts.size == 2) ArrowLinkInclination(Dp(parts[0]), Dp(parts[1])) else null
    }

    // Colors are written with their alpha, as #AARRGGBB.
    private fun XmlElement.color(attribute: String): Color? {
        val hex = attributes[attribute]?.removePrefix("#")?.takeIf { it.length == 8 } ?: return null
        return hex.toLongOrNull(16)?.let { Color(it) }
    }

    private fun Color.toHex(): String = "#" + (toArgb().toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')

    // Line breaks and tabs are escaped too, as XML would otherwise read them as spaces.
    private fun escapeAttribute(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "&#xa;")
            .replace("\r", "&#xd;")
            .replace("\t", "&#x9;")
    }
}
