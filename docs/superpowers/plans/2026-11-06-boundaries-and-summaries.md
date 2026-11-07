# Boundaries and Summaries Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two XMind features for grouping siblings.

- A **boundary** draws a rounded, lightly filled outline around a range of siblings and everything below them.
- A **summary** draws a bracket beside a range of siblings, pointing to a summary topic that sums them up.

XMind maps keep both when they are imported; today the importer drops boundaries and turns summary topics into
ordinary children.

**Architecture:**

- A **boundary** is decoration only. The tree keeps a list of `Boundary(parentId, firstChildId, lastChildId, ...)`,
  as it keeps arrow links. It is drawn in `drawMap`, behind the lines and nodes, so the PNG, SVG and PDF exports get
  it for free.
- A **summary topic is an ordinary node in its parent's `children`**, placed right after the range it sums up, and
  flagged in the tree with the first child of that range. This is how Freeplane stores them. About 80 places in the
  code walk `children` (measuring, export, find, copy and paste, undo snapshots, folding), and all of them keep working
  untouched. Only the layout and the drawing of lines treat a summary differently: it is not stacked with its
  siblings, but placed past them with a bracket instead of a line from the parent.
- Ranges are kept by **node ids, not indexes**, so they survive edits. After any change to the tree's structure, one
  `normalizeGroups()` step repairs or drops ranges whose nodes moved away.

**Build order:** two phases that can each ship on their own. Boundaries first, since they are smaller and touch
nothing structural; summaries second.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Adding one | Select one or more siblings, then Edit › Add Boundary (Ctrl+B) or Add Summary (Ctrl+]), or use the node's right-click menu. XMind uses the same keys. Ctrl+B is bold only while typing a node's text, so there is no clash. |
| Selected nodes under different parents | One boundary or summary for each group of siblings, grouped by parent and, under the root, by side. |
| A new summary topic | Named "Summary", styled as the range's level, and opened for typing at once with `addNodeForTextEdit`, so Escape takes it away again. |
| Changing or removing a boundary | Right-click inside it (not on a node) for its menu: Color, Line (Solid / Dashed), Fill (on / off), Remove. The innermost boundary wins. A left click and drag on it still pans the map, as on empty space. |
| Removing a summary | Remove its topic like any node, and the bracket goes with it. |
| All of a range's nodes removed or moved away | A boundary goes. A summary topic stays as an ordinary topic, so no text is lost. |
| Default look | The color of the branch's line from the root. A boundary is dashed, 1.5 dp, with its color at 12% as the fill. A bracket has the branch's line color and width. |
| Boundary titles | Out of scope (an XMind feature). The XMind importer drops the title. A follow-up. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Adding, changing or removing a boundary or summary is **one** undo step. Both are part of `TreeSnapshot` and of
  `hasSameContentAs`, so `isModified` sees them.
- Boundaries and brackets are drawn by `drawMap` only, never in a second drawing path, so screen and exports agree.
- Maps saved before this feature open unchanged. Maps saved with it open in older versions with the boundaries and
  brackets left out, and summary topics shown as ordinary topics.

## Review Focus

1. **Structural edits:** after cut, paste, drag to another parent, moving a branch to another side of the root,
   folding and undo, no range points at a node outside its parent. Pinned by `normalizeGroups` tests in A1 and B1.
2. **Layout:** two neighbouring siblings that each have a boundary never overlap, nested boundaries stay inside each
   other, and a summary topic never overlaps its range's deepest node, on the left and right of the root.
3. **XMind import:** `desktop/运维工作.xmind` and a hand-built fixture with boundaries and summaries import with the
   same groups XMind shows.
4. **Exports:** HTML, OPML and Markdown still export, with a summary topic as an ordinary item after its range.

---

## Phase A: Boundaries

### Task A1: Boundaries in the tree

**Files:** new `library/.../data/Boundary.kt`, `data/Tree.kt`, `data/TreeSnapshot.kt`, test `BoundaryTreeTest.kt`

- [x] `data class Boundary(id, parentId, childIds: Set<String>, color: Color? = null, style = DASHED, isFilled =
  true)`. A null color follows the branch, so the theme changes it.
  - As built, a boundary keeps the **set** of children it covers, not just the first and last. Once a first or last
    node is removed the old order is gone, so ids at the ends alone could not be repaired. `addBoundary` stores
    every child from the first to the last, so removing one at either end leaves the rest covered. A child inserted
    between them later is covered too.
- [x] `Tree.boundaries`, `addBoundary` (which replaces the boundary with the same id, as `addArrowLink` does),
  `removeBoundary`, and `boundaryChildIds(boundary)`: the siblings from first to last, in order, on that side of the
  root.
- [x] Repairs happen **on read**, not after each change. `boundaries` leaves out the children that are no longer the
  parent's, or not on its side, and leaves out boundaries with none left, or whose parent is no longer connected to
  the root. Removed nodes stay in the node map, so this check is needed. Because of this, no edit path can leave a
  stale range, and no `normalizeGroups()` hook into each mutation is needed. Phase B should use the same approach.
- [x] Boundaries are in the snapshot and in `hasSameContentAs`.
- [x] Tests (14): the range in order; a node inserted between; removing the first, last and middle; removing all;
  dragging out; a removed parent; one side of the root; moving to the other side; an invalid boundary; replacing by
  id; removing; snapshot and restore.

### Task A2: Boundaries in the state

**Files:** `MindMapState.kt`, test `BoundaryStateTest.kt`

- [x] `addBoundary()`: one boundary for each group of `selectedBranches` by parent and side, all as one step, and
  returns the ones added. The root alone gets none.
- [x] `updateBoundary(boundary)` and `removeBoundary(id)`, one step each, and an observable `state.boundaries`.
- [x] Copying a branch carries the boundaries inside it (those whose parent was copied), and pasting gives them new
  ids. A boundary around the copied nodes themselves is not pasted, as they are pasted under another parent.
- [x] Tests (10): grouping by parent and side, the root, nothing selected, one undo step, update and remove, updating
  a removed one, copy and paste, cut.

### Task A3: Where a boundary is

**Files:** new `library/.../layout/BoundaryGeometry.kt`, test `BoundaryGeometryTest.kt`

- [x] `Density.boundaryRect(tree, boundary)`, in the tree's pixels, is the union of the shown nodes of the range's
  subtrees (what is folded is left out), plus the rectangles of the boundaries nested inside them, with 10 dp of
  padding (`BOUNDARY_PADDING`). It is null when the parent is folded or hidden in a folded node.
- [x] Tests (8): one node; a subtree; the children between; a folded child; a folded parent; a hidden parent; nested
  boundaries strictly inside each other; on the left side.

### Task A4: Room for boundaries

**Files:** `layout/MindMapLayoutManager.kt`, test `BoundaryLayoutTest.kt`

- [x] In `arrangeChildren` and `measureHeight`, add `BOUNDARY_PADDING` (10 dp, not the 12 dp first planned) above the
  first and below the last sibling of each boundary, once for each boundary that starts or ends there. Since the gap
  equals the padding the outline is drawn at, an outline is exactly the sibling spacing (50 dp) from the next node
  or outline, however deeply boundaries are nested. A map without boundaries takes no gaps, so it is laid out
  exactly as before.
- [x] Tests (6): siblings without boundaries are 50 dp apart; one boundary moves its neighbours by its padding; the
  outlines of neighbours are 50 dp apart; three nested boundaries stay clear of the next branch's boundary; a
  boundary under the root moves the root's other children on that side; the siblings stay centered on their parent.

### Task A5: Drawing and the boundary menu

**Files:** `MindMap.kt`, `MindMapState.kt`, `MapImage.kt`, `layout/BoundaryGeometry.kt`, test `BoundaryDrawTest.kt`

- [x] `exportBounds` (in `MapImage.kt`) adds the boundary rectangles, so the PNG, SVG and PDF exports don't clip the
  outlines of boundaries at the map's edge.
- [x] `drawBoundaries` comes first in `drawMap`, behind the lines, each boundary before those within it
  (`drawnBoundaries`). It draws a rounded rectangle (corner radius 12 dp), filled at 12% when `isFilled`, with a
  1.5 dp outline, dashed (6 dp dash, 4 dp gap) or solid. The colour is `color`, or else the colour of the line into
  the boundary's first child, which is its branch's colour.
- [x] `state.findBoundary(position)` gives the innermost boundary at a point. `MindMap(onBoundaryMenu = { boundary,
  position -> })` is called on a right click where there is neither a node nor an arrow link. A left click or drag
  there still pans the map.
- [x] Tests (7): the outline's colour is in a PNG export; the fill is there when filled and gone when not; the
  export's bounds hold the whole outline; a right click in the padding asks for the boundary's menu; a right click on
  a node inside asks for the node's menu; the innermost boundary is found; a folded parent draws none.

### Task A6: Boundaries in files

**Files:** `io/MindMapViewFile.kt`, `io/FreeMind.kt`, `io/XMind.kt`, KDoc of `HtmlOutline`, `Opml` and `Markdown`,
tests `BoundaryFilesTest.kt` (common) and `XMindTest.kt` (JVM)

- [x] `.mmv`: `<boundary id parent children color style filled/>` elements after the `<arrowlink>` elements, with
  `children` the covered ids separated by spaces. Writing and reading back gives the same map. A boundary around nodes
  that aren't in the file is left out, and an older file without boundaries reads as before.
- [x] FreeMind: a `<cloud COLOR=...>` on a node is read as a one-node boundary with a solid line, and a cloud on the
  root is ignored. A boundary is written as a `<cloud>` on each of its nodes, the first boundary winning where several
  cover a node. FreeMind's cloud colour is a fill and its default is pale grey, so a pale cloud (luminance 0.8 or
  more) is read as following the branch colour.
- [x] XMind JSON (`boundaries: [{range: "(0,2)"}]`) and XML (`<boundaries><boundary range="(0,2)"/>`) read ranges as
  indexes among the attached children; a range past them is cut short. `master` becomes a one-node boundary around
  the topic, except on the root, where it's dropped. Titles and nonsense ranges are dropped. The KDoc says
  boundaries are kept.
- [x] HTML, OPML and Markdown leave boundaries out; their KDoc says so, and a test confirms their output is unchanged.
- Neither sample `.xmind` in `desktop/` has boundaries or summaries, so hand-built fixtures in `XMindTest` cover
  this. Review Focus 3 should be read that way.

### Task A7: Boundaries in the app

**Files:** `MindMapApp.kt`, `menuBar/EditMenu.kt`, `KeyboardShortcuts.kt`, the user manual and its PDF,
`README.md`, tests `BoundaryAppTest.kt` and the shared `TestPlatform.kt`

- [x] Edit › Add Boundary and the node menu's Add Boundary (not on the root), with Ctrl+B. While a node's text is
  typed, the text field gets Ctrl+B first, so it makes the text bold instead.
- [x] A boundary menu at the right-clicked point, built like the arrow link menu: Solid / Dashed, Filled, Branch
  Color (ticked while it has no colour of its own), Color (the arrow link colour row), and Remove Boundary.
- [x] Shortcuts dialog and manual (a "Boundaries" section at the end of chapter 5, *Arranging the map*, plus the
  shortcut and mouse tables), the PDF regenerated, and the README updated.
- [x] App tests (3): Ctrl+B around two selected topics (the one between is covered too), then undo; Ctrl+B while
  typing makes the text bold and adds no boundary; the menu makes a boundary solid, then removes it. The fake
  platform moved to `TestPlatform.kt` for both app tests, and the app's map has the test tag `MIND_MAP_TAG`.

---

## Phase B: Summaries

### Task B1: Summaries in the tree

**Files:** `data/Tree.kt`, `data/TreeSnapshot.kt`, test `SummaryTreeTest.kt`

- [ ] `summaryStarts: Map<summaryId, firstChildId>`. Add `isSummary(id)`, and `summaryChildIds(id)`: the siblings
  from the first one up to the summary, leaving out other summaries, on its side of the root.
- [ ] `normalizeGroups()` also repairs summaries:
  - A first node that is gone gives way to the next sibling still before the summary.
  - A summary with no nodes left before it loses its flag and stays as an ordinary topic.
  - A summary moved to another parent loses its flag.
- [ ] Add the flags to the snapshot and to `hasSameContentAs`.
- [ ] Tests: the range, a summary after a summary, removing the covered nodes, dragging the summary away, and undo.

### Task B2: Summaries in the state

**Files:** `MindMapState.kt`, test `SummaryStateTest.kt`

- [ ] `addSummaryNode(description, shape, ...)`: for each group of selected siblings, inserts a node right after
  the group's last node, flags it, and records one step. It works inside `addNodeForTextEdit`, so the app can open it
  for typing.
- [ ] Copying a branch keeps the summaries inside it. Copying a summary topic on its own pastes an ordinary topic.
- [ ] Tests: the position, one undo step, typing it with `addNodeForTextEdit` then Escape, copy and paste.

### Task B3: Laying out summaries

**Files:** `layout/MindMapLayoutManager.kt`, test `SummaryLayoutTest.kt`

- [ ] `arrangeChildren` stacks only the non-summary children. Then each summary goes past its range:
  - It is centered on the range's top-to-bottom extent.
  - Its x is past the outermost edge of the range's subtrees, plus a 20 dp bracket and 24 dp of space.
  - Its own children are arranged outward from it.
- [ ] Height measures take the larger of the range's height and the summary subtree's height, so a tall summary
  pushes the next siblings down instead of overlapping them.
- [ ] Include summary subtrees in `reachAbove`, `reachBelow`, `leftMost` and `fittedRoot`, so the map stays on
  screen.
- [ ] Tests: the summary is past the range's deepest node and centered on the range, mirrored on the left side and on
  a slanted root side; a tall summary pushes the next sibling down; a map without summaries lays out as before.

### Task B4: Drawing summaries

**Files:** `MindMap.kt` (`drawLines`, a new `drawSummaryBrackets`), `NodeLines.kt`, test `SummaryDrawTest.kt`

- [ ] `forEachNodeWithLine` skips summary topics, so no line runs from the parent.
- [ ] Draw a curly bracket from the top to the bottom of the range's extent, its point toward the summary topic,
  with a short line from the point to the topic, in the branch's line color and width.
- [ ] Folding the parent hides the bracket. Folding the summary hides its children only.
- [ ] Tests: the bracket's color in a PNG export at the expected x; no parent line to the summary.

### Task B5: Summaries in files

**Files:** `io/MindMapViewFile.kt`, `io/FreeMind.kt`, `io/XMind.kt`, tests

- [ ] `.mmv`: `summary-first="<id>"` on a summary topic's `<node>`.
- [ ] Freeplane: before writing code, check the exact form against a map saved by Freeplane 1.11. Freeplane marks
  the summary node with `<hook NAME="SummaryNode"/>` and the first node of its group with
  `<hook NAME="FirstGroupNode"/>`. Read both; write both. FreeMind itself ignores them and shows an ordinary topic.
- [ ] XMind JSON (`summaries: [{range, topicId}]` with `children.summary`) and XML (`<summaries>` with
  `<topics type="summary">`): put each summary topic right after its range and flag it, instead of adding it at the
  end as today.
- [ ] HTML, OPML and Markdown: a summary is an ordinary item after its range. Add a test so that stays true.

### Task B6: Summaries in the app

**Files:** `MindMapApp.kt`, `menuBar/EditMenu.kt`, `KeyboardShortcuts.kt`, manual and PDF, `README.md`, test
`SummaryAppTest.kt`

- [ ] Edit › Add Summary and the node menu's Add Summary, with Ctrl+]. It adds "Summary" and opens it for typing. On
  it, Tab adds a child and Enter adds an ordinary sibling after it.
- [ ] Shortcuts, a "Summaries" section in the manual next to Boundaries, the PDF, and the README.
- [ ] App test: select two siblings, Ctrl+], type, Enter; undo removes it in one step.

---

## Out of scope / follow-ups

- Boundary titles.
- A boundary or summary over nodes under different parents.
- Summaries in the Android `MindMapView` View.
- XMind's floating topics and callouts. The importer keeps adding them as ordinary children.
- Regenerating `docs/feature_tour.png` with the new features. This needs the app on a real display, as in task 7 of
  the inline-editing plan.
