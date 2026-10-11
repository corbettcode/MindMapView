package com.mindsync.library

import com.mindsync.library.data.NodeData

interface NodeDoubleClickListener {
    fun onDoubleClickListener(node: NodeData<*>)
}