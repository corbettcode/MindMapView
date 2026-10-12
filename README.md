# MindMapView

You can easily draw mind maps. It allows for node creation, deletion, as well as zooming and moving.

```
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
  }
}
```

```
dependencies {
  implementation 'com.github.hegleB:MindMapView:0.1.0'
}
```

## Funtions
- zoom, move
- Fix your window
- remove nodes
- add nodes
- edit node text

|Add|Remove|Edit|Move|Zoom and Fit Window|
|---|---|---|---|---|
|![add](https://github.com/hegleB/MindMapView/assets/39490416/e8bf4cc8-253a-4a7b-9270-9048959cb2f2)|![remove](https://github.com/hegleB/MindMapView/assets/39490416/83283bdc-f704-4f89-8af4-27c2d5c8a771)|![edit](https://github.com/hegleB/MindMapView/assets/39490416/da752563-a85d-440a-948a-4e625fedfed1)|![move](https://github.com/hegleB/MindMapView/assets/39490416/5e226f2e-2420-4356-8473-7f9be5af2471)|![zoom_and_fit](https://github.com/hegleB/MindMapView/assets/39490416/1241982a-b7b9-499c-8b6b-37141bfa61d0)|

## Compose Multiplatform
The library is a Kotlin Multiplatform library built on Compose Multiplatform. It supports Android, Desktop (JVM), iOS (`iosArm64`, `iosSimulatorArm64`) and Web (`wasmJs`).

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.hegleB:MindMapView:<version>")
        }
    }
}
```

```kotlin
@Composable
fun MindMapScreen() {
    val state = rememberMindMapState()

    Column {
        Row {
            Button(onClick = { state.addNode("new node") }) { Text("Add") }
            Button(onClick = { state.editNodeText("edited") }) { Text("Edit") }
            Button(onClick = { state.removeNode() }) { Text("Remove") }
            Button(onClick = { state.fitScreen() }) { Text("Fit") }
        }
        MindMap(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onNodeClick = { node -> /* node is null when empty space is touched */ },
            onNodeMove = { tree, target, parent -> /* target was attached to parent */ },
        )
    }
}
```

- `addNode`, `editNodeText` and `removeNode` act on the selected node (`state.selectedNode`).
- `rememberMindMapState()` places the root node at the center of the mind map. Pass `Tree<Any>(Dp(x), Dp(y))` to choose the position yourself.
- The root has two sets of children: one laid out to its right and one to its left. `addNode` takes the `side` a child of the root is added to, which defaults to `state.layoutSide`. Setting `state.layoutSide` also moves every child of the root to that side.
- Pass `onAddChildClick` to `MindMap` to draw a '+' button on every node, where its lines to its children start; the root gets one on each side. Clicking it selects that node and calls `onAddChildClick` with the node and the side, so a following `addNode` with that side adds the child there.
- Drag a node onto the left or right half of the root to move it to that side.
- Drag empty space to move, pinch or use the mouse wheel to zoom, and drag a node onto another node to re-attach it.

### FreeMind files
`FreeMind` converts a tree to and from the text of a FreeMind `.mm` file. Reading and writing the file itself is up to the app.

```kotlin
val content = FreeMind.write(state.tree)

state.setTree(FreeMind.read(content))
state.animateTreeChange() // positions are not stored in the file, so arrange the tree
```

## Sample apps
- Android: `./gradlew :app:installDebug`
- Desktop: `./gradlew :desktop:run`

## Android View
`MindMapView` is still available on Android as a View that hosts the composable.

1. Add the MindMapView to your XML layout
```kotlin
<com.mindsync.library.MindMapView
    android:id="@+id/mind_map_view"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent" />
```

2. Define Node Classes
```kotlin
sealed class Node(
    open val id: String,
    open val parentId: String?,
    open val path: NodePath,
    open val description: String,
    open val children: List<String>,
) {
    abstract fun adjustPosition(
        horizontalSpacing: Dp,
        totalHeight: Dp,
    ): Node
}

data class CircleNode(
    override val id: String,
    override val parentId: String?,
    override val path: CirclePath = CirclePath(Dp(0f), Dp(0f), Dp(0f)),
    override val description: String,
    override val children: List<String>,
) : Node(id, parentId, path, description, children) {
    override fun adjustPosition(
        horizontalSpacing: Dp,
        totalHeight: Dp,
    ): Node {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}

data class StadiumNode(
    override val id: String,
    override val parentId: String?,
    override val path: StadiumPath = StadiumPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
    override val description: String,
    override val children: List<String>,
) : Node(id, parentId, path, description, children) {
    override fun adjustPosition(
        horizontalSpacing: Dp,
        totalHeight: Dp,
    ): Node {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}

data class RectangleNode(
    override val id: String,
    override val parentId: String,
    override val path: RectanglePath = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f)),
    override val description: String,
    override val children: List<String>,
) : Node(id, parentId, path, description, children) {
    override fun adjustPosition(
        horizontalSpacing: Dp,
        totalHeight: Dp,
    ): Node {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}
```

2. Initialize the Mind Map View
```kotlin
    val tree = Tree<Node>(this)
        binding.mindMapView.setTree(tree)
        binding.mindMapView.initialize()
```

3. Implement Node Click Listener
```kotlin
binding.mindMapView.setNodeClickListener(object : NodeClickListener {
    override fun onClickListener(node: NodeData<*>?) {
        val selectedNode = createNode(node)
        ...
    }
})
```

4. Create Nodes Based on User Interaction
```kotlin
fun createNode(node: NodeData<*>?): Node? {
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

            // The root node is a stadium.
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

            else -> null
        }
    }
```

5. Additional View Manipulations
```
binding.mindMapView.addNode(description)
binding.mindMapView.editNodeText(description)
binding.mindMapView.fitScreen()
```
