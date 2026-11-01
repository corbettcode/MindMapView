package com.mindsync.library.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeLineWidth
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
 * A look for a whole map: its [background], how its [root], main topics (the children of the root) and subtopics
 * (the nodes below them) look, the color of the text of a line node, which is drawn on the background, the lines
 * from the root in [branchColors] one after another, how wide lines are, and the font of the text, by the name of a
 * bundled font, or null for the font the app is set to. Colors, borders and lines a node is given by hand are drawn
 * as they are, whatever the theme.
 */
data class MapTheme(
    val id: String
    , val name: String
    , val background: Color
    , val root: LevelStyle
    , val mainTopic: LevelStyle
    , val subtopic: LevelStyle
    , val lineNodeText: Color?
    , val lineColor: Color
    , val branchColors: List<Color>
    , val lineWidth: Dp
    , val fontName: String?
) {
    /** Whether the background is dark, for what is drawn on it to be light. */
    val isDark: Boolean get() = background.luminance() < 0.5f
}

/** The themes that come with the library, [CLASSIC] first. */
object MapThemes {
    val CLASSIC = MapTheme(
        id = "classic"
        , name = "Classic"
        , background = Color.White
        , root = LevelStyle(fill = null, text = null)
        , mainTopic = LevelStyle(fill = null, text = null)
        , subtopic = LevelStyle(fill = null, text = null)
        , lineNodeText = null
        , lineColor = Color.Black
        , branchColors = DrawInfo().rootLineColors
        , lineWidth = Dp(5f)
        , fontName = null
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
        , lineWidth = Dp(5f)
        , fontName = null
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
        , lineWidth = Dp(5f)
        , fontName = null
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
        , lineWidth = Dp(2f)
        , fontName = "Lora"
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
        , lineWidth = Dp(5f)
        , fontName = "Nunito"
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
        , lineWidth = Dp(4f)
        , fontName = "Inter"
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
