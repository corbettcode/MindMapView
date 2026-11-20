package com.corbettcode.mindmapview.library.data

import androidx.compose.ui.graphics.Color
import com.corbettcode.mindmapview.library.halfHeight
import com.corbettcode.mindmapview.library.model.AttributeDisplay
import com.corbettcode.mindmapview.library.model.GrowDirection
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.model.LevelStyle
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.model.MapTheme
import com.corbettcode.mindmapview.library.model.MapThemes
import com.corbettcode.mindmapview.library.model.NoteDisplay
import com.corbettcode.mindmapview.library.model.TopicKind
import com.corbettcode.mindmapview.library.util.DescriptionEmoji
import com.corbettcode.mindmapview.library.util.Dp
import com.corbettcode.mindmapview.library.util.ExceptionMessage
import com.corbettcode.mindmapview.library.util.NodeGenerator

/**
 * Mutable model of a mind-map style topic tree.
 *
 * A tree stores nodes addressed by their ids and the layout, presentation, and auxiliary
 * data attached to those nodes. It tracks the root topic, ordinary and floating root
 * children, branch placement, branch-specific structures, grid settings, folded nodes,
 * boundaries, summaries, arrow links, and per-node visual or semantic metadata.
 *
 * Layout-related operations determine how a node is placed relative to its parent, which
 * side of the root a branch belongs to, which structure owns a branch, and how summaries,
 * floating topics, and root children are grouped or ordered. Branches may inherit the
 * structure of an ancestor or define their own structure, while the root uses the tree's
 * map structure.
 *
 * Presentation-related operations expose and modify node attributes, labels, borders,
 * tasks, note display settings, attribute display settings, fill colors, topic styles,
 * and theme-derived defaults. Node-specific settings may override map-level or
 * topic-kind defaults, while unset values continue to follow the applicable inherited
 * behavior.
 *
 * The class maintains the consistency of tree-specific invariants such as root-child
 * ordering, attached versus floating root topics, branch side assignment, and structure
 * ownership. Methods that change layout or metadata may reject invalid operations, such
 * as assigning branch-only state to the root or floating a node that is not a child of
 * the root.
 */
class Tree<T> {
    private var _nodes: MutableMap<String, NodeData<*>>
    val nodes get() = _nodes.toMap()

    internal var isRootPlaced = true
        private set

    // A root that is given no position is kept in the center of the mind map, whatever size that takes.
    internal var isRootCentered = false
        private set

    internal var isRootDragged = false
        private set

    // The root has a set of children for each side; the side of each child of the root that is not on its right.
    private val rootChildSides = mutableMapOf<String, LayoutSide>()

    private val nodeAttributes = mutableMapOf<String, List<NodeAttribute>>()

    private val nodeLines = mutableMapOf<String, NodeLine>()
    private val nodeBorders = mutableMapOf<String, NodeBorder>()
    private val nodeTasks = mutableMapOf<String, NodeTask>()
    private val nodeNoteDisplays = mutableMapOf<String, NoteDisplay>()
    private val nodeAttributeDisplays = mutableMapOf<String, AttributeDisplay>()

    private val foldedIds = mutableSetOf<String>()

    // The rectangles drawn raised off the map, with a shadow below them.
    private val elevatedIds = mutableSetOf<String>()

    // The children of the root that float, each with how far its center is from the root's.
    private val floatingOffsets = mutableMapOf<String, DpOffset>()

    // The topics laid out in a structure of their own, rather than in their parent's, each with that structure.
    private val branchStructures = mutableMapOf<String, MapStructure>()

    // The topics a grid is laid out from, the root among them, each with how many columns its grid has.
    private val gridColumns = mutableMapOf<String, Int>()

    /** The look of the map, which the nodes follow where they have no colors or border of their own. */
    var theme: MapTheme = MapThemes.CLASSIC

    /** How the map is laid out: as a mind map, a logic chart, an org chart, and so on. */
    var structure: MapStructure = MapStructure.MIND_MAP

    /**
     * Returns the way the branch the node [id] is in grows: the way its children are placed from it, in the
     * structure they are laid out in, [structureOf].
     */
    fun directionOf(id: String): GrowDirection = structureOf(id).directionOf(this, id, structureOwnerOf(id))

    /**
     * Returns the way the node [id] is placed from its parent: the way its parent's branch grows, in the structure
     * its parent is laid out in. It is [directionOf] the node but for a topic with a structure of its own, whose
     * children may grow another way than it was placed. The root is placed from nothing, and its own is returned.
     */
    fun placedDirectionOf(id: String): GrowDirection {
        val parentId = getNode(id).parentId ?: return directionOf(id)
        return structureOf(parentId).directionOf(this, id, structureOwnerOf(parentId))
    }

    /**
     * Returns the structure set on the topic [id] for its branch, or null when its branch is laid out in its
     * parent's, as it is to start with. The root's is the map's [structure], not this.
     */
    fun branchStructureOf(id: String): MapStructure? = branchStructures[id]?.takeIf { id in _nodes }

    /**
     * Lays out the children of the topic [id], and the nodes below them, in [structure], or in its parent's when it
     * is null. The mind map is not one a branch can have, as it needs the root's six sides, and the root's
     * structure is the map's; it throws for either.
     */
    fun setBranchStructure(
        id: String
        , structure: MapStructure?
    ) {
        if (getNode(id).parentId == null || structure == MapStructure.MIND_MAP) {
            throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        }
        if (structure == null) branchStructures.remove(id) else branchStructures[id] = structure
    }

    /**
     * Returns the topic the structure the children of [id] are laid out in is set on: [id] itself, or the nearest
     * topic above it with a structure of its own, or else the root.
     */
    fun structureOwnerOf(id: String): String =
        generateSequence(id) { getNode(it).parentId }.firstOrNull { it in branchStructures } ?: ROOT_ID

    /**
     * Returns how many columns the grid laid out from the topic [id] has, or null for as many as fit in a row, which
     * is how a grid starts. It is kept whatever the structure, for when the topic's branch is a grid again.
     */
    fun gridColumnsOf(id: String): Int? = gridColumns[id]?.takeIf { id in _nodes }

    /** Gives the grid laid out from the topic [id] [count] columns, or as many as fit in a row when it is null. */
    fun setGridColumns(
        id: String
        , count: Int?
    ) {
        getNode(id)
        require(count == null || count >= 1) { "A grid has at least one column, not $count" }
        if (count == null) gridColumns.remove(id) else gridColumns[id] = count
    }

    /** Returns the structure the children of the node [id] are laid out in: its owner's, see [structureOwnerOf]. */
    fun structureOf(id: String): MapStructure = branchStructures[structureOwnerOf(id)] ?: structure

    /** Whether the node [id] and its siblings are stacked along x, in the structure their parent is laid out in. */
    internal fun isStackedAlongX(id: String): Boolean {
        val parentId = getNode(id).parentId ?: return false
        return structureOf(parentId).isStackedAlongX(this, id, structureOwnerOf(parentId))
    }

    /** Returns the way the summary [id] is past the siblings it is about, in the structure of their parent. */
    internal fun summaryDirectionOf(id: String): GrowDirection {
        val parentId = getNode(id).parentId ?: return directionOf(id)
        return structureOf(parentId).summaryDirectionOf(this, id, structureOwnerOf(parentId))
    }

    /**
     * How the app draws each kind of topic, in place of the theme's style for its level. A kind without one is
     * drawn as the theme draws it. It is the app's, not the map's, so it is not saved with the map nor undone.
     */
    var topicStyles: Map<TopicKind, LevelStyle> = emptyMap()

    /**
     * How the app shows the notes of each kind of topic where a node has no way of its own. Like [topicStyles], it
     * is the app's, not the map's.
     */
    var topicNoteDisplays: Map<TopicKind, NoteDisplay> = emptyMap()

    /**
     * How the app shows the attributes of each kind of topic where a node has no way of its own. Like
     * [topicStyles], it is the app's, not the map's.
     */
    var topicAttributeDisplays: Map<TopicKind, AttributeDisplay> = emptyMap()

    private val _arrowLinks = mutableListOf<ArrowLink>()

    /** The arrow links between the nodes of the tree, in the order they are drawn. */
    val arrowLinks: List<ArrowLink> get() = _arrowLinks.toList()

    private val _boundaries = mutableListOf<Boundary>()

    // The siblings each summary is about, by the id of the summary node, as they were when it was made one.
    private val _summaries = mutableMapOf<String, Set<String>>()

    // A boundary is read through the tree as it is now, so no change of the tree can leave one around nodes that
    // are not its parent's children.
    /**
     * The boundaries of the tree, in the order they are drawn, each with only those of its children that are
     * still its parent's on its side. Those with none left are not among them.
     */
    val boundaries: List<Boundary> get() = _boundaries.mapNotNull(::current)

    constructor() : this(Dp(0f), Dp(0f)) {
        isRootPlaced = false
        isRootCentered = true
    }

    constructor(
        rootCenterX: Dp
        , rootCenterY: Dp
    ) {
        _nodes = mutableMapOf()
        _nodes[ROOT_ID] =
            OvalNodeData(
                id = ROOT_ID
                , children = listOf()
                , color = ROOT_COLOR
                , description = DescriptionEmoji.prepend(ROOT_EMOJI, "root")
                , parentId = null
                , path =
                    OvalPath(
                        rootCenterX
                        , rootCenterY
                        , Dp(72.6f)
                        , Dp(48.4f)
                    )
                , textColor = Color.Black
            )
    }

    constructor(
        nodes: Map<String, NodeData<*>>
    ) {
        this._nodes = nodes.toMutableMap()
    }

    /**
     * Returns the side of the root the node is on: the side of the child of the root it is, or is below.
     * The root itself is on neither side, and [LayoutSide.RIGHT] is returned for it.
     */
    fun sideOf(id: String): LayoutSide = rootChildSides[branchOf(id).id] ?: LayoutSide.RIGHT

    /**
     * Returns how far the center of the floating topic [id] is from the root's, or null when it is no floating
     * topic. A floating topic is a child of the root that is placed where it was put, with no line to the root, as
     * XMind's floating topics and Freeplane's free nodes are; the nodes below it are laid out to its right.
     */
    fun floatingOffsetOf(id: String): DpOffset? = floatingOffsets[id]?.takeIf { _nodes[id]?.parentId == ROOT_ID }

    /** Whether the node is a floating topic, see [floatingOffsetOf]. */
    fun isFloating(id: String): Boolean = floatingOffsetOf(id) != null

    /**
     * Makes the child of the root [id] a floating topic at [offset] from the root's center, or an attached one again
     * when it is null. Floating topics come after the attached children of the root, so one that floats goes last,
     * and one that is attached again goes after the other attached ones. It throws for a node that is not a child of
     * the root.
     */
    fun setFloating(
        id: String
        , offset: DpOffset?
    ) {
        if (_nodes[id]?.parentId != ROOT_ID) {
            throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        }
        val wasFloating = isFloating(id)
        if (offset == null) floatingOffsets.remove(id) else floatingOffsets[id] = offset
        if (offset != null) rootChildSides.remove(id)
        if (wasFloating != (offset != null)) {
            val root = getRootNode()
            _nodes[ROOT_ID] = root.withChildren(root.children - id + id)
            orderRootChildren()
        }
    }

    /**
     * Returns what the child of the root [id] is grouped with for boundaries and summaries: the side of the root it
     * is on in a mind map, or all of the root's attached children in another structure, where they are in one row;
     * and, for a floating topic, itself alone, as it is on no side.
     */
    internal fun rootGroupOf(id: String): Any = when {
        isFloating(id) -> FloatingGroup(id)
        structure == MapStructure.MIND_MAP -> sideOf(id)
        else -> AttachedGroup
    }

    private data class FloatingGroup(val id: String)

    private data object AttachedGroup

    // The attached children of the root come first, then the floating topics, each in their order.
    private fun orderRootChildren() {
        val root = getRootNode()
        val (floating, attached) = root.children.partition { it in floatingOffsets }
        if (attached + floating != root.children) _nodes[ROOT_ID] = root.withChildren(attached + floating)
    }

    /**
     * Moves the branch the node is in, the child of the root it is or is below, to the other side of the
     * root, at the same height. The root itself is in no branch, and nothing is moved for it.
     */
    fun moveToOtherSide(id: String) {
        val branch = branchOf(id)
        if (branch.parentId == null) return
        setRootChildSide(branch.id, sideOf(branch.id).mirrored)
    }

    // The child of the root the node is, or is below; the root for the root itself.
    internal fun branchOf(id: String): NodeData<*> {
        var node = getNode(id)
        while (node.parentId != null && node.parentId != ROOT_ID) {
            node = getNode(node.parentId)
        }
        return node
    }

    /**
     * Moves a child of the root, and the nodes below it, to [side] of the root.
     */
    fun setRootChildSide(
        childId: String
        , side: LayoutSide
    ) {
        // A floating topic is on no side of the root.
        if (side != LayoutSide.RIGHT && _nodes[childId]?.parentId == ROOT_ID && childId !in floatingOffsets) {
            rootChildSides[childId] = side
        }
        else {
            rootChildSides.remove(childId)
        }
    }

    /** Returns the attributes of a node, in the order they are shown. */
    fun attributesOf(id: String): List<NodeAttribute> = nodeAttributes[id].orEmpty()

    fun setAttributes(
        id: String
        , attributes: List<NodeAttribute>
    ) {
        getNode(id)
        if (attributes.isEmpty()) nodeAttributes.remove(id) else nodeAttributes[id] = attributes.toList()
    }

    /**
     * Returns the node's label: the first of the comma-separated values of its [LABELS_ATTRIBUTE] attribute, as
     * XMind's labels are read, or null without one. A matrix puts a topic in the column of its label.
     */
    fun labelOf(id: String): String? = attributesOf(id)
        .firstOrNull { it.name == LABELS_ATTRIBUTE }
        ?.value
        ?.split(',')
        ?.first()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /**
     * Whether the node is a topic in a cell of a matrix: a child of one of its rows or columns, but a summary. Its
     * label is the header of its column, so the table of its attributes leaves it out.
     */
    fun isMatrixCell(id: String): Boolean {
        val groupId = getNode(id).parentId ?: return false
        val ownerId = getNode(groupId).parentId ?: return false
        val isOwner = structureOf(ownerId).isMatrix && structureOwnerOf(ownerId) == ownerId
        // A row or column with a structure of its own has its branch in its header, not in cells.
        return isOwner && !isSummary(id) && !isSummary(groupId) && !isFloating(groupId) &&
            branchStructureOf(groupId) == null
    }

    /**
     * Makes [label] the node's first label, keeping its others, or takes its labels away when it is null.
     */
    fun setLabel(
        id: String
        , label: String?
    ) {
        val attributes = attributesOf(id)
        val others = attributes.firstOrNull { it.name == LABELS_ATTRIBUTE }
            ?.value
            ?.split(',')
            ?.drop(1)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != label }
            .orEmpty()
        val labels = listOfNotNull(label) + if (label == null) emptyList() else others
        val attribute = labels
            .takeIf { it.isNotEmpty() }
            ?.let { NodeAttribute(LABELS_ATTRIBUTE, it.joinToString(", ")) }
        val index = attributes.indexOfFirst { it.name == LABELS_ATTRIBUTE }
        val changed = when {
            index < 0 -> attributes + listOfNotNull(attribute)
            attribute == null -> attributes.filterIndexed { i, _ -> i != index }
            else -> attributes.mapIndexed { i, it -> if (i == index) attribute else it }
        }
        setAttributes(id, changed)
    }

    /**
     * Returns whether the node [id] is drawn raised off the map, with a shadow below it. Only a rectangle is, but the
     * setting is kept whatever the shape, for when the node is a rectangle again.
     */
    fun isElevated(id: String): Boolean = id in elevatedIds

    fun setElevated(
        id: String
        , isElevated: Boolean
    ) {
        getNode(id)
        if (isElevated) elevatedIds.add(id) else elevatedIds.remove(id)
    }

    /** Returns what is drawn around a node: no border unless it was given one. */
    fun borderOf(id: String): NodeBorder = nodeBorders[id] ?: NodeBorder()

    fun setBorder(
        id: String
        , border: NodeBorder
    ) {
        getNode(id)
        if (border.isNone) nodeBorders.remove(id) else nodeBorders[id] = border
    }

    /** Returns what the node holds of a task, which is empty when it is no task. */
    fun taskOf(id: String): NodeTask = nodeTasks[id] ?: NodeTask()

    /** Gives the node what it holds of a task; an empty one makes it no task. */
    fun setTask(
        id: String
        , task: NodeTask
    ) {
        getNode(id)
        if (task.isEmpty) nodeTasks.remove(id) else nodeTasks[id] = task
    }

    /** Returns how the node's note is shown when it was given a way of its own, or null when it follows the map. */
    fun noteDisplayOf(id: String): NoteDisplay? = nodeNoteDisplays[id]

    /**
     * Returns how the node's note is shown: its own way, or that of its kind of topic, or else [default], the way
     * of the map.
     */
    fun shownNoteDisplayOf(
        id: String
        , default: NoteDisplay
    ): NoteDisplay = nodeNoteDisplays[id] ?: topicNoteDisplays[topicKindOf(id)] ?: default

    /** Gives the node its own way of showing its note, or with null makes it follow the map again. */
    fun setNoteDisplay(
        id: String
        , noteDisplay: NoteDisplay?
    ) {
        getNode(id)
        if (noteDisplay == null) nodeNoteDisplays.remove(id) else nodeNoteDisplays[id] = noteDisplay
    }

    /**
     * Returns how the node's attributes are shown when it was given a way of its own, or null when it follows
     * the map.
     */
    fun attributeDisplayOf(id: String): AttributeDisplay? = nodeAttributeDisplays[id]

    /**
     * Returns how the node's attributes are shown: its own way, or that of its kind of topic, or else [default],
     * the way of the map.
     */
    fun shownAttributeDisplayOf(
        id: String
        , default: AttributeDisplay
    ): AttributeDisplay = nodeAttributeDisplays[id] ?: topicAttributeDisplays[topicKindOf(id)] ?: default

    /** Gives the node its own way of showing its attributes, or with null makes it follow the map again. */
    fun setAttributeDisplay(
        id: String
        , attributeDisplay: AttributeDisplay?
    ) {
        getNode(id)
        if (attributeDisplay == null) nodeAttributeDisplays.remove(id) else nodeAttributeDisplays[id] = attributeDisplay
    }

    /**
     * Returns the fill a node is drawn in: its own color, or the theme's for its level when its color is still the
     * one its shape starts with.
     */
    fun fillOf(id: String): Color {
        val node = getNode(id)
        if (!isDefaultFill(node)) return node.color
        return levelStyleOf(id).fill ?: node.color
    }

    /**
     * Returns the color a node's text is drawn in: its own color, or the theme's when it is still the one its shape
     * starts with. A node given a fill of its own keeps its text color too, as the theme's is made to read on the
     * theme's fill. A line node has no fill, so its text takes the theme's color for text on the background.
     */
    fun textColorOf(id: String): Color {
        val node = getNode(id)
        if (node.textColor != defaultTextColorOf(node)) return node.textColor
        if (node !is LineNodeData && !isDefaultFill(node)) return node.textColor
        val themed = if (node is LineNodeData) theme.lineNodeText else levelStyleOf(id).text
        return themed ?: node.textColor
    }

    /**
     * Returns the border a node is drawn with: its own, or the theme's for its level when it has none. A line node
     * has no fill for a border to go around, so it only has one of its own.
     */
    fun drawnBorderOf(id: String): NodeBorder {
        val own = borderOf(id)
        if (!own.isNone || getNode(id) is LineNodeData) return own
        return levelStyleOf(id).border
    }

    /**
     * Returns the level of the node with [id]: 0 for the root, 1 for a child of the root, 2 for a child of one of
     * those, and so on, one more for each node it is below. It follows where the node is, so a node that is moved
     * is on the level of where it goes.
     */
    fun levelOf(id: String): Int = generateSequence(getNode(id).parentId) { getNode(it).parentId }.count()

    /** Returns what the node with [id] is by its level: the root topic, a topic or a subtopic. */
    fun topicKindOf(id: String): TopicKind = TopicKind.ofLevel(levelOf(id))

    /** Returns what a node added to the node with [parentId] is by its level: a topic or a subtopic. */
    fun childTopicKindOf(parentId: String): TopicKind = TopicKind.ofLevel(levelOf(parentId) + 1)

    /** Returns how the theme draws the nodes of the level a node is on: the root, a main topic or a subtopic. */
    fun levelStyleOf(id: String): LevelStyle = levelStyle(topicKindOf(id))

    // Maps saved before the colors a node starts with were changed still hold the earlier ones, which were
    // never chosen.
    private fun isDefaultFill(node: NodeData<*>): Boolean {
        val earlier = if (node.parentId == null) EARLIER_ROOT_COLORS else EARLIER_SHAPE_COLORS[node.shape].orEmpty()
        return node.color == defaultFillOf(node) || node.color in earlier
    }

    /** Returns how the theme draws a node added to the node with [parentId]. */
    fun childLevelStyleOf(parentId: String): LevelStyle = levelStyle(childTopicKindOf(parentId))

    private fun levelStyle(kind: TopicKind): LevelStyle = topicStyles[kind] ?: theme.levelStyleOf(kind)

    /** Returns the fill a node starts with, which follows the theme for as long as it is not changed. */
    fun defaultFillOf(node: NodeData<*>): Color =
        if (node.parentId == null) ROOT_COLOR else node.shape.defaultColor

    /** Returns the text color a node starts with, which follows the theme for as long as it is not changed. */
    fun defaultTextColorOf(node: NodeData<*>): Color =
        if (node.parentId == null) ROOT_TEXT_COLOR else node.shape.defaultTextColor

    /** Returns the line settings the node has of its own; [NodeLine] with nothing set when it has none. */
    fun lineOf(id: String): NodeLine = nodeLines[id] ?: NodeLine()

    /** Sets the node's own line settings. An empty line makes the node take them from its parent again. */
    fun setLine(
        id: String
        , line: NodeLine
    ) {
        getNode(id)
        if (line.isEmpty) nodeLines.remove(id) else nodeLines[id] = line
    }

    /** Whether the node is folded: the nodes below it are hidden, as in FreeMind. */
    fun isFolded(id: String): Boolean = id in foldedIds && id in _nodes

    /**
     * Folds or unfolds a node. Only a node with children is folded, and the root never is.
     */
    fun setFolded(
        id: String
        , isFolded: Boolean
    ) {
        val node = getNode(id)
        if (isFolded && node.parentId != null && node.children.isNotEmpty()) foldedIds.add(id) else foldedIds.remove(id)
    }

    /** Returns the children of the node that are shown: none of them when it is folded. */
    fun visibleChildren(node: NodeData<*>): List<String> = if (isFolded(node.id)) emptyList() else node.children

    /** Whether the node is shown: it is not when a node above it is folded. */
    fun isVisible(id: String): Boolean = id in _nodes && visibleAncestorOf(id) == id

    /**
     * Returns the node itself when it is shown, and otherwise the folded node above it that is: the one
     * it is hidden in.
     */
    fun visibleAncestorOf(id: String): String {
        var visibleId = id
        var parentId = getNode(id).parentId
        while (parentId != null) {
            if (isFolded(parentId)) visibleId = parentId
            parentId = getNode(parentId).parentId
        }
        return visibleId
    }

    /**
     * Adds an arrow link, in place of the one with its id when there is one. Its nodes have to be two
     * different nodes of the tree.
     */
    fun addArrowLink(link: ArrowLink) {
        if (link.sourceId == link.targetId) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        getNode(link.sourceId)
        getNode(link.targetId)
        val index = _arrowLinks.indexOfFirst { it.id == link.id }
        if (index == -1) _arrowLinks.add(link) else _arrowLinks[index] = link
    }

    fun removeArrowLink(id: String) {
        _arrowLinks.removeAll { it.id == id }
    }

    /**
     * Adds a boundary, in place of the one with its id when there is one. Its parent has to be a node of the
     * tree with at least one of its [Boundary.childIds] among its children. It is kept around all of the children
     * from the first to the last of them, so it stays around those between when one at either end goes.
     */
    fun addBoundary(boundary: Boundary) {
        val found = current(boundary)
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        val current = found.copy(childIds = boundaryChildIds(found).toSet())
        val index = _boundaries.indexOfFirst { it.id == boundary.id }
        if (index == -1) _boundaries.add(current) else _boundaries[index] = current
    }

    fun removeBoundary(id: String) {
        _boundaries.removeAll { it.id == id }
    }

    /**
     * Returns the children of the boundary's parent it is around, in order: from the first to the last of its
     * children, with those between them, on the side of the root its first child is on.
     */
    fun boundaryChildIds(boundary: Boundary): List<String> {
        val current = current(boundary) ?: return emptyList()
        val siblings = siblingsOnSide(current.parentId, current.childIds)
        val first = siblings.indexOfFirst { it in current.childIds }
        val last = siblings.indexOfLast { it in current.childIds }
        return siblings.subList(first, last + 1)
    }

    // The boundary with only the children that are still its parent's, on the side of its first one, or null when
    // none are.
    private fun current(boundary: Boundary): Boundary? {
        val parent = _nodes[boundary.parentId]?.takeIf { isInTree(it.id) } ?: return null
        val childIds = siblingsOnSide(parent.id, boundary.childIds).filter { it in boundary.childIds }.toSet()
        return when {
            childIds.isEmpty() -> null
            childIds == boundary.childIds -> boundary
            else -> boundary.copy(childIds = childIds)
        }
    }

    /**
     * Makes the node [summaryId] the summary of some of its siblings, [childIds], and of those between them: a node
     * drawn past them with a bracket to them, in place of a line from its parent. Under the root they are those on
     * the side of the summary. A summary is about no summary, nor about itself. It throws when it would be about
     * none of its siblings.
     */
    fun setSummary(
        summaryId: String
        , childIds: Set<String>
    ) {
        val current = currentSummary(summaryId, childIds)
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        _summaries[summaryId] = summaryRange(summaryId, current).toSet()
    }

    /** Makes the node an ordinary node again, when it is a summary. */
    fun removeSummary(summaryId: String) {
        _summaries.remove(summaryId)
    }

    // A summary is read through the tree as it is now, as a boundary is: one with none of its siblings left, or
    // moved under another node, is an ordinary node again.
    /** The summaries of the tree: the id of each summary node, with the siblings it is about, in order. */
    val summaries: Map<String, List<String>>
        get() = _summaries.keys.mapNotNull { id -> summaryChildIds(id).takeIf { it.isNotEmpty() }?.let { id to it } }
            .toMap()

    /** Whether the node is the summary of some of its siblings. */
    fun isSummary(id: String): Boolean = summaryChildIds(id).isNotEmpty()

    /**
     * Returns the siblings the summary is about, in order: from the first to the last of them, with those between
     * them, but no summaries. It is none for a node that is no summary.
     */
    fun summaryChildIds(summaryId: String): List<String> {
        val childIds = _summaries[summaryId] ?: return emptyList()
        val current = currentSummary(summaryId, childIds) ?: return emptyList()
        return summaryRange(summaryId, current)
    }

    // The siblings of the summary it is still about, on its side of the root, or null when none are.
    private fun currentSummary(
        summaryId: String
        , childIds: Set<String>
    ): Set<String>? {
        if (!isInTree(summaryId)) return null
        val siblings = summarySiblings(summaryId)
        return siblings.filter { it in childIds }.toSet().takeIf { it.isNotEmpty() }
    }

    private fun summaryRange(
        summaryId: String
        , childIds: Set<String>
    ): List<String> {
        val siblings = summarySiblings(summaryId)
        val first = siblings.indexOfFirst { it in childIds }
        val last = siblings.indexOfLast { it in childIds }
        return siblings.subList(first, last + 1)
    }

    // The siblings a summary may be about: those of its parent's children on its side of the root but itself and
    // the other summaries.
    private fun summarySiblings(summaryId: String): List<String> =
        siblingsOnSideOf(summaryId).filter { it != summaryId && !isFlaggedSummary(it) }

    // Whether the node was made a summary and is about one of its siblings that was not, which is all there is to
    // tell without going round and round: which of those are summaries depends on which of the others are.
    private fun isFlaggedSummary(id: String): Boolean {
        val childIds = _summaries[id] ?: return false
        return siblingsOnSideOf(id).any { it != id && it in childIds && it !in _summaries }
    }

    private fun siblingsOnSideOf(id: String): List<String> {
        val parentId = _nodes[id]?.parentId ?: return emptyList()
        val children = _nodes[parentId]?.children.orEmpty()
        return if (parentId == ROOT_ID) children.filter { rootGroupOf(it) == rootGroupOf(id) } else children
    }

    // A removed node stays among the nodes, but is no longer a child of its parent, or is below one that is not.
    private fun isInTree(id: String): Boolean {
        var node = _nodes[id] ?: return false
        while (true) {
            val parent = _nodes[node.parentId ?: return node.id == ROOT_ID] ?: return false
            if (node.id !in parent.children) return false
            node = parent
        }
    }

    // The children of the parent, or under the root only those on the side of the first of [childIds] there is.
    private fun siblingsOnSide(
        parentId: String
        , childIds: Set<String>
    ): List<String> {
        val children = _nodes[parentId]?.children.orEmpty()
        if (parentId != ROOT_ID) return children
        val group = children.firstOrNull { it in childIds }?.let(::rootGroupOf) ?: return emptyList()
        return children.filter { rootGroupOf(it) == group }
    }

    /**
     * Removes the arrow links from and to the node and the nodes below it.
     */
    fun removeArrowLinksOfBranch(id: String) {
        val ids = mutableSetOf<String>()
        doPreorderTraversal(getNode(id)) { node -> ids.add(node.id) }
        _arrowLinks.removeAll { it.sourceId in ids || it.targetId in ids }
    }

    // Nodes are not changed in place but replaced, so the snapshot can share them with the tree.
    internal fun snapshot(): TreeSnapshot = TreeSnapshot(
        nodes = _nodes.toMap()
        , rootChildSides = rootChildSides.toMap()
        , nodeAttributes = nodeAttributes.toMap()
        , nodeLines = nodeLines.toMap()
        , nodeBorders = nodeBorders.toMap()
        , nodeTasks = nodeTasks.toMap()
        , nodeNoteDisplays = nodeNoteDisplays.toMap()
        , nodeAttributeDisplays = nodeAttributeDisplays.toMap()
        , foldedIds = foldedIds.toSet()
        , elevatedIds = elevatedIds.toSet()
        , floatingOffsets = floatingOffsets.toMap()
        , branchStructures = branchStructures.toMap()
        , gridColumns = gridColumns.toMap()
        , arrowLinks = _arrowLinks.toList()
        , boundaries = boundaries
        , summaries = summaries.mapValues { it.value.toSet() }
        , theme = theme
        , structure = structure
        , isRootPlaced = isRootPlaced
        , isRootCentered = isRootCentered
        , isRootDragged = isRootDragged
    )

    internal fun restore(snapshot: TreeSnapshot) {
        _nodes = snapshot.nodes.toMutableMap()
        rootChildSides.clear()
        rootChildSides.putAll(snapshot.rootChildSides)
        nodeAttributes.clear()
        nodeAttributes.putAll(snapshot.nodeAttributes)
        nodeLines.clear()
        nodeLines.putAll(snapshot.nodeLines)
        nodeBorders.clear()
        nodeBorders.putAll(snapshot.nodeBorders)
        nodeTasks.clear()
        nodeTasks.putAll(snapshot.nodeTasks)
        nodeNoteDisplays.clear()
        nodeNoteDisplays.putAll(snapshot.nodeNoteDisplays)
        nodeAttributeDisplays.clear()
        nodeAttributeDisplays.putAll(snapshot.nodeAttributeDisplays)
        foldedIds.clear()
        foldedIds.addAll(snapshot.foldedIds)
        elevatedIds.clear()
        elevatedIds.addAll(snapshot.elevatedIds)
        floatingOffsets.clear()
        floatingOffsets.putAll(snapshot.floatingOffsets)
        branchStructures.clear()
        branchStructures.putAll(snapshot.branchStructures)
        gridColumns.clear()
        gridColumns.putAll(snapshot.gridColumns)
        _arrowLinks.clear()
        _arrowLinks.addAll(snapshot.arrowLinks)
        _boundaries.clear()
        _boundaries.addAll(snapshot.boundaries)
        _summaries.clear()
        _summaries.putAll(snapshot.summaries)
        theme = snapshot.theme
        structure = snapshot.structure
        isRootPlaced = snapshot.isRootPlaced
        isRootCentered = snapshot.isRootCentered
        isRootDragged = snapshot.isRootDragged
    }

    fun copy(nodes: Map<String, NodeData<T>>): Tree<T> {
        return Tree(nodes)
    }

    fun getRootNode(): OvalNodeData {
        _nodes[ROOT_ID]
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_NODE_NOT_EXIST.message)
        return _nodes[ROOT_ID] as OvalNodeData
    }

    fun setRootNode(root: NodeData<*>) {
        _nodes[ROOT_ID] = root
    }

    internal fun placeRoot(centerX: Dp, centerY: Dp) {
        val root = getRootNode()
        _nodes[ROOT_ID] = root.copy(path = root.path.copy(centerX = centerX, centerY = centerY))
        isRootPlaced = true
    }

    // A root the user puts somewhere is no longer kept in the center of the mind map, nor where the tree fits.
    internal fun moveRoot(centerX: Dp, centerY: Dp) {
        placeRoot(centerX, centerY)
        isRootCentered = false
        isRootDragged = true
    }

    fun setNode(
        id: String,
        nodeData: NodeData<*>,
    ) {
        _nodes[id] = nodeData
    }

    fun getNode(id: String?): NodeData<*> {
        return _nodes[id]
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
    }

    /**
     * Adds a node with the id [targetId] as a child of [parentId]. It goes at [index] among the parent's children,
     * counted over all of the root's sides, or after the last of them when [index] is null. An index past either
     * end puts it at that end.
     */
    fun addNode(
        targetId: String,
        parentId: String?,
        content: String,
        shape: NodeShape = NodeShape.RECTANGLE,
        color: Color = shape.defaultColor,
        textColor: Color = shape.defaultTextColor,
        side: LayoutSide = LayoutSide.RIGHT,
        note: String = "",
        index: Int? = null,
    ) {
        if (_nodes[targetId] != null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_DUPLICATED_NODE.message)
        if (parentId == null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message)
        val newNode: NodeData<*> =
            when (shape) {
                NodeShape.CIRCLE ->
                    NodeGenerator.makeNode(CircleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.RECTANGLE ->
                    NodeGenerator.makeNode(RectangleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.SQUARE ->
                    NodeGenerator.makeNode(SquareNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.LINE ->
                    NodeGenerator.makeNode(LineNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.STADIUM ->
                    NodeGenerator.makeNode(StadiumNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.OVAL ->
                    NodeGenerator.makeNode(OvalNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
            }
        val parentNode =
            _nodes[parentId] ?: throw IllegalArgumentException(
                ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message,
            )
        val newChildren = parentNode.children.toMutableList()
        newChildren.add(index?.coerceIn(0, newChildren.size) ?: newChildren.size, targetId)
        val newParent = when (parentNode) {
            is CircleNodeData -> parentNode.copy(children = newChildren)
            is RectangleNodeData -> parentNode.copy(children = newChildren)
            is SquareNodeData -> parentNode.copy(children = newChildren)
            is LineNodeData -> parentNode.copy(children = newChildren)
            is StadiumNodeData -> parentNode.copy(children = newChildren)
            is OvalNodeData -> parentNode.copy(children = newChildren)
        }
        _nodes[parentId] = newParent
        _nodes[targetId] = newNode
        // A folded node would hide the child it is given.
        foldedIds.remove(parentId)
        setRootChildSide(targetId, side)
        if (parentId == ROOT_ID) orderRootChildren()
    }

    fun attachNode(
        targetId: String,
        parentId: String?,
        side: LayoutSide = LayoutSide.RIGHT,
    ) {
        if (targetId == ROOT_ID || parentId == null) return
        val targetNode = nodes[targetId] ?: return

        val newTargetNode =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(parentId = parentId)
                is RectangleNodeData -> targetNode.copy(parentId = parentId)
                is SquareNodeData -> targetNode.copy(parentId = parentId)
                is LineNodeData -> targetNode.copy(parentId = parentId)
                is StadiumNodeData -> targetNode.copy(parentId = parentId)
                is OvalNodeData -> targetNode.copy(parentId = parentId)
            }
        val parentNode = nodes[parentId] ?: return
        val newNodes = parentNode.children.toMutableList()
        newNodes.add(targetId)
        val newParentNode =
            when (parentNode) {
                is CircleNodeData -> parentNode.copy(children = newNodes)
                is RectangleNodeData -> parentNode.copy(children = newNodes)
                is SquareNodeData -> parentNode.copy(children = newNodes)
                is LineNodeData -> parentNode.copy(children = newNodes)
                is StadiumNodeData -> parentNode.copy(children = newNodes)
                is OvalNodeData -> parentNode.copy(children = newNodes)
            }
        _nodes[targetId] = newTargetNode
        _nodes[parentId] = newParentNode
        foldedIds.remove(parentId)
        setRootChildSide(targetId, side)
    }

    /**
     * Moves the node [targetId], with the nodes below it, to [index] among the children of [parentId], counted
     * without the node itself, or after the last of them when [index] is null. An index past either end puts it at
     * that end. Under the root it goes on [side]. The new parent is unfolded so the node stays in view. It throws for
     * the root, a node or parent that is not in the tree, or a parent in the node's own branch.
     */
    fun moveNode(
        targetId: String
        , parentId: String
        , index: Int? = null
        , side: LayoutSide = LayoutSide.RIGHT
    ) {
        if (targetId == ROOT_ID) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        val target = _nodes[targetId]
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        if (_nodes[parentId] == null) {
            throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message)
        }
        val isOwnBranch = generateSequence(parentId) { _nodes[it]?.parentId }.any { it == targetId }
        if (isOwnBranch) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        val oldParent = getNode(target.parentId)
        val oldChildren = oldParent.children - targetId
        _nodes[oldParent.id] = oldParent.withChildren(oldChildren)
        val newParent = getNode(parentId)
        val newChildren = newParent.children.toMutableList()
        newChildren.add(index?.coerceIn(0, newChildren.size) ?: newChildren.size, targetId)
        _nodes[parentId] = newParent.withChildren(newChildren)
        _nodes[targetId] = getNode(targetId).withParent(parentId)
        if (oldChildren.isEmpty()) foldedIds.remove(oldParent.id)
        foldedIds.remove(parentId)
        if (parentId != ROOT_ID) floatingOffsets.remove(targetId)
        setRootChildSide(targetId, side)
        if (parentId == ROOT_ID) orderRootChildren()
    }

    fun removeNode(targetId: String) {
        if (targetId == ROOT_ID) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_CANT_REMOVE.message)
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        targetNode.parentId?.let { parentId ->
            val parentNode =
                _nodes[parentId] ?: throw IllegalArgumentException(
                    ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message,
                )
            val newChildren = parentNode.children.filter { id -> id != targetId }
            if (newChildren.isEmpty()) foldedIds.remove(parentId)
            val newParent =
                when (parentNode) {
                    is CircleNodeData -> parentNode.copy(children = newChildren)
                    is RectangleNodeData -> parentNode.copy(children = newChildren)
                    is SquareNodeData -> parentNode.copy(children = newChildren)
                    is LineNodeData -> parentNode.copy(children = newChildren)
                    is StadiumNodeData -> parentNode.copy(children = newChildren)
                    is OvalNodeData -> parentNode.copy(children = newChildren)
                }
            _nodes[parentId] = newParent
        } ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_CANT_REMOVE.message)
        rootChildSides.remove(targetId)
        floatingOffsets.remove(targetId)
        branchStructures.remove(targetId)
        gridColumns.remove(targetId)
        elevatedIds.remove(targetId)
    }

    /**
     * Moves [targetId] among the children of its parent that are on its side, to the place its center at
     * ([centerX], [centerY]) falls in when they are stacked across the way their branch grows: top down for one
     * growing left or right, left to right for one growing down or up. Returns whether the order changed.
     */
    internal fun moveAmongSiblings(
        targetId: String
        , centerX: Dp
        , centerY: Dp
    ): Boolean {
        val isAcrossX = isStackedAlongX(targetId)
        val center = if (isAcrossX) centerX else centerY
        fun crossOf(node: NodeData<*>) = if (isAcrossX) node.path.centerX else node.path.centerY
        val parent = _nodes[_nodes[targetId]?.parentId] ?: return false
        val side = sideOf(targetId)
        val others = parent.children - targetId
        val sameSide = others.filter { sideOf(it) == side }
        if (sameSide.isEmpty()) return false
        // A grid's main topics are in reading order: a row of cells down, then along it.
        val isReadingOrder = structureOf(parent.id) == MapStructure.GRID && structureOwnerOf(parent.id) == parent.id
        fun isAfter(node: NodeData<*>): Boolean {
            if (!isReadingOrder) return crossOf(node).dpVal > center.dpVal
            val dy = node.path.centerY.dpVal - centerY.dpVal
            val rowHalf = halfHeight(node).dpVal
            return dy > rowHalf || (dy >= -rowHalf && node.path.centerX.dpVal > centerX.dpVal)
        }
        val below = sameSide.firstOrNull { isAfter(getNode(it)) }
        val index = if (below != null) others.indexOf(below) else others.indexOf(sameSide.last()) + 1
        val newChildren = others.toMutableList().apply { add(index, targetId) }
        if (newChildren == parent.children) return false
        _nodes[parent.id] = when (parent) {
            is CircleNodeData -> parent.copy(children = newChildren)
            is RectangleNodeData -> parent.copy(children = newChildren)
            is SquareNodeData -> parent.copy(children = newChildren)
            is LineNodeData -> parent.copy(children = newChildren)
            is StadiumNodeData -> parent.copy(children = newChildren)
            is OvalNodeData -> parent.copy(children = newChildren)
        }
        return true
    }

    fun updateNode(
        targetId: String,
        description: String,
        children: List<String> = emptyList(),
        dx: Dp = Dp(0f),
        dy: Dp = Dp(0f),
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        val newTargetNode =
            when (targetNode) {
                is CircleNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is RectangleNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is SquareNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is LineNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is StadiumNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is OvalNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
            }
        _nodes[targetId] = newTargetNode
    }

    fun updateNodeColor(
        targetId: String,
        color: Color,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(color = color)
                is RectangleNodeData -> targetNode.copy(color = color)
                is SquareNodeData -> targetNode.copy(color = color)
                is LineNodeData -> targetNode.copy(color = color)
                is StadiumNodeData -> targetNode.copy(color = color)
                is OvalNodeData -> targetNode.copy(color = color)
            }
    }

    /**
     * Gives the node another shape and keeps the rest of it. Only where the node is stays of its path: its
     * size is that of a new node, until it is measured for its text again. The root stays the oval it is.
     */
    fun updateNodeShape(
        targetId: String,
        shape: NodeShape,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        val parentId = targetNode.parentId
        if (parentId == null || targetNode.shape == shape) return
        val newNode: NodeData<*> =
            when (shape) {
                NodeShape.CIRCLE ->
                    NodeGenerator.makeNode(CircleNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
                NodeShape.RECTANGLE ->
                    NodeGenerator.makeNode(RectangleNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
                NodeShape.SQUARE ->
                    NodeGenerator.makeNode(SquareNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
                NodeShape.LINE ->
                    NodeGenerator.makeNode(LineNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
                NodeShape.STADIUM ->
                    NodeGenerator.makeNode(StadiumNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
                NodeShape.OVAL ->
                    NodeGenerator.makeNode(OvalNodeData::class, targetNode.description, parentId).let {
                        it.copy(
                            id = targetId,
                            alpha = targetNode.alpha,
                            children = targetNode.children,
                            color = targetNode.color,
                            note = targetNode.note,
                            path = it.path.copy(centerX = targetNode.path.centerX, centerY = targetNode.path.centerY),
                            strokeWidth = targetNode.strokeWidth,
                            textColor = targetNode.textColor,
                        )
                    }
            }
        _nodes[targetId] = newNode
    }

    fun updateNodeNote(
        targetId: String,
        note: String,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(note = note)
                is RectangleNodeData -> targetNode.copy(note = note)
                is SquareNodeData -> targetNode.copy(note = note)
                is LineNodeData -> targetNode.copy(note = note)
                is StadiumNodeData -> targetNode.copy(note = note)
                is OvalNodeData -> targetNode.copy(note = note)
            }
    }

    fun updateNodeTextColor(
        targetId: String,
        textColor: Color,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(textColor = textColor)
                is RectangleNodeData -> targetNode.copy(textColor = textColor)
                is SquareNodeData -> targetNode.copy(textColor = textColor)
                is LineNodeData -> targetNode.copy(textColor = textColor)
                is StadiumNodeData -> targetNode.copy(textColor = textColor)
                is OvalNodeData -> targetNode.copy(textColor = textColor)
            }
    }

    fun doPreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        action: (nodeData: NodeData<*>) -> Unit,
    ) {
        action.invoke(nodeData)
        nodeData.children.forEach { childId ->
            doPreorderTraversal(getNode(childId), action)
        }
    }

    fun doPreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        depth: Int = 0,
        action: (nodeData: NodeData<*>, depth: Int) -> Unit,
    ) {
        action.invoke(nodeData, depth)
        nodeData.children.forEach { childId ->
            doPreorderTraversal(getNode(childId), depth + 1, action)
        }
    }

    /** Calls [action] for the nodes that are shown: those that are not below a folded node. */
    fun doVisiblePreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        action: (nodeData: NodeData<*>) -> Unit,
    ) {
        action.invoke(nodeData)
        visibleChildren(nodeData).forEach { childId ->
            doVisiblePreorderTraversal(getNode(childId), action)
        }
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val ROOT_EMOJI = "🌱"

        internal val ROOT_COLOR = Color(0xFF0C9EB6)
        internal val ROOT_TEXT_COLOR = NodeShape.OVAL.defaultTextColor

        // The colors the root and the shapes started with in earlier versions of the app.
        private val EARLIER_ROOT_COLORS = setOf(
            Color(0xFFB8B8B8)
            , Color(0xFFCCCCCC)
            , Color(0xFF336770)
            , Color(0xFFB691BF)
        )
        private val EARLIER_SHAPE_COLORS = mapOf(
            NodeShape.RECTANGLE to setOf(Color(0xFFFFCF96))
            , NodeShape.CIRCLE to setOf(Color(0xFFE1719F))
        )
    }
}
