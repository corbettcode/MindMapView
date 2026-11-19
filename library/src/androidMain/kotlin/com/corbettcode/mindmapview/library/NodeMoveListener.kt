package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.Tree

interface NodeMoveListener {
    fun onMoveListener(
        tree: Tree<*>,
        target: NodeData<*>,
        parent: NodeData<*>,
    )
}