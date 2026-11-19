package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.NodeData

interface NodeDoubleClickListener {
    fun onDoubleClickListener(node: NodeData<*>)
}