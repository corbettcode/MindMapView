package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.util.IdGenerator
import com.mindsync.library.util.RichText

/**
 * Reads an XMind map as a mind map: the content.json of XMind 2020 and later, or the content.xml of XMind 8
 * and before.
 *
 * Each topic becomes a node, with its title as the node's text and its notes as the node's note. Attached
 * topics come first among a topic's children, followed by its floating topics, summaries and callouts. A
 * map with one sheet has that sheet's central topic as its root. A map with several sheets gets a root named
 * after its title, with each sheet's central topic as a branch.
 *
 * Markers become emojis in front of the text, labels become a "Labels" attribute, a web link makes the text a
 * link, and relationships become arrow links. Fill and text colors, bold and italic text, the shapes this
 * library has and folded branches are kept. Boundaries are kept too, without their titles. Images and the theme are
 * not.
 *
 * Node positions are not stored: a map that was read needs to be arranged, for example with
 * `MindMapState.animateTreeChange()`.
 */
object XMind {
    const val EXTENSION = "xmind"

    private const val CONTENT_JSON = "content.json"
    private const val CONTENT_XML = "content.xml"
    private const val STYLES_XML = "styles.xml"
    private val MANIFESTS = listOf("manifest.json", "META-INF/manifest.xml")
    private const val ENCRYPTION = "encryption-data"
    private const val ATTACHED = "attached"
    private const val MASTER = "master"
    private const val LABELS_ATTRIBUTE = "Labels"

    private val LINK_SCHEMES = listOf("http://", "https://", "mailto:", "file:")
    private val IMAGE = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val PRIORITY = Regex("priority-([1-9])")
    private val MARKER_EMOJIS = mapOf(
        "task-start" to "⬜"
        , "task-done" to "✅"
        , "task-pause" to "⏸️"
        , "smiley-smile" to "🙂"
        , "smiley-laugh" to "😄"
        , "smiley-angry" to "😠"
        , "smiley-cry" to "😢"
        , "smiley-surprise" to "😮"
        , "smiley-boring" to "😐"
        , "arrow-up" to "⬆️"
        , "arrow-down" to "⬇️"
        , "arrow-left" to "⬅️"
        , "arrow-right" to "➡️"
        , "arrow-refresh" to "🔄"
        , "symbol-plus" to "➕"
        , "symbol-minus" to "➖"
        , "symbol-question" to "❓"
        , "symbol-exclam" to "❗"
        , "symbol-info" to "ℹ️"
        , "symbol-wrong" to "❌"
        , "symbol-right" to "✔️"
    )
    // The markers of a kind that come in several colors, or in steps of progress for a task.
    private val MARKER_KIND_EMOJIS = mapOf(
        "task" to "⏳"
        , "flag" to "🚩"
        , "star" to "⭐"
        , "people" to "👤"
    )

    private data class Topic(
        val id: String?
        , val title: String
        , val noteHtml: String?
        , val notePlain: String?
        , val labels: List<String>
        , val markers: List<String>
        , val link: String?
        , val style: Map<String, String>
        , val isFolded: Boolean
        , val children: List<Topic>
        // How many of the children are attached ones, which come first, and which the ranges of boundaries count.
        , val attachedCount: Int = 0
        // The attached children each boundary is around, by their places among them, or null for one that is around
        // the topic itself.
        , val boundaries: List<IntRange?> = emptyList()
    )

    private class Sheet(
        val title: String
        , val root: Topic
        , val relationships: List<Pair<String, String>>
    )

    /**
     * Reads [content], the bytes of an .xmind file, as a mind map whose root is named [title] when the map has
     * several sheets.
     *
     * @throws IllegalArgumentException when [content] is not an XMind map, or is one that is password-protected.
     */
    fun read(
        content: ByteArray
        , title: String
    ): Tree<Any> {
        val archive = try {
            ZipArchive(content)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Not an XMind map: ${e.message}")
        }
        val isEncrypted = MANIFESTS.any { name -> archive.read(name)?.decodeToString()?.contains(ENCRYPTION) == true }
        if (isEncrypted) throw IllegalArgumentException("The map is password-protected")
        // A map of XMind 2020 has a content.xml too, which only says to open the map in a newer XMind.
        val sheets = archive.read(CONTENT_JSON)?.let { jsonSheets(it.decodeToString()) }
            ?: archive.read(CONTENT_XML)?.let { xmlSheets(it.decodeToString(), archive.read(STYLES_XML)?.decodeToString()) }
            ?: throw IllegalArgumentException("Not an XMind map: it has no $CONTENT_JSON or $CONTENT_XML")
        if (sheets.isEmpty()) throw IllegalArgumentException("The map has no sheets")
        return toTree(sheets, title)
    }

    private fun jsonSheets(content: String): List<Sheet> {
        val sheets = JsonParser(content).parse() as? List<*>
            ?: throw IllegalArgumentException("Not an XMind map: $CONTENT_JSON holds no sheets")
        return sheets.filterIsInstance<Map<*, *>>().mapNotNull { sheet ->
            val root = (sheet["rootTopic"] as? Map<*, *>)?.let(::jsonTopic) ?: return@mapNotNull null
            val relationships = (sheet["relationships"] as? List<*>)
                .orEmpty()
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { relationship ->
                    val from = relationship.string("end1Id") ?: return@mapNotNull null
                    val to = relationship.string("end2Id") ?: return@mapNotNull null
                    from to to
                }
            Sheet(sheet.string("title").orEmpty(), root, relationships)
        }
    }

    private fun jsonTopic(topic: Map<*, *>): Topic {
        val notes = topic["notes"] as? Map<*, *>
        val properties = (topic["style"] as? Map<*, *>)?.get("properties") as? Map<*, *>
        val childLists = (topic["children"] as? Map<*, *>)?.entries.orEmpty().sortedBy { it.key != ATTACHED }
        val children = childLists
            .flatMap { (it.value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>() }
            .map(::jsonTopic)
        val attachedCount = childLists
            .firstOrNull { it.key == ATTACHED }
            ?.let { (it.value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().size }
            ?: 0
        val boundaries = (topic["boundaries"] as? List<*>)
            .orEmpty()
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { boundaryRange(it.string("range")) }
        return Topic(
            id = topic.string("id")
            , title = topic.string("title").orEmpty()
            , noteHtml = (notes?.get("realHTML") as? Map<*, *>)?.string("content")
            , notePlain = (notes?.get("plain") as? Map<*, *>)?.string("content")
            , labels = (topic["labels"] as? List<*>).orEmpty().filterIsInstance<String>()
            , markers = (topic["markers"] as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.string("markerId") }
            , link = topic.string("href")
            , style = properties
                ?.entries
                .orEmpty()
                .mapNotNull { (key, value) -> (value as? String)?.let { key.toString() to it } }
                .toMap()
            , isFolded = topic["branch"] == "folded"
            , children = children
            , attachedCount = attachedCount
            , boundaries = boundaries.map { it.range }
        )
    }

    private class BoundaryRange(val range: IntRange?)

    // XMind writes the range of a boundary as "(first,last)", or "master" for one around the topic itself.
    private fun boundaryRange(range: String?): BoundaryRange? {
        val value = range?.trim() ?: return null
        if (value == MASTER) return BoundaryRange(null)
        val ends = value.removePrefix("(").removeSuffix(")").split(',').mapNotNull { it.trim().toIntOrNull() }
        if (ends.size != 2 || ends[0] < 0 || ends[1] < ends[0]) return null
        return BoundaryRange(ends[0]..ends[1])
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun xmlSheets(
        content: String
        , styles: String?
    ): List<Sheet> {
        val document = XmlParser(content).parse().firstOrNull { it.name == "xmap-content" }
            ?: throw IllegalArgumentException("Not an XMind map: no <xmap-content> element")
        val styleProperties = styles?.let(::xmlStyles).orEmpty()
        return document.children.filter { it.name == "sheet" }.mapNotNull { sheet ->
            val root = sheet.child("topic")?.let { xmlTopic(it, styleProperties) } ?: return@mapNotNull null
            val relationships = sheet.child("relationships")
                ?.children
                .orEmpty()
                .filter { it.name == "relationship" }
                .mapNotNull { relationship ->
                    val from = relationship.attributes["end1"] ?: return@mapNotNull null
                    val to = relationship.attributes["end2"] ?: return@mapNotNull null
                    from to to
                }
            Sheet(sheet.child("title")?.text.orEmpty(), root, relationships)
        }
    }

    private fun xmlTopic(
        topic: XmlElement
        , styles: Map<String, Map<String, String>>
    ): Topic {
        val childLists = topic.child("children")
            ?.children
            .orEmpty()
            .filter { it.name == "topics" }
            .sortedBy { it.attributes["type"] != ATTACHED }
        val children = childLists
            .flatMap { topics -> topics.children.filter { it.name == "topic" } }
            .map { xmlTopic(it, styles) }
        val attachedCount = childLists
            .firstOrNull { it.attributes["type"] == ATTACHED }
            ?.children
            ?.count { it.name == "topic" }
            ?: 0
        val boundaries = topic.child("boundaries")
            ?.children
            .orEmpty()
            .filter { it.name == "boundary" }
            .mapNotNull { boundaryRange(it.attributes["range"]) }
        return Topic(
            id = topic.attributes["id"]
            , title = topic.child("title")?.text.orEmpty()
            , noteHtml = null
            , notePlain = topic.child("notes")?.child("plain")?.text
            , labels = topic.child("labels")?.children.orEmpty().filter { it.name == "label" }.map { it.text }
            , markers = topic.child("marker-refs")?.children.orEmpty().mapNotNull { it.attributes["marker-id"] }
            , link = topic.attributes["xlink:href"]
            , style = topic.attributes["style-id"]?.let { styles[it] }.orEmpty()
            , isFolded = topic.attributes["branch"] == "folded"
            , children = children
            , attachedCount = attachedCount
            , boundaries = boundaries.map { it.range }
        )
    }

    // XMind 8 keeps the style of a topic in styles.xml, as the attributes of the <topic-properties> of a <style>.
    private fun xmlStyles(content: String): Map<String, Map<String, String>> {
        val styles = mutableMapOf<String, Map<String, String>>()
        fun collect(element: XmlElement) {
            val id = element.attributes["id"]
            if (element.name == "style" && id != null) {
                styles[id] = element.children.firstOrNull()?.attributes.orEmpty()
            }
            element.children.forEach(::collect)
        }
        XmlParser(content).parse().forEach(::collect)
        return styles
    }

    private fun XmlElement.child(name: String): XmlElement? = children.firstOrNull { it.name == name }

    private fun toTree(
        sheets: List<Sheet>
        , title: String
    ): Tree<Any> {
        val tree = Tree<Any>()
        val root = tree.getRootNode()
        // The ids of the topics in the file, to the ids of their nodes, for the relationships between them.
        val nodeIds = mutableMapOf<String, String>()
        val rootTopic = sheets.singleOrNull()?.root
        rootTopic?.id?.let { nodeIds[it] = root.id }
        val branches = rootTopic?.children
            ?: sheets.map { sheet -> sheet.root.takeIf { it.title.isNotBlank() } ?: sheet.root.copy(title = sheet.title) }
        // A node is only in the tree once the nodes above it are, so the boundaries are added once all nodes are.
        val boundaries = mutableListOf<Boundary>()
        val children = branches.map { addNode(tree, it, root.id, nodeIds, boundaries) }
        rootTopic?.let { boundaries.addAll(rangeBoundaries(it, root.id, children)) }
        tree.setRootNode(
            root.copy(
                children = children
                , description = rootTopic?.let(::description) ?: RichText.fromPlainText(title)
                , note = rootTopic?.let(::note).orEmpty()
                , color = rootTopic?.let { color(it.style["svg:fill"]) } ?: root.color
                , textColor = rootTopic?.let { color(it.style["fo:color"]) } ?: root.textColor
            )
        )
        rootTopic?.let { tree.setAttributes(root.id, attributes(it)) }
        sheets.flatMap { it.relationships }.forEach { (from, to) ->
            val sourceId = nodeIds[from] ?: return@forEach
            val targetId = nodeIds[to] ?: return@forEach
            if (sourceId != targetId) tree.addArrowLink(ArrowLink(IdGenerator.makeRandomNodeId(), sourceId, targetId))
        }
        boundaries.forEach { tree.addBoundary(it) }
        return tree
    }

    // The boundaries around ranges of the attached children of a topic, whose nodes are [childIds]. A range past
    // the attached children is cut short at the last of them.
    private fun rangeBoundaries(
        topic: Topic
        , nodeId: String
        , childIds: List<String>
    ): List<Boundary> {
        val attachedIds = childIds.take(topic.attachedCount)
        return topic.boundaries.filterNotNull().mapNotNull { range ->
            val ids = attachedIds.filterIndexed { index, _ -> index in range }.toSet()
            if (ids.isEmpty()) null else Boundary(IdGenerator.makeRandomNodeId(), nodeId, ids)
        }
    }

    private fun addNode(
        tree: Tree<Any>
        , topic: Topic
        , parentId: String
        , nodeIds: MutableMap<String, String>
        , boundaries: MutableList<Boundary>
    ): String {
        val id = IdGenerator.makeRandomNodeId()
        topic.id?.let { nodeIds[it] = id }
        val children = topic.children.map { addNode(tree, it, id, nodeIds, boundaries) }
        boundaries.addAll(rangeBoundaries(topic, id, children))
        if (null in topic.boundaries) boundaries.add(Boundary(IdGenerator.makeRandomNodeId(), parentId, setOf(id)))
        val shape = shape(topic.style["shape-class"]) ?: NodeShape.RECTANGLE
        val node = unplacedNode(
            shape
            , id
            , parentId
            , description(topic)
            , children
            , color(topic.style["svg:fill"]) ?: shape.defaultColor
            , color(topic.style["fo:color"]) ?: shape.defaultTextColor
            , note(topic)
        )
        tree.setNode(id, node)
        tree.setAttributes(id, attributes(topic))
        tree.setFolded(id, topic.isFolded)
        return id
    }

    // The first marker's emoji is the node's emoji; those of the other markers follow it, as text.
    private fun description(topic: Topic): String {
        var text = RichText.fromPlainText(topic.title.replace("\r\n", "\n").trim())
        if (topic.style["fo:font-weight"] == "bold") text = "<b>$text</b>"
        if (topic.style["fo:font-style"] == "italic") text = "<i>$text</i>"
        topic.link
            ?.takeIf { link -> LINK_SCHEMES.any { link.startsWith(it, ignoreCase = true) } }
            ?.let { text = "<a href=\"${escapeAttribute(it)}\">$text</a>" }
        val emojis = topic.markers.mapNotNull(::markerEmoji).distinct()
        // In a paragraph, as DescriptionEmoji keeps an emoji, so that it is not in a paragraph of its own.
        return if (emojis.isEmpty()) text else "<p>${(emojis + text).joinToString(" ")}</p>"
    }

    private fun markerEmoji(markerId: String): String? {
        PRIORITY.matchEntire(markerId)?.let { return it.groupValues[1] + "️⃣" }
        return MARKER_EMOJIS[markerId] ?: MARKER_KIND_EMOJIS[markerId.substringBefore('-')]
    }

    // The images of a note are in the map's archive, which a note cannot show them from.
    private fun note(topic: Topic): String = topic.noteHtml
        ?.replace(IMAGE, "")
        ?.takeIf { it.isNotBlank() }
        ?: topic.notePlain
            ?.replace("\r\n", "\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(RichText::fromPlainText)
        ?: ""

    private fun attributes(topic: Topic): List<NodeAttribute> = topic.labels
        .filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.let { listOf(NodeAttribute(LABELS_ATTRIBUTE, it.joinToString(", "))) }
        .orEmpty()

    private fun shape(shapeClass: String?): NodeShape? = when (shapeClass?.substringAfterLast('.')) {
        "rect", "roundedRect" -> NodeShape.RECTANGLE
        "ellipse" -> NodeShape.OVAL
        "circle" -> NodeShape.CIRCLE
        "ellipserect", "stadium" -> NodeShape.STADIUM
        "underline" -> NodeShape.LINE
        else -> null
    }

    // XMind writes colors as #RRGGBB, or #RRGGBBAA; a color that is fully transparent is none.
    private fun color(value: String?): Color? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.length == 8 && hex.endsWith("00")) return null
        val rgb = hex.take(6).toLongOrNull(16) ?: return null
        return Color(0xFF000000 or rgb)
    }

    private fun escapeAttribute(text: String): String = text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
