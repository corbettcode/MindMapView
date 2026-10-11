package com.mindsync.library

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree


class MindMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {
    private val state = MindMapState()
    private var fontFamily: FontFamily? = null
    private var nodeClickListener: NodeClickListener? = null
    private var nodeDoubleClickListener: NodeDoubleClickListener? = null
    private var nodeMoveListener: NodeMoveListener? = null

    init {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.NodeFont, defStyle, 0)
        val fontId = typedArray.getResourceId(R.styleable.NodeFont_nodeFontFamily, -1)
        if (fontId != -1) {
            fontFamily = FontFamily(resources.getFont(fontId))
        }
        typedArray.recycle()
    }

    fun initialize() {
        val composeView = ComposeView(context).apply {
            setContent {
                MindMap(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    fontFamily = fontFamily,
                    onNodeClick = { node -> nodeClickListener?.onClickListener(node) },
                    onNodeDoubleClick = { node -> nodeDoubleClickListener?.onDoubleClickListener(node) },
                    onNodeMove = { tree, target, parent ->
                        nodeMoveListener?.onMoveListener(tree, target, parent)
                    },
                )
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    @JvmOverloads
    fun addNode(
        description: String,
        shape: NodeShape = NodeShape.RECTANGLE,
        color: Color = shape.defaultColor,
        textColor: Color = shape.defaultTextColor,
    ) {
        state.addNode(description, shape, color, textColor)
    }

    fun getAddNode(): NodeData<*>? {
        return state.getAddNode()
    }

    fun removeNode() {
        state.removeNode()
    }

    fun fitScreen() {
        state.fitScreen()
    }

    fun editNodeText(description: String) {
        state.editNodeText(description)
    }

    fun editNodeColor(color: Color) {
        state.editNodeColor(color)
    }

    fun editNodeTextColor(textColor: Color) {
        state.editNodeTextColor(textColor)
    }

    fun animateTreeChange() {
        state.animateTreeChange()
    }

    fun setTree(tree: Tree<*>) {
        state.setTree(tree)
    }

    fun getTree(): Tree<*> {
        return state.tree
    }

    fun getMindMapManager(): MindMapManager {
        return state.mindMapManager
    }

    fun getState(): MindMapState {
        return state
    }

    fun setNodeDoubleClickListener(nodeDoubleClickListener: NodeDoubleClickListener) {
        this.nodeDoubleClickListener = nodeDoubleClickListener
    }

    fun setNodeClickListener(nodeClickListener: NodeClickListener) {
        this.nodeClickListener = nodeClickListener
    }

    fun setNodeMoveListener(nodeMoveListener: NodeMoveListener) {
        this.nodeMoveListener = nodeMoveListener
    }
}
