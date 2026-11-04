package com.mindsync.library.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.NodeShape
import com.mindsync.library.util.Dp

/**
 * How the nodes of one level of a map look when they have no colors or border of their own. A null [fill] or
 * [text] leaves the node in the color it has, as the Classic theme does.
 */
data class LevelStyle(
    val fill: Color?
    , val text: Color?
    , val border: NodeBorder = NodeBorder()
)

/**
 * How the attributes of a node are shown: as an icon, as an icon that shows them while the mouse is over it, or as
 * a table inside the node.
 */
enum class AttributeDisplay(
    val label: String
) {
    HIDDEN("Hidden")
    , FLYOVER("Flyover")
    , INLINE("Inline")
}

/**
 * A look for a whole map: its [background], how its [root], main topics (the children of the root) and subtopics
 * (the nodes below them) look, the color of the text of a line node, which is drawn on the background, the lines
 * from the root in [branchColors] one after another, how wide lines are, and the font of the text, by the name of a
 * bundled font, or null for the font the app is set to, and how attributes are shown, or null for how the app is
 * set to show them, the colors offered for an arrow link, the shape of a new node and how notes are shown, each
 * null for what the app is set to. Colors, borders and lines a node is given by hand are drawn as they are,
 * whatever the theme.
 *
 * Represents the styling and theme configuration for a map, including visual and structural properties.
 *
 * @property id A unique identifier for the map theme.
 * @property arrowLinkColors The colors offered for an arrow link in this theme, or null for those the app offers.
 * @property attributeDisplay How the attributes of nodes are shown in this theme, or null for how the app is set to.
 * @property background The background color of the map.
 * @property branchColors A list of colors used to differentiate branches originating from the root.
 * @property fontName The name of the font to be used for text in this theme, if specified.
 * @property isDark Indicates whether the theme uses a dark background, suitable for light-colored content.
 * @property lineColor The color of the lines connecting nodes in the map.
 * @property lineNodeText The color of the text that appears on the connecting lines, if any.
 * @property lineWidth The width of the lines connecting nodes, measured in density-independent pixels (Dp).
 * @property mainTopic The visual style applied to the main topics of the map.
 * @property name The name of the map theme.
 * @property newNodeShape The shape of a topic added to a parent without children in this theme, or null for the
 * one the app is set to.
 * @property noteDisplay How the notes of nodes are shown in this theme, or null for how the app is set to.
 * @property root The visual style applied to the root level of the map.
 * @property subtopic The visual style applied to the subtopics of the map.
 * @property subtopicShape The shape of a subtopic added to a parent without children in this theme, or null for
 * [newNodeShape].
 */
data class MapTheme(
    val id: String
    , val arrowLinkColors: List<Color>? = null
    , val attributeDisplay: AttributeDisplay? = null
    , val background: Color
    , val branchColors: List<Color>
    , val fontName: String?
    , val lineColor: Color
    , val lineNodeText: Color?
    , val lineWidth: Dp
    , val mainTopic: LevelStyle
    , val name: String
    , val newNodeShape: NodeShape? = null
    , val noteDisplay: NoteDisplay? = null
    , val root: LevelStyle
    , val subtopic: LevelStyle
    , val subtopicShape: NodeShape? = null
) {
    /** Whether the background is dark, for what is drawn on it to be light. */
    val isDark: Boolean get() = isDarkBackground(background)

    /** Returns the shape this theme adds a node of [kind] in, or null for the one the app is set to. */
    fun newNodeShapeOf(
        kind: TopicKind
    ): NodeShape? = when (kind) {
        TopicKind.ROOT_TOPIC -> null
        TopicKind.TOPIC -> newNodeShape
        TopicKind.SUBTOPIC -> subtopicShape ?: newNodeShape
    }

    /** Returns how this theme draws a node of [kind]. Its main topics are the topics, on level 1. */
    fun levelStyleOf(
        kind: TopicKind
    ): LevelStyle = when (kind) {
        TopicKind.ROOT_TOPIC -> root
        TopicKind.TOPIC -> mainTopic
        TopicKind.SUBTOPIC -> subtopic
    }
}

/** The themes that come with the library, [CLASSIC] first. */
object MapThemes {
    val CLASSIC = MapTheme(
        id = "classic"
        , background = Color.White
        , branchColors = DrawInfo().rootLineColors
        , fontName = null
        , lineColor = Color.Black
        , lineNodeText = null
        , lineWidth = Dp(5f)
        , mainTopic = LevelStyle(fill = null, text = null)
        , name = "Classic"
        , root = LevelStyle(fill = null, text = null)
        , subtopic = LevelStyle(fill = null, text = null)
    )

    private val OCEAN = MapTheme(
        id = "ocean"
        , name = "Ocean"
        , background = Color(0xFFF2F8FB)
        , root = LevelStyle(fill = Color(0xFF0B4F6C), text = Color.White)
        , mainTopic = LevelStyle(fill = Color(0xFF20A4C3), text = Color.Black)
        , subtopic = LevelStyle(fill = Color(0xFFD6EEF5), text = Color(0xFF0B4F6C))
        , lineNodeText = Color(0xFF0B4F6C)
        , lineColor = Color(0xFF0B4F6C)
        , branchColors = listOf(Color(0xFF0B4F6C), Color(0xFF20A4C3), Color(0xFF01BAEF), Color(0xFF3D7EA6))
        , arrowLinkColors = listOf(
            Color(0xFF0B4F6C), Color(0xFF1B7F9A), Color(0xFF3D7EA6), Color(0xFFD1495B), Color(0xFF5C5C5C)
        )
        , lineWidth = Dp(5f)
        , fontName = null
        , newNodeShape = NodeShape.STADIUM
        , noteDisplay = NoteDisplay.FLYOVER
    )

    private val NIGHT = MapTheme(
        id = "night"
        , name = "Night"
        , background = Color(0xFF1E1F26)
        , root = LevelStyle(fill = Color(0xFFF2C14E), text = Color(0xFF1E1F26))
        , mainTopic = LevelStyle(fill = Color(0xFF3A3D4D), text = Color(0xFFF5F5F5))
        , subtopic = LevelStyle(fill = Color(0xFF2A2C36), text = Color(0xFFC9CCD6))
        , lineNodeText = Color(0xFFF5F5F5)
        , lineColor = Color(0xFF9AA0B4)
        , branchColors = listOf(Color(0xFFF2C14E), Color(0xFFF78154), Color(0xFF4D9078), Color(0xFF5FAD9E))
        , arrowLinkColors = listOf(
            Color(0xFFF2C14E), Color(0xFFF78154), Color(0xFF5FAD9E), Color(0xFF9AA0B4), Color(0xFFC9A0DC)
        )
        , lineWidth = Dp(5f)
        , fontName = null
        , newNodeShape = NodeShape.RECTANGLE
        , noteDisplay = NoteDisplay.FLYOVER
    )

    private val INK = Color(0xFF3B3B3B)
    private val CREAM = Color(0xFFFBF8F1)

    private val PAPER = MapTheme(
        id = "paper"
        , name = "Paper"
        , background = CREAM
        , root = LevelStyle(CREAM, INK, NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.FOUR, INK))
        , mainTopic = LevelStyle(CREAM, INK, NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.TWO, INK))
        , subtopic = LevelStyle(CREAM, INK, NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.ONE, INK))
        , lineNodeText = INK
        , lineColor = INK
        , branchColors = listOf(INK)
        , arrowLinkColors = listOf(INK, Color(0xFFA23E48), Color(0xFF2F5D8A), Color(0xFF5E7F3E), Color(0xFF8A5A2B))
        , lineWidth = Dp(2f)
        , fontName = "Lora"
        , newNodeShape = NodeShape.RECTANGLE
        , noteDisplay = NoteDisplay.INLINE
    )

    private val MEADOW = MapTheme(
        id = "meadow"
        , name = "Meadow"
        , background = Color(0xFFF6FBF0)
        , root = LevelStyle(fill = Color(0xFF2E6B30), text = Color.White)
        , mainTopic = LevelStyle(fill = Color(0xFFF4D35E), text = Color(0xFF1F3A1F))
        , subtopic = LevelStyle(fill = Color(0xFFDCEFD0), text = Color(0xFF1F3A1F))
        , lineNodeText = Color(0xFF2E6B30)
        , lineColor = Color(0xFF2E6B30)
        , branchColors = listOf(
            Color(0xFF2E6B30), Color(0xFFF4A259), Color(0xFF5B8E7D), Color(0xFFBC4B51), Color(0xFF8CB369)
        )
        , arrowLinkColors = listOf(
            Color(0xFF2E6B30), Color(0xFFBC4B51), Color(0xFF4E7D6D), Color(0xFFB5651D), Color(0xFF4A5899)
        )
        , lineWidth = Dp(5f)
        , fontName = "Nunito"
        , newNodeShape = NodeShape.OVAL
        , noteDisplay = NoteDisplay.FLYOVER
    )

    private val HIGH_CONTRAST = MapTheme(
        id = "contrast"
        , name = "High Contrast"
        , background = Color.Black
        , root = LevelStyle(fill = Color.White, text = Color.Black)
        , mainTopic = LevelStyle(
            Color.Black, Color.White, NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.TWO, Color.White)
        )
        , subtopic = LevelStyle(fill = Color.Black, text = Color.White)
        , lineNodeText = Color.White
        , lineColor = Color.White
        , branchColors = listOf(Color(0xFFFFD400), Color(0xFF00E5FF), Color(0xFFFF4FD8), Color(0xFF7CFF4F))
        , arrowLinkColors = listOf(
            Color(0xFFFFD400), Color(0xFF00E5FF), Color(0xFFFF4FD8), Color(0xFF7CFF4F), Color.White
        )
        , lineWidth = Dp(4f)
        , fontName = "Inter"
        , newNodeShape = NodeShape.RECTANGLE
        , subtopicShape = NodeShape.LINE
        , noteDisplay = NoteDisplay.INLINE
    )

    val all: List<MapTheme> = listOf(CLASSIC, OCEAN, NIGHT, PAPER, MEADOW, HIGH_CONTRAST)

    /** Returns the theme with [id], or [CLASSIC] when there is none, as for a map saved before themes. */
    fun byId(id: String?): MapTheme = all.firstOrNull { it.id == id } ?: CLASSIC
}

/** How far apart two colors are to the eye, by WCAG: 1 for the same, 21 for black on white. */
internal fun contrastRatio(
    first: Color
    , second: Color
): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Whether [background] is dark, for what is drawn on it to be light. */
fun isDarkBackground(
    background: Color
): Boolean = background.luminance() < 0.5f

/**
 * The color of the halo around the node a dragged node would be attached to: a shade on a light background, and a
 * light one on a dark background, where a shade would not show.
 */
internal fun attachHighlightColor(
    background: Color
): Color = if (isDarkBackground(background)) {
    Color.White.copy(alpha = 60 / 255f)
} else {
    Color.Black.copy(alpha = 50 / 255f)
}
