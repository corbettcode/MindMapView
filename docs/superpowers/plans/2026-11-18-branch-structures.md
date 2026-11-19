# Branch Structures Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A topic can have a **structure of its own**, as in XMind: a mind map whose "Timeline" branch is a timeline
and whose "Team" branch is an org chart. The topic's children and everything below them are laid out in that
structure; the topic itself stays where its parent's structure puts it. The topics below a topic with a structure
follow it until one of them has its own. As with the map's structure, only the drawing changes: switching a branch
back to its parent's structure gives the map as it was.

Today the structure is the map's (`tree.structure`) and every question about it asks the map: `tree.directionOf(id)`
goes to `tree.structure.directionOf`, and `MindMapManager`, `MindMap.kt` (lines, braces, the timeline's axis, add
buttons, the tree chart's elbows), `SummaryGeometry`, `Tree.moveAmongSiblings` and `MindMapState` all test
`tree.structure == …`. Many of them also test "is a child of the root" where they mean "is a child of the topic the
structure is laid out from": the tree chart's columns, the timeline's axis, the curves from the root of a tree chart,
summaries of columns. The layouts measure a branch as **centered across** on its topic (`measureChildHeight`, placed
at `start + childCross / 2`) and **starting at its topic along** the way it grows, which stops being true when a
child's branch is an org chart inside a logic chart.

**Architecture:**

- **Owner:** `tree.branchStructureOf(id): MapStructure?` is the structure set on a topic, or null to follow its parent.
  `tree.structureOwnerOf(id)` is the nearest topic, starting at `id`, with one set, or the root; `tree.structureOf(id)`
  is that owner's structure (the root's is `tree.structure`). `structureOf(id)` is the structure **id's children** are
  laid out in, as `directionOf(id)` is already the way they grow.
- **Structures relative to their owner:** `MapStructure.directionOf`, `isStackedAlongX` and `summaryDirectionOf` take
  the owner where they now take the root: a topic is a "column" of a tree chart, or "on the axis" of a timeline, when
  its parent is the owner. The mind map stays the map's only: it is not offered for a branch, and a branch whose
  parent is in a mind map and has no structure of its own grows left or right by the root's sides, as now.
- **A branch is a box:** each layout measures a child's branch as a box around the child's center — how far it
  reaches back and forward along the way the parent's branch grows, and before and after across it — instead of one
  length across centered on the child. A child with the same structure as its parent has today's box exactly (back =
  its half size, across centered), so nothing moves. A child that owns another structure is laid out by that structure
  around a probe center, its box read off the result, and the branch moved into place once its parent's layout has
  placed it: back past its parent's edge by the gap and its box's back reach, across by its box's before and after.
- **One recursion:** `MindMapManager.arrangeBranchOf(node)` lays out `node`'s children in `structureOf(node.id)` —
  the side layouts for a direction, `TreeChartLayout` or `TimelineLayout` from `node` instead of from the root — and
  the layouts call it for each child that owns a structure, instead of going on with their own recursion.
- **Geometry by owner:** every `tree.structure == X` test in drawing and editing becomes a test of the structure the
  thing is in: a line by `structureOf(parent)`, a brace by `structureOf(parent)`, a timeline axis for each owner that
  is a timeline, an add button and fold ring by `structureOf(node)`, a drop among siblings by `structureOf(parent)`.
  Only the root's six sides, slanted lines, "Move Clockwise" and the root's six add buttons stay with `tree.structure`.

**Build order:** phase A adds the branch structure to the tree with no visible change; phase B turns the layouts into
boxes with no visible change and composes them, starting with directions (logic and org charts in a branch); phase C
puts tree charts, brace maps and timelines in a branch; phase D files, the app and the docs. Each phase can ship on its
own.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Which topics | Any topic but the root, whose structure is the map's (View › Structure). Floating topics too: a floating topic's own structure lays out its branch. Summary topics too, as they have branches. |
| Which structures | The eight that are not the mind map: logic → / ←, org ↓ / ↑, tree → / ←, brace map, timeline. "Same as parent" clears it. The mind map needs a root's six sides, so it is not offered for a branch, as XMind doesn't. |
| Where it is chosen | Edit › Branch Structure and the node menu: a submenu as View › Structure's, with "Same as Parent" first and a tick on what the selected topics have; it sets it on every selected topic but the root, as one change. View › Structure stays the map's. |
| What a branch keeps | Its structure whatever its parent's: a map switched from the mind map to an org chart keeps its timeline branch a timeline. Moving a topic (drag, cut and paste, indent) takes its structure with it; copying copies it. |
| Direction in a branch | As chosen, whatever the parent's direction: a timeline in a branch on the left of a mind map still runs to the right of its topic, its box pushing it clear of the parent, as XMind does. "← / →" are the user's to pick. |
| Where the branch's topic is | Where its parent's structure puts it: a column topic of a tree chart branch is placed by the parent's layout, then its children are laid out around it. The parent's layout leaves room for the whole box, back and across, so nothing overlaps. |
| Lines | The line into the owner is the parent's (a curve in a logic chart, an elbow in a tree chart); lines out of it are its structure's. A brace map branch draws braces below its owner only; a timeline branch draws its axis from its owner. |
| Summaries and boundaries | A summary is in the structure of the topic whose children it is about (`structureOf(parent)`), as now relative to the owner: of a timeline branch's axis topics, below them. A boundary around the owner holds its whole box. |
| Drag to reorder | Among siblings by the parent's structure. A dragged owner is laid out by its own structure while it moves (`arrangeBranch`). |
| Keyboard | Arrow keys select by screen direction, so no change. |
| Undo | Setting or clearing branch structures is one change; the snapshot keeps them, `hasSameContentAs` compares them. |
| Exports | PNG, SVG and PDF as on screen; outlines unchanged. |
| Files | `.ccmm`: `<node … structure="timeline">`, left out when it follows its parent, so other maps are written as before; an unknown name, or `mind-map`, is read as none. XMind: the `structureClass` (`structure-class` in XMind 8) of every topic but the central one, already read into `Topic.structureClass` and unused; `org.xmind.ui.map.*` and unknown classes (fishbone, matrix) as none. FreeMind/Freeplane: not written. Older versions of the app ignore the attribute and draw the branch in its parent's structure. |
| Big maps | A box is measured once per layout pass and kept by id for the pass, as an owner's probe layout is a whole layout of its branch; nested owners are measured from the inside out. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Phases A and B1 must not change a single pixel of a map with no branch structures**, in any of the nine map
  structures: `ThemeDrawingTest.classicFeatureTourLooksAsBefore`, `featureTourAsAnOrgChartLooksAsBefore` and every
  layout test of the library guard that. Run the suites with `--rerun`.
- Works in the web app (wasm): common code only.

## Review Focus

1. **No change without branch structures:** the box refactor keeps every position for maps of every structure.
2. **Every "child of the root" that means "child of the owner"** found: columns, axis topics, root curves of a tree
   chart, summary placement and direction, add buttons on the spine, `isStackedAlongX`, `rootGroupOf` (which stays the
   map root's: only it has sides).
3. **Nesting:** an org chart in a logic chart in a mind map, a timeline in a tree chart column, an owner below an
   owner — each laid out clear of its siblings and its parent, rendered and looked at.
4. **Switching back:** setting a branch structure and clearing it gives the map as it was, with one undo step each way;
   switching the map's structure keeps the branches'.

---

## Phase A: The branch structure in the tree

### Task A1: Owners

**Files:** `data/Tree.kt` (`branchStructures`, `branchStructureOf`, `setBranchStructure`, `structureOwnerOf`,
`structureOf`, `directionOf`, `isStackedAlongX`, `summaryDirectionOf`), `data/TreeSnapshot.kt`, `model/MapStructure.kt`
(an `owner` in place of the root, the root by default), `layout/SummaryGeometry.kt`, `MindMapState.kt`
(`setBranchStructure(structure)`, the clipboard), test `BranchStructureTest.kt`

- [x] `branchStructures: Map<String, MapStructure>` kept beside the floating offsets; removed with the node, carried
  by copy, cut and paste (`CopiedNode.branchStructure`), in the snapshot and `hasSameContentAs`.
  `setBranchStructure(id, null)` clears; the root and `MIND_MAP` throw.
- [x] `MapStructure.directionOf`, `isStackedAlongX` and `summaryDirectionOf` take an `owner` (the root by default) and
  read "child of the root" as "child of `owner`"; the timeline's turns count the owner's attached children.
  `tree.directionOf(id)` passes `structureOwnerOf(id)`; `tree.isStackedAlongX(id)` and `tree.summaryDirectionOf(id)`
  ask the structure of the node's parent, as they are about it among its siblings. Maps without branch structures
  get the same answers as before.
- [x] `state.setBranchStructure(structure)` sets it on the selected topics but the root, as one change that re-lays
  out the map; the mind map is refused. Until phase B the layouts don't follow it everywhere, but nothing in the app
  or the files sets one yet.
- [x] Tests (6): none by default; set, undo, redo, clear; the owner of a topic and below a nested owner; directions
  by owner (an org chart branch in a mind map grows down, the mind map around it by sides, a timeline branch up and
  down by turns, stacking by the parent's structure); kept through the map's structure change and cut and paste;
  refused for the root and the mind map. All 695 library and 251 desktop tests pass on a full rerun, the golden
  images among them.

## Phase B: Boxes, and directions in a branch

### Task B1: The layouts measure boxes

**Files:** new `layout/BranchBox.kt` (`BranchBox(left, right, top, bottom)` on the screen, and `OwnedBranches`, which
measures and lays out a branch with a structure of its own), `layout/MindMapLayoutManager.kt` (`ownedBranches`,
stacking and main placement by box, `crossReach`, `arrangeBelow`), `layout/TimelineLayout.kt` (spans by
`crossReach`), test `BranchBoxTest.kt`

- [x] A side layout with `ownedBranches` reads the box of a child with a structure of its own, turned to its
  direction: it stacks it by how far it reaches before and after it across, places it the gap and its back reach past
  its parent, and hands what is below it to `arrangeBelow`. The same in `outerEdge` (summaries), `backMost` (the
  overflow past the top and left), summaries with a structure of their own, and floating topics. Any other branch goes
  through the arithmetic it went through before, so nothing moves.
- [x] The timeline's axis topics take their spans from `crossReach`, which is today's half each side for a branch in
  its parent's structure. The tree chart is left to C1, which places columns by their boxes.
- [x] No change: all 698 library and 251 desktop tests pass on a full rerun, both golden images among them.
- [x] Tests (3): with the hook, a map without branch structures is laid out as without it; a child with a box as an
  org chart row's is pushed its back reach past its parent and its siblings are clear of the box; a layout growing
  down reads the box turned.

### Task B2: Directions in a branch

**Files:** `MindMapManager.kt` (the side layouts' `OwnedBranches`: a box measured by laying the branch out around its
topic and kept for the layout under way, `inPass`; `layoutManagerFor` by `structureOf`), `data/Tree.kt`
(`placedDirectionOf`), `MindMap.kt` (lines by `placedDirectionOf`, add buttons and fold rings by `structureOf`), test
`BranchDirectionTest.kt`

- [x] A side layout meeting a topic with a structure of its own asks the manager for its box, which lays its branch
  out around it and reads how far it reaches, once per layout (`arrangeTree`, `arrangeBranch`, `measureOverflow`,
  `measureHeight`); placed, the topic's branch is laid out by `layoutManagerFor(it)`, which follows `structureOf`.
- [x] Logic and org charts in a branch, nested too: an org chart ↓ under a topic on the right of a mind map has its
  children in a row below it, the topic pushed right of its parent by the row's back half, its siblings clear of the
  whole row.
- [x] The line into a topic leaves its parent as its parent's structure places it (`placedDirectionOf`), and lines out
  of it as its own grows; add buttons and fold rings by the structure of the topic's children. Braces, the timeline's
  axis and tree chart elbows still go by the map's structure, until phase C.
- [x] Tests (5): an org chart branch in a mind map, a row below its topic clear of its parent and siblings; a logic
  chart ← in a right branch reaching back from its topic, which is pushed clear; an org chart ↑ under an org chart ↓;
  placed and own directions; clearing it gives every position back. All 703 library and 251 desktop tests pass on a
  full rerun. The tour was rendered with branches as org charts ↓ and ↑ and a logic chart ← in the mind map, and with
  a logic chart → and an org chart ↑ in an org chart, and looked at: nothing overlaps. The lines from the root cross
  the rows that reach back towards it, as in XMind.

## Phase C: Tree charts, brace maps and timelines in a branch

### Task C1: Tree chart in a branch

**Files:** `layout/TreeChartLayout.kt` (`arrange` from any topic, `ownedBranches`, `placeOwned`),
`layout/TimelineLayout.kt` (`arrange` from any topic), `MindMapManager.kt` (`arrangeBelow` by structure,
`arrangeBranch`), `MindMap.kt` (elbows, spine and buttons by the structure the topic is in), test
`BranchTreeChartTest.kt`

- [x] A tree chart branch: the owner's shown children in columns in a row below it, centered on it, rows indented;
  measured as a box like any other. The owner's lines to its columns are curves down (`placedDirectionOf`), as from a
  tree chart's root.
- [x] A row or column of a tree chart with a structure of its own is placed so its whole branch has its near edge
  where the row's would be and its top at the row's top, and is laid out in its structure.
- [x] Every `parentId == null` / `!= null` test for columns and rows asks for the owner instead (`isRow`, the add
  button on the spine); the elbow's and spine's side by `structureOf`. A dragged tree chart or timeline owner lays
  its branch out from where it is.
- [x] Tests (5): columns below the owner, clear of its parent and siblings; rows indented with the elbow on the
  spine; a summary of columns below them; stacking by x for columns, y for rows; a logic chart column in a tree chart
  map. All 708 library and 251 desktop tests pass on a full rerun. The tour was rendered with a tree chart → and ←
  branch in the mind map, and a logic chart and an org chart column in a tree chart, and looked at.

### Task C2: Brace map and timeline in a branch

**Files:** `MindMap.kt` (`drawBraces` for every topic in a brace map, `drawTimelineAxis` and `timelineAxis` for each
topic a timeline is laid out from, lines left out by the structure the parent is in), `layout/TimelineLayout.kt`
(laid out from any topic, done with C1), test `BranchBraceAndTimelineTest.kt`

- [x] A topic in a brace map, the map's or a branch's, joins its children by a brace, and lines elsewhere: a brace
  map with a logic chart branch draws lines in that branch.
- [x] A timeline branch has its axis from its topic to its right, through its children, in the line of the first;
  their branches up and down by turns; measured as a box, so its parent's layout keeps it clear of its siblings.
- [x] Tests (4): braces only in a brace branch of a logic chart (by the paths of an SVG export); lines in a logic
  branch of a brace map; a timeline branch's axis ends and turns; a timeline branch clear of its siblings. All 712
  library and 251 desktop tests pass on a full rerun. The tour was rendered with a brace and a timeline branch in the
  mind map, and a logic chart and a timeline branch in a brace map, and looked at.

## Phase D: Files, the app and the docs

### Task D1: In files

**Files:** `model/MapStructure.kt` (`branchFromFileName`, `branchFromXMindClass`), `io/MindMapViewFile.kt`,
`io/XMind.kt`, tests `BranchStructureFilesTest.kt`, `XMindTest.kt`

- [x] `.ccmm`: `structure="…"` on a node with one; read back, an unknown name or `mind-map` as none, so a file of a
  later version with a structure this one has no layout for opens with that branch as its parent's.
- [x] XMind: the `structureClass` (`structure-class` in XMind 8) of every topic but the central one, the mind map and
  unknown ones (fishbone, matrix) as none. A map of several sheets is read as one map with a branch for each, so each
  sheet's structure is its branch's. **Not confirmed with a file from XMind:** none of the repo's three samples has a
  structure below the central topic. XMind keeps `structureClass` on a topic, not a sheet, so it is read the same way
  there; a sample with one would confirm it.
- [x] Tests (5): every branch structure kept; nothing written without one; an unknown one and the mind map read as
  none; XMind 2020 and XMind 8 topics with a structure, the mind map and a fishbone; the sheets of a map. All 717
  library and 251 desktop tests pass on a full rerun.

### Task D2: In the app

**Files:** `menuBar/StructureMenu.kt` (`BranchStructureSubmenu`, both submenus through one `StructureChoices`),
`menuBar/EditMenu.kt`, `MindMapApp.kt` (`NodeMenu`, `selectedBranchStructures`), test `StructureAppTest.kt`

- [x] Edit › Branch Structure, after Add Floating Topic, and the same in the node menu: "Same as Parent" first, then
  the eight structures a branch can have, each with its picture; a tick on the one the selected topics share, none
  when they differ. In the Edit menu it is disabled with only the root selected, and the node menu leaves it out for
  the root, as it does its other items that are not the root's.
- [x] Tests (3): the submenu sets it on two selected topics and Ctrl+Z takes it back, the map's own structure
  untouched; "Same as Parent" clears it; disabled for the root. All 717 library and 254 desktop tests pass on a full
  rerun, and the web app builds. The submenu was rendered and looked at.

### Task D3: Docs

**Files:** `docs/user-manual/user-manual.html` ("Structures" in chapter 5 gets "A structure for a branch", with a
picture in `docs/structures/`), PDF, `README.md` (`setBranchStructure`, `structureOf`)

- [ ] What it does, how it is chosen and cleared, how it nests, and how it is kept in each format.

---

## Out of scope / follow-ups

- Fishbone and matrix structures, for the map or a branch.
- The mind map as a branch structure.
- Mirroring a branch's structure by the side of the root it is on (a timeline running left in a left branch).
