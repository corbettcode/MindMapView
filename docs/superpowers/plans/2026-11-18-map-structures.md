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

**Files:** `layout/MindMapLayoutManager.kt` (a `direction` and `isAllRootChildren` beside `side`; the left and right
subclasses stay as they are, being public), test `DirectionLayoutTest.kt`

- [x] The side layout works in main/cross coordinates: stacking, gaps, summaries' room and boundaries' room along the
  cross axis; children placed along the main axis; `adjustRoot` keeps the branches past the top and left of the map
  across, and along for a branch growing left or up. `RIGHT` and `LEFT` give today's positions exactly.
- [x] The root's slanted sides stay a mind-map-only step (not with `isAllRootChildren`, nor growing down or up).
- [x] Tests: all 645 library tests pass unchanged, the feature tour's golden image among them; 6 new: `DOWN` places
  the children below their parent in a row in order, a gap below, centered on it, each branch as wide as it needs;
  `UP` is `DOWN` turned over; `isAllRootChildren` takes the root's children whatever their side.

### Task A3: Geometry by direction

**Files:** `MindMap.kt` (`drawLines`, `lineAnchor`, `addButtonCenter(tree, …)` beside the mind map's own,
`foldMarkerCenter`, the bracket drawn through `SummaryBracket.at`), `layout/SummaryGeometry.kt`, `data/Tree.kt`
(`directionOf`, `moveAmongSiblings` along the cross axis), `MindMapState.kt` (`addButtonSides`, the drop), test
`DirectionGeometryTest.kt`

- [x] Line ends on the parent's edge facing the direction and the child's edge facing back (`lineAnchor`); the
  curve's bend along the main axis. Summary brackets past the range along the main axis, spanning it across. Add
  buttons and fold rings on the growing edge; the root has one button outside the mind map. Dropping among siblings
  compares the cross axis. The slanted sides' lines only in the mind map.
- [x] No change for `RIGHT` and `LEFT`: all 651 library tests pass on a full rerun, the golden image among them.
- [x] Tests (5): line ends down and up; add button, fold ring and the root's one button below; the summary bracket
  below the branches it sums up; drop order by x; a mind map's buttons where they were.

### Task A4: The logic chart

**Files:** `MindMapManager.kt` (`layoutManagerFor`, the root's layouts by structure, floating topics and fitting by
structure), `MindMapState.kt` (dragging a node lays it out by its branch's layout), test `LogicChartTest.kt`

- [x] `LOGIC_RIGHT` / `LOGIC_LEFT` lay all the root's children out on one side, in their order, straight out (no
  slanted sides); the root's sides are kept for switching back. Every structure with one direction (the org chart
  too) goes through the same path; the root of a map in another structure has one add button.
- [x] Tests (4): all to the right in order; to the left, kept on the map; back to the mind map with every position
  and side as it was; the root's buttons.

## Phase B: The org chart

### Task B1: Org chart down and up

**Files:** `layout/MindMapLayoutManager.kt` (spacing for branches growing down or up), `data/Tree.kt` (`rootGroupOf`
by structure), `MindMapState.kt` (summaries grouped the same way), tests `OrgChartTest.kt`, `ThemeDrawingTest.kt`
(`featureTourAsAnOrgChartLooksAsBefore`, golden `org-chart-feature-tour.png`)

- [x] `ORG_DOWN` lays every branch out with `DOWN`, the root's children in a row below it; `ORG_UP` with `UP`. Lines
  leave the bottom (top) of a topic and enter the top (bottom) of its children, in every line style (A3).
- [x] Side by side, siblings are 20 dp apart and 40 dp below their parent (50 and 50 one above the other), as text
  is wider than it is tall: the tour as an org chart went from 6587 to 5447 px wide going up.
- [x] Outside the mind map the root's attached children are one group for summaries and boundaries, as they are in
  one row; a summary keeps all it is about and, back in a mind map, is about those on its side again.
- [x] Tests (6 + the golden): in a row below the root whatever their side; up, kept on the map; a summary below what
  it sums up; a boundary leaves room left and right; a floating topic's branch grows down; a summary across two sides
  of the root. The tour as an org chart was rendered down and up and looked at; a floating topic keeps its offset from
  the root, so the tour's lands on the Import branch there, as it would in XMind.

## Phase C: The tree chart and the brace map

### Task C1: Tree chart

**Files:** new `layout/TreeChartLayout.kt`, `data/NodeData.kt` (`withCenter`), `MindMapManager.kt` (tree charts laid
out and fitted, `arrangeBranch` for dragging), `model/MapStructure.kt` (`isTreeChart`, `isStackedAlongX`,
`summaryDirectionOf`), `MindMap.kt` (`treeChartElbow`, buttons on the spine), `layout/SummaryGeometry.kt`,
`data/Tree.kt`, `MindMapState.kt`, test `TreeChartTest.kt`

- [x] The root's children are columns side by side in a row below it, centered on it; each topic's children are below
  it, stacked down, indented 30 dp to the right (`TREE_RIGHT`) or left. The chart is moved as a whole to be clear of
  the top and left of the map.
- [x] Lines: a vertical spine from below the parent, half an indent in, with a straight stub into the side of each
  child, in every line style (sharp ones as a wedge). The root's lines to its columns are curves down, as in an org
  chart. Add buttons and fold rings are on the spine.
- [x] Summaries: of rows, to their side, centered on them; of the root's columns, below them. Dropping a row among its
  siblings goes by its y, a column by its x.
- [x] Tests (8): columns in a row clear of each other; rows indented in order; the left chart; folding shortens the
  column; the elbow's points and the button on the spine; a summary of rows; a summary of columns; drop order by y.
  The tour was rendered both ways and looked at.

### Task C2: Brace map

**Files:** `MindMap.kt` (`drawBraces`, the bracket's drawing shared as `drawBracket`), `layout/SummaryGeometry.kt`
(`braceOf`, `braceChildIds`), test `BraceMapTest.kt`

- [x] Laid out as the logic chart to the right; each parent is joined to its shown children (but summaries and
  floating topics) by a brace spanning them, its point to the parent, in place of separate lines, in the color and
  width of the line to its first child.
- [x] Tests (3): the brace spans the children with its point to the parent; none for a leaf or a folded topic; an
  SVG export has a brace for each parent in place of its lines. The tour was rendered and looked at.

## Phase D: The timeline

### Task D1: Horizontal timeline

**Files:** new `layout/TimelineLayout.kt`, `MindMapManager.kt` (timelines laid out and fitted as tree charts are),
`model/MapStructure.kt` (summaries and floating topics below the axis), `MindMap.kt` (`drawTimelineAxis`,
`timelineAxis`), test `TimelineTest.kt`

- [x] The root's children are on a horizontal axis through the root, to its right in order; their branches grow up and
  down by turns, as org charts. Each is as far along as it needs to be clear of the topic before it on the axis and of
  the branch before it on its own side, so branches above and below interleave (the tour went from 5880 to 3960 px
  wide with that). A summary of them is below, centered on them, and takes no turn.
- [x] The axis is one line from the root through the middle of its topics, behind them, in the color and width of the
  line to the first; the topics sit on it, so no elbow joins them (unlike the plan's first idea).
- [x] Tests (6): on the axis in order, clear of each other; up and down by turns; branches on one side clear of each
  other; the axis's ends; a summary below; kept on the map. The tour was rendered and looked at.

## Phase E: Files, the app and the docs

### Task E1: In files

**Files:** `model/MapStructure.kt` (`fileName`, `fromFileName`, `fromXMindClass`), `io/MindMapViewFile.kt`,
`io/XMind.kt`, tests `MapStructureFilesTest.kt`, `XMindTest.kt`

- [x] `.ccmm` keeps the structure as `<mindmapview structure="org-chart-down">`; a mind map is written as before, and
  an unknown name is read as a mind map.
- [x] XMind: the `structureClass` (XMind 2020+) or `structure-class` (XMind 8) of the central topic of a map of one
  sheet. `org.xmind.ui.map.unbalanced` / `…clockwise` are confirmed by the repo's tests and samples; `…logic.right`,
  `…org-chart.down` and `…timeline.horizontal` by a third-party XMind guide found on the web; `…logic.left`,
  `…org-chart.up`, `…tree.right/left` and `…brace.right` follow their pattern but are **not confirmed**. Any name not
  known, as a fishbone, is the mind map, so a wrong guess only falls back.
- [x] Tests (5): every structure kept; nothing written for the mind map; an unknown one; XMind names; an XMind map read
  as an org chart, and the repo's sample as a mind map.

### Task E2: In the app

**Files:** new `menuBar/StructureMenu.kt` (`StructureSubmenu`, `StructurePicture`, `MapStructure.label`),
`menuBar/ViewMenu.kt`, `menuBar/EditMenu.kt` (`canMoveAround`), `dialog/PreferencesDialog.kt` ("New maps"),
`UserPreferences.kt` (`newMapStructure`), `MapThemePreference.kt` (`newMapTree`), `MindMapApp.kt`, `MindMapState.kt`
(moving round the root only in a mind map), test `StructureAppTest.kt`

- [x] View › Structure with a tick on the current one and a small drawing of each; Preferences › New maps for the
  structure new maps start in, kept with the app's preferences, not the map's.
- [x] Move Clockwise and Counterclockwise (menus and Ctrl+M) only in the mind map; the root's six add buttons only
  there too (A3). Move Up, Move Down, Indent and Outdent stay in every structure.
- [x] Tests (4): the submenu sets it and Ctrl+Z takes it back; Ctrl+M does nothing in a logic chart; new maps get the
  preference; the preference is kept. The submenu was rendered and looked at; a map opens in its saved structure (E1).

### Task E3: Docs

**Files:** `docs/user-manual/user-manual.html` (a "Structures" section in chapter 5, with a picture of each from
`docs/structures/`, a "New maps" row in the Preferences), PDF, `README.md`

- [x] What each structure is for, how to choose it, what it keeps, and how it is kept in each format.

---

## Out of scope / follow-ups

- A structure per branch, as XMind allows: done in `2026-11-18-branch-structures.md`.
- Fishbone and matrix structures.
- XMind's clockwise mind map, as its own order of the root's sides.
- Free positioning of attached topics.
