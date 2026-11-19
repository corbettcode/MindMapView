# Map Structures Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A map can be laid out in other **structures** than the mind map, as XMind's most used feature after the map
itself: a **logic chart** (everything to the right, or to the left), an **org chart** (down, or up), a **tree chart**
(indented lists below each topic), a **brace map** and a **timeline**. The structure is chosen per map and changes only
how the map is drawn: the topics, their order and everything about them stay as they are, and switching back gives the
map as it was.

Today the layout knows one shape: children stacked **vertically**, growing **left or right** from their parent, with
the root's children on six sides. That assumption is in more places than the layout manager: lines leave and enter
nodes at their left and right edges (`drawLines`, `getNodeEdgeX`), summary brackets are vertical (`SummaryGeometry`),
add buttons and fold rings sit on the left or right edge (`addButtonCenter`, `foldMarkerCenter`), dropping a dragged
topic among its siblings compares `centerY` (`Tree.moveAmongSiblings`), and boundaries leave room above and below
(`boundaryGaps`). Each structure needs all of them right.

**Architecture:**

- **Structure:** `enum class MapStructure` on the tree (`tree.structure`, default `MIND_MAP`), saved in the map file,
  part of the undo snapshot, and set with `state.setStructure(…)` as one change. Floating topics follow it too.
- **Direction, not side:** each branch grows in a `GrowDirection` — `RIGHT`, `LEFT`, `DOWN`, `UP` — that a structure
  gives it (`MapStructure.directionOf(tree, id)`). The mind map gives `RIGHT` or `LEFT` by the root's sides, as now.
  `LayoutSide` stays for the mind map's six sides of the root.
- **One layout, turned:** the side layout is rewritten in **main / cross** coordinates (main = the way the branch
  grows, cross = the way siblings are stacked) and turned to screen coordinates by the direction. `RIGHT` is today's
  layout exactly; `DOWN` is the same layout turned, with widths and heights swapped. Everything that measures a node
  (`halfWidth`, `ownHeight`, the gaps) goes through the direction too.
- **Geometry follows the direction:** line ends, the bend of the curve, summary brackets, add buttons, fold rings and
  the drop-among-siblings test all take the branch's direction instead of assuming left or right. The tree chart and
  the timeline add their own placement on top, and their own line shape (an elbow), as they aren't a turned layout.

**Build order:** phase A turns today's layout into the direction-based one with no visible change, and adds the
logic chart, which needs nothing else; phase B adds the org chart (the vertical direction); phase C the tree chart and
the brace map; phase D the timeline; phase E files, the app and the docs. Each phase can ship on its own.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Per map or per branch | **Per map** in this plan. XMind can give a branch its own structure; that is a follow-up, which the direction design allows (a branch's direction would come from its nearest topic with a structure). |
| Structures in this plan | Mind map (today's), Logic chart → / ←, Org chart ↓ / ↑, Tree chart → / ←, Brace map →, Timeline (horizontal). **Not** in this plan: fishbone and matrix (both need their own geometry: diagonal ribs, and table cells), and XMind's "clockwise" mind map, which is read as the mind map. |
| Where it is chosen | View › Structure, a submenu with a small picture of each, and the same in the Preferences (Map) for new maps. It is the map's, saved in the file, so it opens as it was saved. |
| Undo | Changing the structure is one change to undo. Nothing else changes with it, so undo gives the same map back. |
| The root's sides in other structures | Kept but not used: a map laid out as a logic chart and switched back to the mind map has its branches on the sides they were on. "Move Clockwise" and the side add buttons of the root are only in the mind map. |
| Line shapes | Each line style (linear, Bézier, sharp) bends along the branch's direction, so an org chart's lines leave the bottom of a topic and enter the top of its children. The tree chart and timeline draw elbows (a spine and straight stubs) whatever the line style, as they do in XMind; the line width and color still apply. |
| Summaries | The bracket is past the branches it sums up along the direction they grow: to their right in a logic chart, below them in an org chart down. In a tree chart and timeline, summaries are drawn as in the logic chart to the right. |
| Boundaries | Unchanged in shape (a rounded rectangle around the branches); the layout leaves room for them along the cross axis, as it does now vertically. |
| Floating topics | Their branch grows in the structure's direction for the root's children: right in a logic chart, down in an org chart. |
| Drag to reorder | A topic dropped among its siblings takes its place along the cross axis: by its x in an org chart, by its y otherwise. |
| Keyboard | Arrow keys already select by screen direction, so they need no change. Ctrl+Up / Down (next and previous sibling) follow the order, whatever the direction. |
| Exports | PNG, SVG and PDF draw the structure as on screen; outlines (OPML, Markdown, HTML) are unchanged, as they have no layout. |
| Files | `.ccmm`: `<mindmapview structure="org-chart-down">`, left out for the mind map, so other maps are written as before. XMind: read `structureClass` of the central topic (`org.xmind.ui.map.unbalanced` and `…map.clockwise` are confirmed by the repo's samples and tests; `org.xmind.ui.logic.right`, `…org-chart.down`, `…tree.right`, `…brace.right`, `…timeline.horizontal` are **to confirm** with sample files before E1); an unknown one is read as the mind map. FreeMind/Freeplane: not written (Freeplane's layouts are its own). |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Phase A must not change a single pixel of today's maps.** `ThemeDrawingTest.classicFeatureTourLooksAsBefore`
  guards that, and every layout test already in the library must pass unchanged. Run the suites with `--rerun`, as
  Gradle does not see a change of `desktop/feature_tour.ccmm` as a reason to run that test again.
- Works in the web app (wasm): common code only.
- Maps saved before this open as mind maps; in older versions, a map with another structure opens as a mind map.

## Review Focus

1. **No change in the mind map:** phase A's refactor keeps every position, line and bracket where it was.
2. **Every horizontal assumption found:** lines, brackets, add buttons, fold rings, drop among siblings, boundary
   gaps, the slanted sides, `fittedRoot` / `measureOverflow` (which assume growth to the right and down), Fit, and the
   inline text editor's place over the node.
3. **Switching back:** a map switched to each structure and back is the same map, side by side, with one undo step
   each way.
4. **Big maps:** the feature tour in each structure, rendered and looked at, with nothing overlapping.

---

## Phase A: Directions, and the logic chart

### Task A1: The structure in the tree

**Files:** new `model/MapStructure.kt` (`MapStructure`, `GrowDirection`), `data/Tree.kt` (`structure`),
`data/TreeSnapshot.kt`, `MindMapState.kt` (`setStructure`), test `MapStructureTest.kt`

- [x] `enum class GrowDirection { RIGHT, LEFT, DOWN, UP }` with `isHorizontal` and `sign` (+1 right and down); the
  turn between screen and main/cross coordinates comes with the layout that uses it (A2).
- [x] `enum class MapStructure { MIND_MAP, LOGIC_RIGHT, LOGIC_LEFT, ORG_DOWN, ORG_UP, TREE_RIGHT, TREE_LEFT,
  BRACE_RIGHT, TIMELINE }` with `directionOf(tree, id)`: the mind map by the root's sides as now, the others the one
  direction they have (the timeline's branches up and down by turn).
- [x] `tree.structure`, in the snapshot and `hasSameContentAs`; `state.setStructure(structure)` is one change that
  re-lays out the map. Only `MIND_MAP` is laid out differently until the later tasks; the others fall back to it.
- [x] Tests (6): default; set and undo; a structure change alone is a change; directions in the mind map by side;
  the other structures one way, floating topics too; the timeline up and down by turns.

### Task A2: The layout in main and cross coordinates

**Files:** `layout/MindMapLayoutManager.kt` (and its left and right subclasses, folded into one taking a direction),
`MindMapManager.kt`, test `DirectionLayoutTest.kt`

- [ ] The side layout works in main/cross coordinates: stacking, gaps, summaries' room and boundaries' room along the
  cross axis; children placed along the main axis. `RIGHT` and `LEFT` give today's positions exactly.
- [ ] The root's slanted sides stay a mind-map-only step on top.
- [ ] `fittedRoot` and `measureOverflow` measure reach along both axes, so a structure growing up or left is kept on
  the map.
- [ ] Tests: every existing layout test passes unchanged; the feature tour's golden image is unchanged; `DOWN` places
  a parent's children below it, stacked left to right and centered on it (6 new).

### Task A3: Geometry by direction

**Files:** `MindMap.kt` (`drawLines`, `getNodeEdgeX`, `addButtonCenter`, `foldMarkerCenter`),
`layout/SummaryGeometry.kt`, `data/Tree.kt` (`moveAmongSiblings` along the cross axis), `MindMapState.kt`
(`addButtonSides`), tests `DirectionGeometryTest.kt`

- [ ] Line ends on the parent's edge facing the direction and the child's edge facing back; the curve's bend along
  the main axis. Summary brackets past the range along the main axis, spanning it along the cross axis. Add buttons
  and fold rings on the growing edge. Dropping among siblings compares the cross axis.
- [ ] No change for `RIGHT` and `LEFT`: the golden image and all drawing tests pass unchanged.
- [ ] Tests (6): each piece for `DOWN` (line end points, bracket, add button, fold ring, drop order), and the golden.

### Task A4: The logic chart

**Files:** `model/MapStructure.kt`, `MindMapManager.kt`, `MindMapState.kt` (root add buttons, Move Clockwise only in
the mind map), test `LogicChartTest.kt`

- [ ] `LOGIC_RIGHT` / `LOGIC_LEFT` lay all the root's children out on one side, in their order, straight out (no
  slanted sides); the root's sides are kept for switching back.
- [ ] Tests (4): all to the right in order; to the left; back to the mind map with the sides as they were; undo.

---

## Phase B: The org chart

### Task B1: Org chart down and up

**Files:** `model/MapStructure.kt`, `MindMapManager.kt`, `layout/BoundaryGeometry.kt` (gaps along x), the inline
editor's place (`NodeTextEditor`, unchanged if it reads the node's center), tests `OrgChartTest.kt`

- [ ] `ORG_DOWN` lays every branch out with `DOWN`, the root's children in a row below it; `ORG_UP` with `UP`.
- [ ] Lines leave the bottom (top) of a topic and enter the top (bottom) of its children, in every line style.
- [ ] Wide topics: siblings are spaced by their own width and their branches' width, so neighbours never overlap.
- [ ] Tests (7): positions; line ends; summary below; a boundary leaves room left and right; drag reorder by x; a
  floating topic's branch grows down; the feature tour rendered as an org chart and looked at (and kept as a
  second golden image).

---

## Phase C: The tree chart and the brace map

### Task C1: Tree chart

**Files:** new `layout/TreeChartLayout.kt`, `MindMap.kt` (elbow lines), tests `TreeChartTest.kt`

- [ ] Each topic's children are below it, stacked down, indented by a fixed step to the right (`TREE_RIGHT`) or left;
  a branch takes the height of all its rows. The root's children are side by side in a row, each with its column below.
- [ ] Lines: a vertical spine from below the parent, with a straight stub into the side of each child.
- [ ] Tests (5): positions; elbow line points; folding shortens the column; summaries to the right; the tour rendered.

### Task C2: Brace map

**Files:** `model/MapStructure.kt`, `MindMap.kt` (brace connectors, from the summary bracket's drawing),
tests `BraceMapTest.kt`

- [ ] Laid out as the logic chart to the right; each parent is joined to its children by a brace spanning them, in
  place of separate lines.
- [ ] Tests (3): a brace per parent with children; none for a leaf; line color and width followed.

---

## Phase D: The timeline

### Task D1: Horizontal timeline

**Files:** new `layout/TimelineLayout.kt`, `MindMap.kt` (the axis line), tests `TimelineTest.kt`

- [ ] The root's children are placed along a horizontal axis to the right of the root, evenly by the width of their
  branches; their branches grow up and down by turns, as org charts.
- [ ] The axis is one line through the root and its children; each branch joins it with an elbow.
- [ ] Tests (5): order along the axis; up and down by turns; spacing by branch width; the axis line; the tour
  rendered.

---

## Phase E: Files, the app and the docs

### Task E1: In files

**Files:** `io/MindMapViewFile.kt`, `io/XMind.kt`, tests `MapStructureFilesTest.kt`, `XMindTest.kt`

- [ ] `.ccmm` keeps the structure; a map in the mind map is written as before.
- [ ] XMind's `structureClass` of the central topic, once the names are confirmed with sample files; unknown ones are
  the mind map.
- [ ] Tests (4): round trip; nothing written for the mind map; XMind names; an unknown one.

### Task E2: In the app

**Files:** `menuBar/ViewMenu.kt` (Structure submenu), new `component/StructurePicture.kt`,
`dialog/PreferencesDialog.kt` (new maps' structure), `UserPreferences.kt`, `MindMapApp.kt`, test
`StructureAppTest.kt`

- [ ] View › Structure with a tick on the current one and a small drawing of each; Preferences › Map › Structure for
  new maps.
- [ ] Root menus show Move Clockwise and the side add buttons only in the mind map.
- [ ] Tests (4): the submenu sets it and undo takes it back; new maps get the preference; the root's buttons; a map
  opens in its saved structure.

### Task E3: Docs

**Files:** `docs/user-manual/user-manual.html` (a "Structures" section in chapter 5, with a picture of each), PDF,
`README.md`

- [ ] What each structure is for, how to choose it, what it keeps, and how it is kept in each format.

---

## Out of scope / follow-ups

- A structure per branch, as XMind allows.
- Fishbone and matrix structures.
- XMind's clockwise mind map, as its own order of the root's sides.
- Free positioning of attached topics.
