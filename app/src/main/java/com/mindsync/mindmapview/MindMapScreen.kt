package com.mindsync.mindmapview

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.MindMap
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.rememberMindMapState
import com.mindsync.mindmapview.model.CircleNode
import com.mindsync.mindmapview.model.CirclePath
import com.mindsync.mindmapview.model.Node
import com.mindsync.mindmapview.model.OvalNode
import com.mindsync.mindmapview.model.OvalPath
import com.mindsync.mindmapview.model.RectangleNode
import com.mindsync.mindmapview.model.RectanglePath
import com.mindsync.mindmapview.model.StadiumNode
import com.mindsync.mindmapview.model.StadiumPath

private enum class Operation { ADD, UPDATE }

@Composable
fun MindMapScreen(viewModel: MindMapViewModel) {
    val state = rememberMindMapState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    var operation by remember { mutableStateOf<Operation?>(null) }
    var addSide by remember { mutableStateOf<LayoutSide?>(null) }
    val nodeFontFamily = remember { FontFamily(Font(R.font.pretendard_bold)) }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                MindMap(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    fontFamily = nodeFontFamily,
                    onNodeClick = { node -> viewModel.setSelectedNode(createNode(node)) },
                    onAddChildClick = { node, side ->
                        viewModel.setSelectedNode(createNode(node))
                        addSide = side
                        operation = Operation.ADD
                    },
                )
                IconButton(
                    onClick = { state.fitScreen() },
                    modifier = Modifier.align(Alignment.TopEnd).size(50.dp),
                ) {
                    Image(painterResource(R.drawable.ic_fit_screen), contentDescription = "Fit screen")
                }
            }
            if (selectedNode != null) {
                MindMapSideBar(
                    removeEnabled = selectedNode is RectangleNode,
                    onAdd = {
                        addSide = null
                        operation = Operation.ADD
                    },
                    onRemove = {
                        state.removeNode()
                        viewModel.setSelectedNode(null)
                    },
                    onEdit = { operation = Operation.UPDATE },
                )
            }
        }

        operation?.let { currentOperation ->
            EditDescriptionDialog(
                description = if (currentOperation == Operation.ADD) "" else selectedNode?.description.orEmpty(),
                onSubmit = { description ->
                    when (currentOperation) {
                        Operation.ADD -> state.addNode(description, side = addSide ?: state.layoutSide)
                        Operation.UPDATE -> state.editNodeText(description)
                    }
                },
                onDismiss = { operation = null },
            )
        }
    }
}

@Composable
private fun MindMapSideBar(
    removeEnabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAdd) {
            Image(
                painterResource(R.drawable.ic_add),
                contentDescription = "Add node",
                modifier = Modifier.size(25.dp),
            )
        }
        IconButton(onClick = onRemove, enabled = removeEnabled) {
            Image(
                painterResource(if (removeEnabled) R.drawable.ic_remove else R.drawable.ic_banned_remove),
                contentDescription = "Remove node",
                modifier = Modifier.size(25.dp),
            )
        }
        IconButton(onClick = onEdit) {
            Image(
                painterResource(R.drawable.ic_outlined_drawing),
                contentDescription = "Edit node",
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

private fun createNode(node: NodeData<*>?): Node? {
    return when (node) {
        is CircleNodeData -> CircleNode(
            node.id,
            node.parentId,
            CirclePath(
                Dp(node.path.centerX.dpVal),
                Dp(node.path.centerY.dpVal),
                Dp(node.path.radius.dpVal)
            ),
            node.description,
            node.children
        )

        is RectangleNodeData -> RectangleNode(
            node.id,
            node.parentId,
            RectanglePath(
                Dp(node.path.centerX.dpVal),
                Dp(node.path.centerY.dpVal),
                Dp(node.path.width.dpVal),
                Dp(node.path.height.dpVal)
            ),
            node.description,
            node.children
        )

        is StadiumNodeData -> StadiumNode(
            node.id,
            node.parentId,
            StadiumPath(
                Dp(node.path.centerX.dpVal),
                Dp(node.path.centerY.dpVal),
                Dp(node.path.length.dpVal),
                Dp(node.path.radius.dpVal)
            ),
            node.description,
            node.children
        )

        is OvalNodeData -> OvalNode(
            node.id,
            node.parentId,
            OvalPath(
                Dp(node.path.centerX.dpVal),
                Dp(node.path.centerY.dpVal),
                Dp(node.path.radiusX.dpVal),
                Dp(node.path.radiusY.dpVal)
            ),
            node.description,
            node.children
        )

        else -> null
    }
}
