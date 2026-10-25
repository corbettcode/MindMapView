package com.mindsync.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx
import kotlin.math.sqrt

private const val CURVE_SEGMENTS = 32
private const val DEFAULT_INCLINATION_RATIO = 0.5f
private val MIN_DEFAULT_INCLINATION = Dp(40f)
private val MAX_DEFAULT_INCLINATION = Dp(200f)
private const val EDGE_OFFSET_RATIO = 0.6f

/**
 * The cubic curve an arrow link is drawn as, in the pixels of the tree.
 */
internal class ArrowLinkCurve(
    val start: Offset
    , val startControl: Offset
    , val endControl: Offset
    , val end: Offset
) {
    fun pointAt(t: Float): Offset {
        val u = 1 - t
        return start * (u * u * u) + startControl * (3 * u * u * t) + endControl * (3 * u * t * t) + end * (t * t * t)
    }

    fun distanceTo(point: Offset): Float {
        var nearest = Float.MAX_VALUE
        var from = start
        for (segment in 1..CURVE_SEGMENTS) {
            val to = pointAt(segment / CURVE_SEGMENTS.toFloat())
            nearest = minOf(nearest, distanceToSegment(point, from, to))
            from = to
        }
        return nearest
    }

    /** Where along the curve, from 0 at its start to 1 at its end, the point nearest to [point] is. */
    fun nearestT(point: Offset): Float {
        var nearestT = 0f
        var nearest = Float.MAX_VALUE
        for (step in 0..CURVE_SEGMENTS) {
            val t = step / CURVE_SEGMENTS.toFloat()
            val distance = (pointAt(t) - point).getDistanceSquared()
            if (distance < nearest) {
                nearest = distance
                nearestT = t
            }
        }
        return nearestT
    }

    private fun distanceToSegment(
        point: Offset
        , from: Offset
        , to: Offset
    ): Float {
        val segment = to - from
        val lengthSquared = segment.getDistanceSquared()
        if (lengthSquared == 0f) return (point - from).getDistance()
        val along = ((point.x - from.x) * segment.x + (point.y - from.y) * segment.y) / lengthSquared
        return (point - (from + segment * along.coerceIn(0f, 1f))).getDistance()
    }
}

/**
 * Returns the curve of [link], or null when one of its nodes is not in [tree] or is hidden in a folded node.
 */
internal fun Density.arrowLinkCurve(
    tree: Tree<*>
    , link: ArrowLink
): ArrowLinkCurve? {
    val source = tree.nodes[link.sourceId] ?: return null
    val target = tree.nodes[link.targetId] ?: return null
    if (!tree.isVisible(source.id) || !tree.isVisible(target.id)) return null
    val isStartOnRight = isOnRightEdge(tree, source, target, link.startInclination)
    val isEndOnRight = isOnRightEdge(tree, target, source, link.endInclination)
    val start = edgePoint(source, target, isStartOnRight)
    val end = edgePoint(target, source, isEndOnRight)

    // An end that is given no inclination leaves its node sideways, further the further apart the ends are.
    val defaultLength = ((end - start).getDistance() * DEFAULT_INCLINATION_RATIO)
        .coerceIn(MIN_DEFAULT_INCLINATION.toPx(this), MAX_DEFAULT_INCLINATION.toPx(this))
    fun inclination(given: ArrowLinkInclination?, isOnRight: Boolean): Offset {
        return given?.let { Offset(it.x.toPx(this), it.y.toPx(this)) }
            ?: Offset(if (isOnRight) defaultLength else -defaultLength, 0f)
    }
    return ArrowLinkCurve(
        start = start
        , startControl = start + inclination(link.startInclination, isStartOnRight)
        , endControl = end + inclination(link.endInclination, isEndOnRight)
        , end = end
    )
}

// The two ends face each other when one node is wholly to the side of the other. Nodes that are above
// one another have both ends on the edge their own lines to their children start at, so the curve goes
// around them; for the root, which has such an edge on either side, that is the right one.
private fun isOnRightEdge(
    tree: Tree<*>
    , node: NodeData<*>
    , other: NodeData<*>
    , inclination: ArrowLinkInclination?
): Boolean {
    if (inclination != null) return inclination.x.dpVal >= 0f
    return when {
        (other.path.centerX - halfWidth(other)).dpVal >= (node.path.centerX + halfWidth(node)).dpVal -> true
        (other.path.centerX + halfWidth(other)).dpVal <= (node.path.centerX - halfWidth(node)).dpVal -> false
        else -> !tree.sideOf(if (node.parentId == null) other.id else node.id).isLeft
    }
}

// The end is off the middle of the edge, towards the other node: the middle is where the node's lines
// and its add button are.
private fun Density.edgePoint(
    node: NodeData<*>
    , other: NodeData<*>
    , isRightEdge: Boolean
): Offset {
    val apart = (other.path.centerY - node.path.centerY).dpVal
    val offsetY = when {
        apart > 0f -> halfHeight(node) * EDGE_OFFSET_RATIO
        apart < 0f -> halfHeight(node) * -EDGE_OFFSET_RATIO
        else -> Dp(0f)
    }
    val offsetX = edgeDistance(node, offsetY)
    val x = if (isRightEdge) node.path.centerX + offsetX else node.path.centerX - offsetX
    return Offset(x.toPx(this), (node.path.centerY + offsetY).toPx(this))
}

// How far from the center of the node its edge is, on the horizontal line [offsetY] below the center.
private fun edgeDistance(
    node: NodeData<*>
    , offsetY: Dp
): Dp {
    return when (node) {
        is CircleNodeData -> chord(node.path.radius, offsetY)
        is RectangleNodeData -> node.path.width / 2
        is SquareNodeData -> node.path.width / 2
        is StadiumNodeData -> node.path.length / 2 + chord(node.path.radius, offsetY)
        is OvalNodeData -> node.path.radiusX * (chord(node.path.radiusY, offsetY).dpVal / node.path.radiusY.dpVal)
    }
}

// Half the width of a circle on the horizontal line [offsetY] from its center.
private fun chord(
    radius: Dp
    , offsetY: Dp
): Dp {
    return Dp(sqrt(maxOf(0f, radius.dpVal * radius.dpVal - offsetY.dpVal * offsetY.dpVal)))
}

internal fun halfWidth(node: NodeData<*>): Dp {
    return when (node) {
        is CircleNodeData -> node.path.radius
        is RectangleNodeData -> node.path.width / 2
        is SquareNodeData -> node.path.width / 2
        is StadiumNodeData -> node.path.width() / 2
        is OvalNodeData -> node.path.width() / 2
    }
}

internal fun halfHeight(node: NodeData<*>): Dp {
    return when (node) {
        is CircleNodeData -> node.path.radius
        is RectangleNodeData -> node.path.height / 2
        is SquareNodeData -> node.path.height / 2
        is StadiumNodeData -> node.path.height() / 2
        is OvalNodeData -> node.path.height() / 2
    }
}
