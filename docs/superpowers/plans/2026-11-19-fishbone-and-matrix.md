# Fishbone and Matrix Structures Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The two XMind structures MindMapView doesn't have yet. A **fishbone** (an Ishikawa diagram) has the effect
at the head and its causes on bones slanting off a spine, for root-cause analysis. A **matrix** lays a map out as a
table: the root's topics are rows, their subtopics sit in cells, and the cells' columns come from the subtopics'
labels, as XMind's matrix does. As with the other structures, each can be the map's or a branch's
(`2026-11-18-branch-structures.md`), and only the drawing changes.

Neither is a turned version of the side layout, as the logic and org charts are. They are layouts of their own, as
the tree chart and the timeline are, on the same footing: laid out from an owner (`TreeChartLayout.arrange(owner)`
is the pattern), measured as a box by `OwnedBranches`, with their own lines drawn in `drawMap` so that the screen
and the PNG, SVG and PDF exports all show them.

**What XMind's own pages say, and what they don't.** XMind's blog and help pages ([matrix and fishbone][blog],
[spreadsheet help][help]) confirm:
- a fishbone "starts from the effect", each branch is a cause, sub-branches dig into "the why" behind it, and three
  levels are best;
- in a matrix, "the main topics become one of the headers and the labels group subtopics", a cell holds several
  topics, and the cell topics are logic charts unless given another structure.

They do **not** give the fishbone's geometry (which side the head is on, the bones' angle, how sub-branches sit) or
either structure's class name in a file. This plan takes the classic Ishikawa shape and the class names XMind is
known to use (`org.xmind.ui.fishbone.leftHeaded` / `.rightHeaded`, `org.xmind.ui.spreadsheet`), all **to be
confirmed with sample files saved from XMind before E1**. A wrong guess reads as the mind map, as today.

[blog]: https://xmind.com/blog/matrix-fishbone
[help]: https://xmind-help.github.io/en/spreadsheet.html

**Architecture:**

- **Structures:** `MapStructure` gains `FISHBONE_LEFT` (head on the left, the causes to its right), `FISHBONE_RIGHT`
  (head on the right, the classic Ishikawa), `MATRIX_ROWS` (the root's topics are rows, labels are columns) and
  `MATRIX_COLUMNS` (the same table turned: the root's topics are columns, labels are rows). Each is a map's or a
  branch's; the mind map stays the only one a branch can't have.
- **Fishbone:** `FishboneLayout`, laid out from an owner. The spine runs from the head (the owner) away from it. Its
  children are bones, above and below the spine by turns, slanting back towards the tail at a fixed angle, each topic
  at the far end of its bone. A bone's children sit along the bone, joined to it by **horizontal** ribs; their
  children slant again, and so on by turns. Bones are spaced along the spine so each is clear of the one before it on
  its side, interleaving above and below as the timeline does. Lines are straight segments whatever the line style,
  in the line's color and width.
- **Matrix:** `MatrixLayout`, laid out from an owner. The rows are the owner's children; each cell topic goes in the
  column of its label: the first value of its `Labels` attribute, which is where `XMind` puts labels. Columns are the
  labels in the order they are first met, row by row, and a column with no header holds the topics without a label.
  A cell stacks its topics, each with its branch laid out as a logic chart → (or its own branch structure), measured
  as a box. A column is as wide as its widest cell and a row as tall as its tallest. `drawMatrix` draws the grid and
  the column headers' text; the owner sits in the top-left corner cell and the row topics in the first column.
- **Shared:** both go through `OwnedBranches.arrangeBelow`, `MindMapManager.arrangeWhole` for a map of that structure,
  `structureOf` for lines and buttons, and `placedDirectionOf` for the way their topics are joined.

**Build order:** phase A adds the four structures to the model and files, laid out as the logic chart until their
layouts come; phase B the fishbone; phase C the matrix; phase D the app and the docs; phase E checks against XMind's
own files. Each phase can ship on its own.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Fishbone heads | Both: head left (causes to the right) and head right (the classic Ishikawa). XMind has both. |
| Bone angle | 60° from the spine, slanting back towards the tail. Ribs off a bone horizontal, their children slanted again. Past the third level, a branch is laid out as a logic chart away from the head, as XMind advises against deeper fishbones. |
| Fishbone lines | Straight, whatever the line style: a spine from the head, a bone into each main topic, a rib into each topic below. The line width and color still apply. The spine is drawn in the line of the first bone, as the timeline's axis is. |
| Matrix columns | By label: the first value of a cell topic's `Labels` attribute. This matches XMind and what `XMind` already reads; in MindMapView a label is set by giving a topic a "Labels" attribute. A topic without one is in the column with no header, last. |
| Matrix rows and columns turned | `MATRIX_COLUMNS` makes the root's topics the column headers and the labels the rows, as XMind's two matrix kinds do. Same layout, the axes swapped. |
| Matrix headers | The column headers are drawn text, not topics: renaming one is changing that label on the topics in the column. Editing headers in place is a follow-up. |
| Matrix grid | Thin lines in the line color of the map between rows and columns, a slightly heavier one under the headers. The owner is in the top-left corner cell. |
| Deeper topics in a matrix | A cell topic's branch is a logic chart → inside its cell, or its own branch structure. A matrix inside a cell is allowed but not advised, as XMind says. |
| Summaries | Fishbone: a summary of bones is placed past the tail, centered on the spine; a summary of ribs past them, as in a logic chart. Matrix: a summary of rows is a row of its own below them, spanning the table; of cell topics, inside the cell as in a logic chart. |
| Boundaries | Around a bone with its ribs, or around topics in a cell; not across cells. |
| Floating topics | As in the other structures: where they were put, their branch a logic chart →. |
| Drag to reorder | Fishbone: bones by their place along the spine; ribs by their place along the bone. Matrix: rows by y (by x for `MATRIX_COLUMNS`); a cell topic dropped in another cell of its row takes that column's label, one undo step. |
| Keyboard | Arrow keys select by screen direction, so no change. |
| Exports | PNG, SVG and PDF as on screen, through `drawMap`. Outlines unchanged. Markdown and OPML don't keep labels as columns. |
| Files | `.ccmm`: `fishbone-left`, `fishbone-right`, `matrix-rows`, `matrix-columns`, for the map and for branches. XMind: `org.xmind.ui.fishbone.leftHeaded` / `.rightHeaded` and `org.xmind.ui.spreadsheet` (XMind 8) are the guesses; XMind 2020's matrix classes are unknown. All to confirm in E1. FreeMind/Freeplane: not written. |
| Older versions | Read the unknown names as the mind map (a map) or as their parent's (a branch), as they already do. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **No change to any existing structure:** every layout test and both golden images pass on a full rerun after each
  task. Run the suites with `--rerun`.
- Works in the web app (wasm): common code only.

## Review Focus

1. **Fishbone spacing:** bones above and below interleave without overlap, the longest rib clear of the next bone, at
   two and three levels; the head clear of the first bones.
2. **Matrix sizing:** a column as wide as its widest cell, with a cell's branches of several levels; empty cells; a
   row without any labeled topics; a topic moved to another column by its label.
3. **As a branch:** each new structure as a branch of a mind map, measured as a box, clear of its siblings.
4. **XMind's files:** a fishbone and a matrix saved from XMind open as they look there.

---

## Phase A: In the model and files

### Task A1: Four more structures

**Files:** `model/MapStructure.kt` (the four entries, `directionOf`, `isFishbone`, `isMatrix`), desktop
`menuBar/StructureMenu.kt` (labels and pictures), tests `MapStructureTest.kt`, `MapStructureFilesTest.kt`,
`BranchStructureFilesTest.kt`, `XMindTest.kt`

- [x] `FISHBONE_LEFT("fishbone-left", "org.xmind.ui.fishbone.leftHeaded")`, `FISHBONE_RIGHT(…rightHeaded)`,
  `MATRIX_ROWS("matrix-rows", "org.xmind.ui.spreadsheet")`, `MATRIX_COLUMNS("matrix-columns",
  "org.xmind.ui.spreadsheet.column")`, the XMind names marked in the code as not yet confirmed. `directionOf`: a
  fishbone's spine grows away from the head (right for head left, left for head right); a matrix's rows grow right,
  its columns down. Until phases B and C the side layout lays them out that way, as a logic chart (an org chart for
  the columns).
- [x] The `.ccmm` names come with the entries, for the map and for branches. View › Structure, Edit › Branch
  Structure and Preferences › New maps list them as "Fishbone, Head Left / Right" and "Matrix, Rows / Columns", each
  with a picture; the menu was rendered and looked at.
- [x] Tests: their directions and kinds; their XMind names; kept in files for the map and a branch (the existing tests
  over every structure). The tests that used a fishbone as an unknown structure now use XMind's tree table. All 718
  library and 254 desktop tests pass on a full rerun, and the web app builds.

## Phase B: Fishbone

### Task B1: The fishbone layout

**Files:** new `layout/FishboneLayout.kt`, `MindMapManager.kt` (laid out from the owner, `arrangeWhole` for a map,
`arrangeBranch`), `model/MapStructure.kt` (`fishboneDirectionOf`), `layout/MindMapLayoutManager.kt` (`backReach`),
test `FishboneLayoutTest.kt`

- [x] The head is the owner; the spine runs from its far edge away from it. Causes by turns above and below, each at
  the far end of its bone at 60° back towards the tail; each bone as far along as it needs to be clear of the last on
  its side, its joint at least 30 dp past the last joint on the spine, so bones above and below interleave.
- [x] Ribs: a cause's children along its bone, the first nearest the cause, each joined to the bone by a horizontal rib
  long enough for the bone, slanting on past it, to be clear of the rib's whole branch. **Changed from the plan:** the
  topics below a rib are a logic chart growing towards the tail, not slanted again. Three levels of slants crowd the
  bones, and XMind advises against deep fishbones; this keeps every level readable.
- [x] Directions (`fishboneDirectionOf`): the head and the topics below the ribs grow towards the tail; a cause's ribs
  grow from the cause towards the spine (down above it, up below it), so drop order is by x for causes and by y for
  ribs, and add buttons sit at the bone's end of a cause. A summary of causes is below the whole fishbone, centered on
  them, its branch growing down; a summary of ribs is past them towards the tail.
- [x] A cause with a structure of its own has its branch laid out in that past a short bone; a dragged cause has its
  ribs laid out from where it is; floating topics grow towards the tail; folded causes have a short bone.
- [x] Tests (6): causes by turns, in order; each clear of the one before on its side and of the head; ribs along their
  bone, past it, the first nearest the cause; head right mirrors head left; a folded cause; a fishbone branch clear of
  its siblings. All 724 library and 254 desktop tests pass on a full rerun.

### Task B2: Drawing the fishbone

**Files:** `MindMap.kt` (`drawFishbone`: the spine, the bones and the ribs, lines skipped for topics in a fishbone, add
buttons and fold rings at the end of a bone or rib), `layout/SummaryGeometry.kt`, test `FishboneDrawTest.kt`, the
golden `fishbone-feature-tour.png`

- [ ] The spine from the head's edge to past the last bone; a bone from the spine to its topic; a rib from the bone to
  its topic's near edge; all straight, in each topic's line color and width.
- [ ] Summaries and boundaries as in the Decisions; drop order along the spine and along the bone.
- [ ] Tests: the spine's ends; a bone's ends; an SVG export has the bones in place of the lines; a golden image of the
  tour as a fishbone, rendered and looked at, head left and right.

## Phase C: Matrix

### Task C1: The matrix layout

**Files:** new `layout/MatrixLayout.kt`, `MindMapManager.kt`, `data/Tree.kt` (`labelOf(id)`, the first value of the
`Labels` attribute), test `MatrixLayoutTest.kt`

- [ ] Columns are the labels of the owner's grandchildren in the order first met, then the column with no header. Each
  cell stacks its topics, each laid out as a logic chart → (or its branch structure) and measured as a box. Column
  widths and row heights fit the largest cell; padding inside cells; the owner in the corner, the rows in the first
  column, centered in their cells.
- [ ] `MATRIX_COLUMNS` swaps the axes.
- [ ] Measured as a box when it is a branch's; a folded row is one line high.
- [ ] Tests: cells in the right columns by label; unlabeled topics in the last column; widths and heights fit;
  several topics in one cell stacked; the turned matrix; a matrix branch clear of its siblings; folding.

### Task C2: Drawing the matrix

**Files:** `MindMap.kt` (`drawMatrix`: the grid and the column headers' text, no lines from rows to cells, buttons),
`MindMapState.kt` (a cell topic dropped in another column takes its label), test `MatrixDrawTest.kt`, the golden
`matrix-feature-tour.png`

- [ ] The grid in the map's line color; the headers' text in the topic text style; lines inside cells as logic charts.
- [ ] Dropping a cell topic in another cell of its row sets its label to that column's, as one change; between rows it
  moves to that row, as dropping on a topic does.
- [ ] Tests: the grid's lines; the headers' text in an SVG export; a drop between columns sets the label and undoes;
  a golden image of a small project matrix, rendered and looked at.

## Phase D: The app and the docs

### Task D1: In the app

**Files:** desktop `menuBar/StructureMenu.kt` (pictures), the edit dialog's attributes (a hint that "Labels" sets a
matrix's column), test `StructureAppTest.kt`

- [ ] Both structures in View › Structure and Edit › Branch Structure with their pictures; Preferences › New maps
  offers them too.
- [ ] Tests: the submenus set them; undo.

### Task D2: Docs

**Files:** `docs/user-manual/user-manual.html` (the two structures in "Structures", with pictures in
`docs/structures/`; how labels make a matrix's columns), PDF, `README.md`

- [ ] What each is for, how a matrix takes its columns from labels, how both are kept in files and read from XMind.

## Phase E: Against XMind's files

### Task E1: XMind names and shapes

**Files:** `model/MapStructure.kt`, `io/XMind.kt`, tests `XMindTest.kt`, samples in `library/src/jvmTest/resources/`

- [ ] **Needs files saved from XMind:** a fishbone (head left and right), a matrix (both kinds), and a mind map with a
  fishbone branch. Confirm or correct the four class names, and that XMind keeps labels on cell topics as this plan
  reads them.
- [ ] Open each in MindMapView and compare with XMind: the head's side, the bones' angle and the ribs; the matrix's
  columns and their order. Adjust the layouts where they differ.
- [ ] Tests: each sample opens in its structure, with its cells in their columns.

---

## Out of scope / follow-ups

- Editing a matrix's column headers in place, and adding an empty column.
- XMind's tree table and grid structures.
- XMind's clockwise mind map, as its own order of the root's sides.
- Free positioning of attached topics.
