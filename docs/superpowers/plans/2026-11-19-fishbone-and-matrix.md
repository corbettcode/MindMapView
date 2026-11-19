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

**Files:** `MindMap.kt` (`drawFishbones`, `fishboneLines`, the lines of bones and ribs left out of `drawLines`), test
`FishboneDrawTest.kt`, `ThemeDrawingTest.featureTourAsAFishboneLooksAsBefore` with the golden
`fishbone-feature-tour.png`

- [x] For each fishbone head, the map's root or a topic with a fishbone of its own: the spine from the head's edge to
  40 dp past the last joint, in the line of the first cause; a bone from its joint to its cause's near edge; a rib from
  the bone straight across to its topic's near edge; all straight, in each topic's line color and width, whatever the
  line style, and faded by a task filter as lines are. The topics below a rib have ordinary lines.
- [x] Add buttons and fold rings needed no change: a cause's are at the bone's end, as its ribs grow towards the spine
  (B1), and a rib's towards the tail. Summary brackets and drop order follow the directions of B1.
- [x] Tests (3 + the golden): the spine's ends; a bone's ends at 60° and a rib's across to its topic; an SVG export has
  the spine, bones and ribs in place of the lines. The tour was rendered as a fishbone head left and head right, and
  with its Lines branch as a fishbone in the mind map, and looked at. All 728 library and 254 desktop tests pass on a
  full rerun.

## Phase C: Matrix

### Task C1: The matrix layout

**Files:** new `layout/MatrixLayout.kt` (`MatrixLayout`, and `matrixGrid`, which the drawing in C2 shares),
`MindMapManager.kt`, `model/MapStructure.kt` (`matrixDirectionOf`), `data/Tree.kt` (`labelOf`, `setLabel`),
`data/NodeAttribute.kt` (`LABELS_ATTRIBUTE`, shared with `XMind`), `layout/MeasureTextSize.kt` (`header`,
`headerSize`), tests `MatrixLayoutTest.kt`, `MapStructureTest.kt`

- [x] `matrixGrid` works out the table from the sizes of what is in it: the owner in the corner; its rows (columns) in
  the first cell of each; the labels as headers of the other way, in the order first met row by row, then the one
  with no header; each cell's topics stacked, each with its branch laid out as a logic chart → and measured. Each
  column is as wide, and each row as tall, as the largest in it with 10 dp padding, at least 40 dp. The corner cell is
  centered on the owner; topics are at the top left of their cells, row and column topics centered in theirs.
- [x] Headers are measured as the app draws them, the text of a topic on one line, once text can be measured, and
  guessed at before.
- [x] `MATRIX_COLUMNS` swaps the axes. Directions: the rows of a matrix by rows grow right and stack by y; the
  columns of one by columns grow down and stack by x; the topics in cells grow right; a summary of the rows is below
  the table (right of it, for columns), its branch growing away from it. Summaries of topics in cells are placed in
  the cells by their own labels, as XMind does with any topic.
- [x] A folded row has no cells; a matrix branch is measured as a box, clear of its siblings.
- [x] Tests (6): a label is the first of a node's labels, and setting one keeps the others; topics in the columns of
  their labels in the order first met, the unlabeled last; rows one below another, a cell's topics stacked, the row
  as tall as them; the turned table; a folded row; a matrix branch clear of its siblings. The phase A test of a
  matrix by columns now checks a column and a cell topic apart. All 734 library and 254 desktop tests pass on a full
  rerun.

### Task C2: Drawing the matrix

**Files:** `MindMap.kt` (`drawMatrices`, the lines to rows and cells left out), `MindMapManager.kt` (`matrixGridOf`,
the grid in a matrix branch's box), `layout/MatrixLayout.kt` (`gridOf`, `reachOf`), `MindMapState.kt` (a dropped cell
topic takes its column's label), `data/Tree.kt` (`isMatrixCell`), `layout/MeasureTextSize.kt` (the label left out of
a cell topic's attribute table), tests `MatrixDrawTest.kt`, `ThemeDrawingTest.projectMatrixLooksAsBefore` with the
golden `matrix-project.png`

- [x] The grid is worked out once, by `matrixGridOf`, from where the topics are, for the drawing and for drops alike.
  Lines between the rows and columns in the map's line color, twice as wide past the headers; each label's text in its
  header, as a topic's text on one line. No lines from the matrix's topic to its rows, nor from a row to its cells;
  a cell topic's own branch has its lines.
- [x] A topic in a cell doesn't show its label in its table of attributes, as its column's header shows it. **Added to
  the plan:** without it every cell repeated its header.
- [x] A cell topic dropped on no other topic, in another column (row, for a matrix by columns) takes that column's
  label, or loses its labels in the column with no header, as one change with its new place among its siblings.
  Dropped on a topic, it moves there as any topic does.
- [x] A matrix branch's box takes in its grid, which reaches past the topics at its edges.
- [x] Tests (3 + the golden): the grid has a column for each label and a row for each row, around all the topics, a
  topic in its cell; an SVG export has the headers' text and no "Labels" table; a drop in another column sets the
  label, and undo takes it back. A project matrix was rendered by rows, by columns and as a branch of a mind map, and
  looked at. All 738 library and 254 desktop tests pass on a full rerun.

## Phase D: The app and the docs

### Task D1: In the app

**Files:** desktop `component/AttributesEditor.kt` (`hint`), `dialog/EditNodeDialog.kt` (`attributeHint`),
`MindMapApp.kt` (`MATRIX_LABEL_HINT`, "+ Labels" offered), library `data/Tree.kt` (`isMatrixCell` public), test
`StructureAppTest.kt`

- [x] View › Structure, Edit › Branch Structure and Preferences › New maps already list the four, each with its
  picture (A1).
- [x] Editing a topic in a matrix cell, the attributes say that the first of its Labels is its column, and offer a
  "+ Labels" row when no topic has labels yet.
- [x] Tests (3): View › Structure sets each of the four and undo takes the last back; the hint and "+ Labels" for a
  topic in a matrix; no hint outside one. All 738 library and 257 desktop tests pass on a full rerun, and the web app
  builds.

### Task D2: Docs

**Files:** `docs/user-manual/user-manual.html` (the two in "Structures", with `docs/structures/fishbone-right.png` and
`matrix-rows.png`, and a paragraph on how labels make a matrix's columns), `desktop/manual/MindMapView-User-Manual.pdf`,
`README.md`, pictures `docs/structures/fishbone-left.png`, `matrix-columns.png` for later use

- [x] What each is for and how it is laid out, how a matrix takes its columns from labels and how a topic is moved to
  another column, what the edit dialog offers, and that XMind's names are not yet confirmed. The pictures are a
  cause-and-effect fishbone for a late delivery and the project table; the PDF was printed again and its pages looked
  at.

## Phase E: Against XMind's files

### Task E1: XMind names and shapes

**Files:** `model/MapStructure.kt`, `io/XMind.kt`, tests `XMindTest.kt`, samples in `library/src/jvmTest/resources/`

- [x] Confirm or correct the four class names, and that XMind keeps labels on cell topics as this plan reads them.
  Confirmed against jan-bar/xmind (`model.go`), a library that reads and writes XMind's files: the four names are
  as read, `spreadsheet` being rows and `spreadsheet.column` columns, and labels are a topic's `labels` array
  (`<labels><label>` in XMind 8). Not yet against files saved from XMind itself.
- [ ] **Needs files saved from XMind.** Open each in MindMapView and compare with XMind: the head's side, the bones' angle and the ribs; the matrix's
  columns and their order. Adjust the layouts where they differ.
- [x] Tests: each sample opens in its structure, with its cells in their columns.

---

## Out of scope / follow-ups

- Editing a matrix's column headers in place, and adding an empty column.
- XMind's tree table and grid structures.
- XMind's clockwise mind map, as its own order of the root's sides.
- Free positioning of attached topics.
