# Floating Topics Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A map can have **floating topics**, as in XMind: topics placed anywhere on the canvas, with no line to the
root, each with its own branch of subtopics. They hold side notes, parking-lot ideas and labels that belong to no
branch, and an arrow link can join them to any topic.

Today an XMind map's floating topics are read as ordinary children of the root, which moves them into a branch
and loses where they were. XMind keeps each in the central topic's `children.detached` list with a
`position {x, y}` relative to the central topic, as both XMind samples in the repo show (`docs/xmind_2026.xmind`,
"floating test" at (122, 444), and `desktop/71080cba-….xmind` at (0, 204)).

**Architecture:**

- **Data:** a floating topic is a child of the root, as Freeplane keeps its free nodes, with a flag and an
  **offset from the root**: `Tree.floatingOffsetOf(id): DpOffset?`, null for an attached topic. Being a child of the
  root keeps traversal, find, copy and paste, undo, the outline, levels and styles working with no change; only the
  layout, the lines and dragging treat it differently. The offsets are in the tree snapshot, so moves undo.
- **Layout:** the side layouts skip floating topics (`childrenOnSide`). After them, `arrangeTree` puts each at the
  root's center plus its offset and lays its branch out to its right with the right-hand layout. Its place moves with
  the root, so dragging the root takes them along, as in XMind.
- **Drawing:** no line from the root to a floating topic; its own branch has lines as any other. A floating topic's
  `sideOf` is `RIGHT`, so its children, the `+` button and new children go right.
- **Fitting:** `fittedRoot` and `measureOverflow` take the floating branches into account, so a floating topic above
  or left of the root is not drawn off the map, and Fit shows it.

**Build order:** phase A (data, layout, drawing, `.ccmm` files) shows the floating topics of a saved map; phase B adds
making, moving, detaching and attaching them; phase C reads and writes them in the other formats and shows them in
the outline; phase D documents them.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Where a floating topic is kept | A child of the root with an offset, after the attached children in `root.children`. Adding, detaching and pasting keep floating topics last, so the order of the attached children (and of the outline) is unchanged. |
| Its offset | From the root's center to its own center, in dp, so a map whose root is dragged or re-fitted keeps its floating topics where they were in relation to it. XMind's `position` is read as that offset (XMind's units are px at 100%, taken as dp). |
| Its branch | Laid out to the right of it, whichever side of the root it is on, as XMind does. |
| Its style | That of a Topic (level 1), as it is a child of the root; its children are Subtopics. XMind's own "floating topic" style is not modelled. |
| Making one | Double-click on blank map adds one there and starts typing its text (XMind's gesture). Also Edit › Add Floating Topic, which puts it below the map's lowest node, and the node menu's **Add Floating Topic** on blank map. No key. |
| Detaching a topic | The node menu's **Make Floating Topic** for any topic but the root: it keeps its branch, leaves its parent, and stays where it is drawn now. Alt+drag to blank map does the same where it is dropped. |
| Attaching one | Dragging a floating topic onto a topic makes it a child of that topic, as for any topic. The node menu's **Attach to Root** makes it an attached child of the root on the right. |
| Moving one | Dragging it on blank map moves it and its branch there, one undo step. Arrow keys still select by direction; Alt+arrow keys nudge it 10 dp (no Shift variant, as Alt+Shift+Up / Down move topics among their siblings). |
| Selecting from it | Ctrl+Left (select parent) does nothing from a floating topic, as it has no line to the root; Ctrl+Right goes to its first child. |
| Boundaries and summaries | A floating topic can't be in a boundary or summary with attached topics (they are grouped by side of the root); inside its own branch both work. |
| Arrow links | To and from floating topics, as any topic. |
| Outline view | Floating topics are rows at depth 1 after the attached topics, with a dashed bullet. Indent makes one an attached child of the row above. Outdenting a child of a floating topic makes it an **attached** topic of the root, on the right. |
| FreeMind / Freeplane | Written as ordinary children of the root for now: Freeplane's marker for a free node could not be confirmed (see C1). |
| OPML, Markdown, HTML | Written after the attached topics as ordinary branches. OPML marks them `_floating="true"` with `_x`, `_y`; Markdown and HTML lose that they float. |
| Exports | PNG, SVG and PDF draw them as on screen; the image bounds already come from all the nodes drawn. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Every change of floating topics is **one** undo step: adding with its typed text, a drag, a detach, an attach.
- Code that looks at `node.parentId == null` for "the root" or walks the root's children by side must be checked
  for floating topics: `childrenOnSide`, `drawLines`, `addButtonSides`, `LayoutSide.nearest` on drop, boundaries
  and summaries of the root, `selectParent`, `moveNodeAround` and `moveNodeToOtherSide` (which do nothing to a
  floating topic). **This is the easiest thing to miss.**
- Works in the web app (wasm): common code only.
- Maps without floating topics save byte for byte as before. In older versions, a map with them opens with them as
  ordinary children of the root.

## Review Focus

1. **Layout:** a floating topic stays at its offset when the map is re-laid out (a node added, text typed, the root
   dragged), and its branch never overlaps the attached branches by being pushed; it is where it was put.
2. **Fitting and panning:** a floating topic above or left of everything else is in view after Fit and in exports.
3. **Undo:** a drag, a detach and an attach are each one step, and undo puts the topic and its branch back exactly.
4. **Round trips:** `.ccmm` keeps the offsets; XMind's detached topics come in floating at their positions; Freeplane
   keeps them once confirmed; OPML keeps them.

---

## Phase A: Floating topics in the map

### Task A1: The data

**Files:** `data/Tree.kt`, `data/TreeSnapshot.kt`, `MindMapState.kt` (`CopiedNode`), test `FloatingTreeTest.kt`

- [x] `Tree.floatingOffsetOf(id): DpOffset?`, `isFloating(id)`, and `setFloating(id, offset: DpOffset?)`, which only
  takes a child of the root (it throws for another) and keeps floating topics last among the root's children; null
  makes it attached again, placed last among the attached ones. `moveNode` and `removeNode` drop the flag of a topic
  that leaves the root.
- [x] Offsets are in the snapshot, `restore` and `hasSameContentAs`; `CopiedNode` carries them, so a floating topic
  pasted onto the root floats again (24 dp off the original while that is still there), and pasted onto another
  topic is attached. `DpOffset(x, y)` is new, in `data`.
- [x] `sideOf` a floating topic or below it is `RIGHT`; the root's `rootChildSides` has no entry for it.
- [x] Tests (9): set and clear; only children of the root; order kept last; moved under another topic, it is no
  longer floating; snapshot and restore; copy and paste onto the root and onto a topic; side; a map without any
  is unchanged.

### Task A2: Layout and drawing

**Files:** `layout/MindMapLayoutManager.kt` (`childrenOnSide`, `arrangeFloating`, `floatingOverflow`, `movedBy`),
`MindMapManager.kt` (`arrangeTree`, `fittedRoot`), `MindMap.kt` (`drawLines`), `data/Tree.kt` (`rootGroupOf`),
`MindMapState.kt` (`fitScreen`, `selectParent`, boundary and summary grouping), tests `FloatingLayoutTest.kt`,
`FloatingDrawTest.kt`

- [x] The side layouts skip floating topics. `arrangeTree` then places each at the root plus its offset and arranges
  its branch with the right-hand manager.
- [x] `fittedRoot` moves the root right and down far enough that no floating branch reaches above or left of the
  map; `measureOverflow` uses it, so panning reaches them. Fit also covers the furthest node shown.
- [x] No line from the root to a floating topic. At the root, each floating topic is a group of its own for
  boundaries (`rootGroupOf`), and summaries leave floating topics out; Ctrl+Left does nothing from one.
- [x] Tests (7): placed at its offset; its branch to its right; attached topics laid out as if it weren't there;
  it stays when the map changes; it moves with a dragged root; one far above-left of the root is inside the map;
  no line to it in an SVG export (fails without the change). Fit needs a check by eye.

### Task A3: In `.ccmm` files

**Files:** `io/MindMapViewFile.kt`, test `FloatingFilesTest.kt`

- [x] `<node … side="right" floating="true" dx="120.5" dy="-80.0">` under the root; the attributes are left out for
  an attached topic, so other maps are written as before. `floating` on a node that is not a child of the root, or
  without both `dx` and `dy`, is ignored and the topic is attached.
- [x] Tests (4): round trip with its branch and order; no attributes written without floating topics; a misplaced
  attribute is ignored; one without an offset is attached.

## Phase B: Making and moving them

### Task B1: Adding, detaching and attaching in the state

**Files:** `MindMapState.kt` (`addFloatingNode(description, shape, color, textColor, at, …)`, `makeFloating()`,
`attachToRoot()`, `moveFloating(by)`), test `FloatingStateTest.kt`

- [x] `addFloatingNode` adds one at a point of the map (screen to tree), or below the lowest node when none is given,
  styled as a Topic; inside `addNodeForTextEdit { }` its text is typed at once.
- [x] `makeFloating()` detaches the selected topics, each keeping the place it is drawn at, as one step;
  `attachToRoot()` attaches the selected floating topics on the right, as one step.
- [x] `moveFloating(by: DpOffset)` nudges the selected floating topics, one step.
- [x] Tests (8): add at a point, then undo; add with no point; typed with `addNodeForTextEdit`, then cancel removes it;
  detach keeps the drawn place and the branch; detach several; attach; nudge; the root can't be detached.

### Task B2: Dragging

**Files:** `MindMapState.kt` (`onTouchUp(…, isDetaching)`), `MindMap.kt` (Alt on drop, double-click on blank map),
test `FloatingDragTest.kt`

- [x] A floating topic dropped on blank map moves there (its offset changes), one step; dropped on a topic, it
  becomes that topic's last child.
- [x] An attached topic dropped on blank map with Alt held becomes floating there; without Alt it is reordered among
  its siblings, as today.
- [x] `MindMap(onBlankDoubleClick = { point -> })`, which the app uses to add a floating topic there.
- [x] Tests (5): drag a floating topic on blank map, then undo; onto a topic; Alt-drag an attached topic to blank
  map; a plain drag still reorders; a double click on blank map calls `onBlankDoubleClick` with the point.

### Task B3: In the app

**Files:** `MindMapApp.kt` (`addFloatingToType`, node menu, double-click, Alt+arrows), `menuBar/EditMenu.kt`, test
`FloatingAppTest.kt`

- [x] Double-click on blank map adds a floating topic there and types its text, styled and numbered as a new Topic.
- [x] Edit › Add Floating Topic (with or without a selection) puts one below the map; the node menu has Make
  Floating Topic (not on the root or a floating topic) and Attach to Root (on a floating topic), and no Move
  Clockwise for a floating topic. A right click on blank map has no menu yet (a follow-up).
- [x] Alt+arrow keys nudge the selected floating topics 10 dp; with only attached topics selected they do nothing.
  No Shift variant: Alt+Shift+Up / Down already move topics among their siblings.
- [x] Tests (5): double-click on blank map, type, then undo; Edit menu, then Esc takes it away; Make Floating Topic
  and Attach to Root from the node menu; Alt+Right and Alt+Up nudge; Alt+Right on an attached topic does nothing.
  The shortcut rows come with the docs (D1), as the shortcut test keeps the dialog and the manual alike.

## Phase C: Other formats and the outline

### Task C1: XMind, Freeplane, OPML, Markdown, HTML

**Files:** `io/XMind.kt`, `io/Opml.kt`, tests `XMindTest.kt` (JVM), `FloatingFilesTest.kt`

- [x] XMind 2020+: the central topic's `children.detached` with `position`; XMind 8: `<topics type="detached">` with
  `<position svg:x svg:y>`. Detached topics of other topics, and those of a map of several sheets, stay ordinary
  children (this library floats only under the root). Both repo samples read with their floating topics at their
  positions.
- [ ] Freeplane: **not done, the format is not confirmed.** Freeplane 1.11's source writes `HGAP_QUANTITY` and
  `VSHIFT_QUANTITY` on `<node>` as `"<number> pt"` (`Quantity.toString()`), and its predefined style
  `defaultstyle.floating` hides the edge, but neither the source files read nor `doc/latestFreeplaneFeatures.mm` show
  what marks a node as free positioned (no `FreeNode` hook was found), nor what the offsets are measured from. Until
  that is confirmed, floating topics are written to FreeMind files as ordinary children of the root, as they were.
- [x] OPML writes `_floating="true"`, `_x` and `_y` (export only; this library reads no OPML). Markdown, HTML and
  FreeMind write floating topics after the attached ones, as the tree keeps them last.
- [x] Tests (5): the two XMind samples; an XMind 8 snippet; floating topics of a map of two sheets stay children;
  OPML attributes; the order in Markdown, HTML and FreeMind.

### Task C2: The outline

**Files:** `outline/OutlineRows.kt` (`isFloating`), `outline/OutlineRowView.kt` (dashed bullet), tests
`OutlineRowsTest.kt`, `OutlineMoveStateTest.kt` (the tree keeping floating topics last makes `indent`, `outdent` and
the moves right with no change of their own)

- [x] Floating topics are rows at depth 1 after the attached topics, with a dashed bullet.
- [x] Indenting a floating topic makes it an attached child of the row above; outdenting a child of a floating topic
  makes it an attached topic of the root on the right. Move up and down keep floating topics among themselves.
- [x] Tests (4): row order, depth and flag; indent; outdent; moves among floating topics only.

---

## Phase D: Documentation

### Task D1: Manual, README, shortcuts

**Files:** `docs/user-manual/user-manual.html` (a "Floating topics" section in chapter 5, Arranging the map), PDF,
`README.md`, `desktop/feature_tour.ccmm` (a floating topic with an arrow link to a branch), `KeyboardShortcuts.kt`

- [ ] What they are, making, moving, detaching and attaching them, and how they are kept in each format.
- [ ] The Alt+arrow and Alt+drag rows in the shortcut tables, in the dialog and the manual alike.

---

## Out of scope / follow-ups

- Floating topics in Freeplane files, once a file saved by Freeplane with a free node confirms the format.
- A right click on blank map offering Add Floating Topic there.
- Floating topics under topics other than the root (XMind allows them; they are read as ordinary children).
- XMind's own floating-topic style and callouts.
- Snapping or aligning floating topics to each other.
- Free positioning of attached topics (XMind's "free position" layout).
