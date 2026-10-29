# Node Line Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let each node set the style, color and width of the line into it, inherited by its branch like FreeMind,
edited in the edit dialog, saved in `.mmv` and read from and written to FreeMind `.mm`.

**Architecture:** The tree keeps each node's own line settings in a side map, like attributes and folding, included in
undo snapshots. A single traversal in the library resolves the line each node is drawn with (own settings over
inherited, defaults at the top) and feeds the line, add-button and fold-marker drawing. The desktop edit dialog gains
a line settings section.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.1, kotlin.test, `runComposeUiTest` for image tests.

**Spec:** `docs/superpowers/specs/2026-10-29-node-line-style-design.md`

## Global Constraints

- Styles: Linear, Bezier, Sharp Linear, Sharp Bezier. Widths: Thin, 1, 2, 4, 8 (a number is that many dp; Thin is
  0.75 dp). With nothing set: Bezier, 5 dp (`DrawInfo.lineWidth`), branch colors from `DrawInfo.rootLineColors`.
- Each of style, color and width is inherited separately; null means inherited. The root may hold settings, which
  every branch inherits.
- A chosen color is drawn as chosen in both themes.
- Library public API changes are additive: new parameters last, with defaults.
- `CLAUDE.md` Kotlin style: leading commas, K&R braces, 120 columns. Commit directly on `main`.
- No line is drawn while a node is moved (its line is hidden today), so the spec's "drag line" needs nothing.

## Review Focus

1. A `.mmv` saved before this, or a FreeMind file without `<edge>` → no settings, looks as today → Task 2
   `fileWithoutLinesHasNone`.
2. Unknown or broken values in a file (`lineStyle="zigzag"`, `WIDTH="wide"`, bad color) → that setting is inherited,
   no crash → Task 2 `unknownLineValuesAreInherited`.
3. Cut and paste of a branch, and paste more than once → each pasted node keeps its own settings → Task 3
   `copiedBranchKeepsItsLines`.
4. Removing a node with settings, then undo → the settings come back → Task 3 `undoBringsBackRemovedNodeLine`.
5. A FreeMind root `<edge>` → kept on the root, inherited by all branches, written back on export → Task 2
   `freeMindRootEdgeRoundTrips`.

---

### Task 1: Line settings in the tree, and how they resolve

**Files:**
- Create: `library/src/commonMain/kotlin/com/mindsync/library/data/NodeLine.kt`
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/data/Tree.kt` (storage next to `nodeAttributes`;
  `snapshot`, `restore`), `library/src/commonMain/kotlin/com/mindsync/library/data/TreeSnapshot.kt` (field and
  `hasSameContentAs`)
- Create: `library/src/commonMain/kotlin/com/mindsync/library/NodeLines.kt` (resolution)
- Test: `library/src/commonTest/kotlin/com/mindsync/library/NodeLineTest.kt`

**Interfaces:**
- Produces (public, package `com.mindsync.library.data`):
  ```kotlin
  enum class NodeLineStyle(val freeMindName: String) {
      LINEAR("linear"), BEZIER("bezier"), SHARP_LINEAR("sharp_linear"), SHARP_BEZIER("sharp_bezier")
  }
  enum class NodeLineWidth(val label: String, val width: Dp) {
      THIN("thin", Dp(0.75f)), ONE("1", Dp(1f)), TWO("2", Dp(2f)), FOUR("4", Dp(4f)), EIGHT("8", Dp(8f))
  }
  data class NodeLine(val style: NodeLineStyle? = null, val color: Color? = null, val width: NodeLineWidth? = null) {
      val isEmpty: Boolean
  }
  // Tree:
  fun lineOf(id: String): NodeLine                 // the node's own settings; NodeLine() when none
  fun setLine(id: String, line: NodeLine)          // an empty line removes the entry
  ```
- Produces (internal, package `com.mindsync.library`):
  ```kotlin
  internal data class ResolvedLine(val style: NodeLineStyle, val color: Color, val width: Dp)
  // Calls action for every shown node with the line it is drawn with. Replaces forEachNodeWithLineColor.
  internal fun forEachNodeWithLine(
      tree: Tree<*>, drawInfo: DrawInfo, rootColor: Color, action: (node: NodeData<*>, line: ResolvedLine) -> Unit
  )
  ```
  Resolution: the root resolves with its own settings over (BEZIER, `rootColor`, `drawInfo.lineWidth`); a child of the
  root inherits style and width from the root, and color from the root's own color or else
  `rootLineColor(drawInfo, index)`; any other node inherits from its parent; a node's own settings override.

- [ ] **Step 1: Write the failing tests** (build trees with `Tree<Any>(Dp(150f), Dp(300f))` and `tree.addNode` as in
  `MindMapTest`; collect `forEachNodeWithLine` results into a map by id)

```kotlin
@Test fun nothingSetLooksAsToday()        // children of root: BEZIER, rootLineColor(drawInfo, i), drawInfo.lineWidth
@Test fun ownSettingAppliesToItsBranch()  // A.style=LINEAR → A and A's child LINEAR; A's sibling BEZIER
@Test fun settingsAreInheritedSeparately()// A.color=Red, A's child width=EIGHT → child: BEZIER, Red, 8 dp
@Test fun rootSettingsReachEveryBranch()  // root color Blue → every child Blue, not the branch palette
@Test fun emptyLineMakesTheNodeInheritAgain() // setLine(A, LINEAR) then setLine(A, NodeLine()) → BEZIER; lineOf(A).isEmpty
@Test fun snapshotKeepsLines()            // setLine; snapshot; setLine(other); restore → lineOf back; hasSameContentAs differs
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :library:jvmTest --tests "*NodeLineTest"`
- [ ] **Step 3: Implement** `NodeLine.kt`, the tree storage (`nodeLines`), snapshot/restore, and `NodeLines.kt`.
- [ ] **Step 4: Run** `./gradlew :library:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit**: `feat: keep and resolve each node's line settings`.

### Task 2: Save and read line settings (.mmv and FreeMind)

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/io/MindMapViewFile.kt` (`writeNode`, `readNode`, the
  root in `read`)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/io/FreeMind.kt` (`writeNode`, `readNode`, the root in
  `read`)
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MindMapViewFileTest.kt`, and a new
  `library/src/commonTest/kotlin/com/mindsync/library/FreeMindLineTest.kt`

**Interfaces:**
- Consumes: `NodeLine`, `NodeLineStyle.freeMindName`, `NodeLineWidth.label`, `Tree.lineOf/setLine` (Task 1).
- `.mmv` `<node>` attributes, written only when set: `lineStyle` (the `freeMindName`), `lineColor` (same hex format as
  `color`), `lineWidth` (the `label`). FreeMind: a child `<edge STYLE=… COLOR=… WIDTH=…/>` with only the set ones,
  written only when the line is not empty. Reading FreeMind `WIDTH`: `thin` → THIN; a number → the nearest of 1, 2,
  4, 8 (ties go to the wider); anything else → null. Unknown style or bad color → null. Root included both ways.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun linesRoundTrip()                // child with all three and root with a color → write → read → same lineOf
@Test fun fileWithoutLinesHasNone()       // an existing test fixture / file text without line attributes → every lineOf isEmpty
@Test fun unknownLineValuesAreInherited() // lineStyle="zigzag" lineWidth="wide" lineColor="nope" → NodeLine()
@Test fun freeMindEdgeIsRead()            // <edge STYLE="sharp_linear" COLOR="#ff0000" WIDTH="4"/> → (SHARP_LINEAR, Red, FOUR)
@Test fun freeMindWidthsReadAsTheNearest()// "thin"→THIN, "1"→ONE, "3"→FOUR, "6"→EIGHT, "20"→EIGHT, "wide"→null
@Test fun freeMindRootEdgeRoundTrips()    // root <edge COLOR="#0000ff"/> → root lineOf color Blue → write has <edge COLOR on root
@Test fun noEdgeIsWrittenWithoutSettings()// a tree with no settings → write contains no "<edge"
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :library:jvmTest --tests "*MindMapViewFileTest" --tests "*FreeMindLineTest"`
- [ ] **Step 3: Implement** the writing and reading.
- [ ] **Step 4: Run** `./gradlew :library:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit**: `feat: save line settings in .mmv and FreeMind files`.

### Task 3: Draw the lines, and edit them through the state

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMap.kt` (`drawLines`, `drawAddButtons`,
  `drawFoldMarkers`; remove `forEachNodeWithLineColor`)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMapState.kt` (`editNodeLine`, `addNode`,
  `CopiedNode`, `copyBranch`, `pasteNode`)
- Test: `library/src/commonTest/kotlin/com/mindsync/library/ExportImageTest.kt`, `UndoTest.kt` or `MindMapTest.kt`
  (follow where `editNodeColor` and copy/paste are tested)

**Interfaces:**
- Consumes: `forEachNodeWithLine`, `ResolvedLine` (Task 1).
- Produces (public, `MindMapState`):
  - `fun editNodeLine(line: NodeLine)` — gives the selected node these own settings; recorded for undo.
  - `addNode(…, note: String = "", line: NodeLine = NodeLine())` — new last parameter.
- Drawing: LINEAR is a straight stroke; BEZIER today's curve; SHARP_* fill a closed shape that is `width` wide at the
  start, centered on the start point, and narrows to the end point, following the straight line or the curve. The
  root's slanted sides keep their bend for all four. Strokes and shapes use the resolved color and width.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun chosenLineColorIsDrawn()        // ExportImageTest style: a child with line color Color(0xFFFF0000) → exported pixels contain it
@Test fun editNodeLineIsUndone()          // select child; editNodeLine(LINEAR); undo → lineOf(child).isEmpty; redo → LINEAR
@Test fun undoBringsBackRemovedNodeLine() // set line, remove node, undo → node back with its line
@Test fun copiedBranchKeepsItsLines()     // child with own line and grandchild with own width; copy; paste twice → each pasted copy has them
@Test fun addedNodeGetsItsLine()          // addNode(…, line = NodeLine(style = SHARP_BEZIER)) → new node's lineOf
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :library:jvmTest`
- [ ] **Step 3: Implement** the drawing with `forEachNodeWithLine`, then the state functions.
- [ ] **Step 4: Run** `./gradlew :library:jvmTest :library:compileKotlinWasmJs` — Expected: PASS.
- [ ] **Step 5: Commit**: `feat: draw each node's line style, color and width`.

### Task 4: Line settings in the edit dialog

**Files:**
- Create: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/component/LineSettings.kt`
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/dialog/EditNodeDialog.kt`
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/MindMapApp.kt` (the `EditNodeDialog` call)
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/LineSettingsTest.kt`

**Interfaces:**
- Consumes: `NodeLine`, `NodeLineStyle`, `NodeLineWidth`, `Tree.lineOf`, `MindMapState.editNodeLine`,
  `addNode(line = …)` (Tasks 1, 3); `ChoicePicker` (in `dialog/PreferencesDialog.kt`), `ColorPicker`.
- Produces:
  - `@Composable fun LineSettings(line: NodeLine, colors: List<Color>, onChange: (NodeLine) -> Unit)` — three rows,
    each with an "Inherited" choice first: "Line" (style: Inherited, Linear, Bezier, Sharp linear, Sharp bezier),
    "Line width" (Inherited, Thin, 1, 2, 4, 8), "Line color" (an Inherited choice, then the colors).
  - `internal val LINE_STYLE_LABELS: Map<NodeLineStyle?, String>` and `internal val LINE_WIDTH_LABELS:
    Map<NodeLineWidth?, String>` with the labels above.
  - `EditNodeDialog(…, line: NodeLine, …)` and `onSubmit` gains a last `line: NodeLine` parameter. The section sits
    after the Fill and Text colors and shows for the root too. Offered colors: `DrawInfo().rootLineColors` plus the
    node's own line color when it is not among them.
  - `MindMapApp` passes `state.tree.lineOf(selectedNode.id)` for an update and `NodeLine()` for an add, and submits
    with `state.editNodeLine(line)` inside the existing `groupChanges`, or `addNode(…, line = line)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun everyStyleAndWidthHasALabelAfterInherited() {
    assertEquals(listOf(null) + NodeLineStyle.entries, LINE_STYLE_LABELS.keys.toList())
    assertEquals(listOf("Inherited", "Linear", "Bezier", "Sharp linear", "Sharp bezier"), LINE_STYLE_LABELS.values.toList())
    assertEquals(listOf("Inherited", "Thin", "1", "2", "4", "8"), LINE_WIDTH_LABELS.values.toList())
}
```

- [ ] **Step 2: Run to see it fail** — `./gradlew :desktop:jvmTest --tests "*LineSettingsTest"`
- [ ] **Step 3: Implement** `LineSettings`, the dialog parameter and section, and the `MindMapApp` wiring.
- [ ] **Step 4: Run** `./gradlew :desktop:jvmTest :desktop:compileKotlinWasmJs` — Expected: PASS.
- [ ] **Step 5: Check in the web app** (headless Chrome, as before; list what could not be checked): edit a child,
  pick Sharp bezier, 8 and a red color → its line and its branch below redraw that way, siblings unchanged; set a
  grandchild back to a different style → only its line changes; Inherited brings the default back; undo reverts the
  edit; edit the root and pick Linear → every branch straight; save and reopen keeps it.
- [ ] **Step 6: Commit**: `feat: choose a node's line style, color and width in the edit dialog`.
