package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.NodeData

interface NodeClickListener {
    fun onClickListener(node: NodeData<*>?)
}