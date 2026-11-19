# Tree Table and Grid Structures Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The two XMind structures MindMapView still lacks, both drawn as tables of nested cells rather than with
lines:
- A **tree table** shows a branch as nested blocks. The owner is a header across the top, and each topic is a cell
  to the left of its children's cells, as tall as they are together. The deepest topics line up in the last column,
  so branches of different depths can be compared, as in a list of items with prices.
- A **grid** arranges the owner's main topics in a set number of columns, wrapping into rows. Each main topic heads
  a cell, and its subtopics are stacked below it in that cell.

As with the other structures, each can be the map's or a branch's (`2026-11-18-branch-structures.md`), they nest, and
only the drawing changes.

Both are layouts of their own, laid out from an owner as the tree chart, timeline, fishbone and matrix are:
- They are measured as a box by `OwnedBranches`.
- Their cells are drawn in `drawMap`, so the screen and the PNG, SVG and PDF exports all show them.
- They share the matrix's way of drawing a table: a list of cell rectangles drawn by one function.

**What XMind's own pages say, and what they don't.**
- **Tree table** ([introduction][tt-blog], [benefits][tt-benefits]):
  - "The central topic is displayed at the top level".
  - Topics "are displayed in nested blocks", and "the tree branches [keep] growing in the blocks".
  - The lowest-level topics are right-aligned "for comparison across branches with varying depths".
  - The layout adjusts by itself, and "no need to manually merge or unmerge cells".
- **Grid** ([beta notes][grid-blog], [tutorial][grid-tutorial]):
  - It arranges "topics in rows and columns".
  - You "decide how many columns you may need at the very beginning".
  - Cells can be aligned and colored.
  - Branch topics may have other structures.

They do **not** give:
- where the central topic sits;
- whether main topics are rows or columns;
- how cells span, align or are bordered;
- what happens to summaries;
- the class names in a file (the tree table's is commonly seen as `org.xmind.ui.treetable`, the grid's is unknown);
- where a grid keeps its column count.

This plan proposes the shapes below, all **to be confirmed with files saved from XMind in phase E**. A wrong guess
changes only the drawing, and an unknown name still reads as the mind map.

[tt-blog]: https://xmind.com/blog/introducing-tree-table-new-structure-in-xmind
[tt-benefits]: https://xmind.com/blog/benefits-of-tree-table-better-visual-and-quicker-representation
[grid-blog]: https://xmind.com/blog/xmind-beta-updates-grid-structure
[grid-tutorial]: https://xmind.com/academy/xmind-grid-structure-tutorial

**Architecture:**

- **Structures:**
  - `MapStructure` gains `TREE_TABLE` (`tree-table`) and `GRID` (`grid`), each usable for the map or for a branch.
  - `isTable` covers them and the two matrices, for drawing.
- **Grid columns:** a grid has a column count, kept per owner:
  - In `Tree`: `gridColumnsOf(ownerId)` and `setGridColumns(ownerId, count)`, in the snapshot so that undo covers it.
  - In `.ccmm`: a `columns` attribute on the map or on the owner's node.
  - With none set, the grid is one row with a column per main topic, and never more than 4 columns.
- **Tree table:** `TreeTableLayout`, laid out from an owner.
  - There is one column per depth below the owner.
  - Each shown leaf, or folded topic, is a row.
  - A topic's cell spans the rows of its leaves.
  - A leaf above the deepest level has its cell stretch to the table's right edge, with the topic at the right end of
    the cell, so the leaves line up.
  - A column is as wide as its widest topic, and a row as tall as its tallest leaf.
  - The owner is a header cell across the whole width, above the table.
  - A topic with a branch structure of its own is a leaf whose cell holds its branch's box.
- **Grid:** `GridLayout`, laid out from an owner.
  - The owner is a header cell across the whole width, above the table.
  - The main topics fill the cells in reading order, `columns` to a row.
  - In each cell, the main topic sits at the top, with a rule under it, and its children are stacked below it. Each
    child's branch is laid out as a logic chart →, or in its own branch structure, measured as a box (the
    matrix's `Reach`).
  - All columns are as wide as the widest cell, so it reads as a grid, and each row is as tall as its tallest cell.
- **Drawing:** `TableCells`, produced by each table layout's `cellsOf(owner)`, holds:
  - the cells' rectangles;
  - the heavier rules, under a header and between a grid cell's main topic and its children.

  One `drawTables` draws them for matrices, tree tables and grids, replacing the grid-line part of `drawMatrices`.
  The matrix keeps its header text. Lines between topics that share a table are not drawn: the cells show the
  nesting.
- **Shared:**
  - Both go through `OwnedBranches.arrangeBelow`, `MindMapManager.arrangeWhole` for a map of that structure,
    `structureOf` for buttons, and `placedDirectionOf`.
  - Their topics grow RIGHT. In a grid, a main topic's children grow DOWN within its cell.

**Build order:**
- Phase A: the two structures in the model and files, plus the grid's column count, laid out as the logic chart until
  their layouts come.
- Phase B: the shared table drawing, moving the matrix onto it.
- Phase C: the tree table.
- Phase D: the grid.
- Phase E: the app and the docs.
- Phase F: checks against XMind's own files.

Each phase can ship on its own.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Tree table's owner | A header cell across the full width, at the top: "displayed at the top level". |
| Tree table's direction | Children to the right of their parent, each cell spanning its descendants' rows: "nested blocks", "arranged horizontally, with indentation". There is no turned (top-down) variant. |
| "Lowest level right-aligned" | A leaf above the deepest level stretches its cell to the right edge, with the topic at the right of the cell, so all leaves end in the last column. To confirm in F1. |
| Vertical alignment in a spanning cell | Top, as an outline reads, with the matrix's padding. |
| Grid's owner | A header cell across the full width, at the top, as in the tree table. |
| Grid's main topics | Cells in reading order, a set number to a row, each holding its subtopics stacked below it. Rows of main topics wrap, as in a card layout. |
| Grid's column count | Per owner, default "all in one row, at most 4", set from 1 to 8 in the app (Edit › Grid Columns, and a topic's menu for a branch). Changing it is one undo step. |
| Grid column widths | Equal, the widest cell's, so that it reads as a grid. Row heights by their tallest cell. |
| Cells and borders | Thin lines in the map's line color, as the matrix's (`MATRIX_LINE_WIDTH`), heavier under a header. Topics keep their own shapes inside cells. |
| Lines | None between topics of the same table. A branch below a grid cell's subtopic, or one with its own structure, draws its own lines. |
| Summaries | Tree table: a summary is a column past the table's right edge, its cell spanning the rows of the topics it sums. Grid: a summary of main topics is a row below the grid across its width; a summary inside a cell is as in a logic chart. |
| Boundaries | Around topics within one cell or nested cells of one parent, drawn as they are now. Not across grid cells. |
| Folded topics | A folded topic is a leaf: its cell stretches as any leaf's does. |
| Floating topics | As in the other structures: where they were put, their branch a logic chart →. |
| Drag to reorder | Tree table: siblings by y. Grid: main topics by reading order (row, then x), a cell's topics by y. Dropping onto a topic makes it a child, as today. |
| Keyboard | Arrow keys select by screen direction, so no change. |
| Exports | PNG, SVG and PDF as on screen, through `drawMap`. Outlines unchanged. |
| Files | `.ccmm`: `tree-table` and `grid`, with `columns="n"` on the grid's owner. XMind: `org.xmind.ui.treetable` read as the tree table now. The grid's class name and column count are unknown, so not read until F1. FreeMind/Freeplane: not written. |
| Older versions | Read the unknown names as the mind map (a map) or as their parent's (a branch), as they already do, and ignore `columns`. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions, comments only for *why*.
- Maps that use neither structure lay out and draw pixel-identically. The golden images in `ThemeDrawingTest` must
  not change. `matrix-project.png` may change only if moving the matrix onto `drawTables` changes a pixel, and if so,
  the change must be understood before the image is replaced.
- Each task: its own branch, committed, merged into `main` with `--ff-only`, and the branch deleted.
- Run `:library:jvmTest`, `:desktop:jvmTest` and the wasm build before each merge.
- Do not touch the untracked `desktop/software development.ccmm`.

## Review Focus

- The tree table's leaf stretching: is "the leaves line up in the last column" the right reading of XMind's
  "rightward alignment of lowest-level topics"?
- The grid's default column count, and whether its column widths should be equal or each column's own.
- Whether table topics should keep their shapes, or fill their cells as plain text, as a spreadsheet would.

## Phase A: In the model and files

### Task A1: Two more structures and the grid's columns

**Files:** `model/MapStructure.kt`, `data/Tree.kt`, `io/MindMapViewFile.kt`, `io/XMind.kt`, `MindMapState.kt`;
tests `MapStructureTest`, `MapStructureFilesTest`, `BranchStructureFilesTest`, `XMindTest`

- [x] `TREE_TABLE("tree-table", "org.xmind.ui.treetable")`, `GRID("grid", null)`, and `isTable` (with the
  matrices). `directionOf`: RIGHT for both until phases C and D lay them out; the grid's main topics' children grow
  DOWN from D1. Until then, both are laid out as the logic chart →. Both are in the menus already, with their
  pictures and labels, as each structure has been since it was added.
- [x] Tests that use `tree-table` / `org.xmind.ui.treetable` as an unknown name switch to one that stays unknown
  (`bubble` / `org.xmind.ui.bubble`).
- [x] `Tree.gridColumnsOf`, `setGridColumns`, kept in the snapshot. A removed owner drops its count.
  `state.setGridColumns(count)` sets the grid a selected topic owns or is in, and is one undo step. Cut and paste
  keep the count.
- [x] `.ccmm` writes and reads `columns` on the owner's node, the root's for the map. Tests: round trip, and that an older file without
  it opens unchanged.
- [x] `XMind` reads `org.xmind.ui.treetable` as a map's or a branch's tree table.

## Phase B: One way of drawing tables

### Task B1: `TableCells` and `drawTables`

**Files:** `layout/TableCells.kt` (new), `layout/MatrixLayout.kt`, `MindMap.kt`; tests `MatrixDrawTest`,
`ThemeDrawingTest`

- [ ] `TableCells(rects, rules)` in dp. `MatrixGrid.toCells()` gives the matrix's grid lines and its heavier header
  rules as cells.
- [ ] `drawTables` draws every table owner's cells: the matrix's through it now. Header text stays in
  `drawMatrices`.
- [ ] `isTableLine(tree, id)` replaces `isMatrixLine`, skipping a line between two topics of one table.
- [ ] Tests: the matrix draws as before, and `matrix-project.png` is unchanged or, if not, understood.

## Phase C: Tree table

### Task C1: The tree table layout

**Files:** `layout/TreeTableLayout.kt` (new), `MindMapManager.kt`; tests `TreeTableLayoutTest` (new)

- [ ] Columns by depth, leaves as rows, each topic's cell spanning its leaves' rows, leaves stretched to the last
  column with the topic at the cell's right, and the owner as a header across the top.
- [ ] A topic with its own branch structure is a leaf holding its branch's box. A folded topic is a leaf.
- [ ] Summaries in a column past the right edge, spanning the rows they sum.
- [ ] `MindMapManager`: a map in `TREE_TABLE` is laid out whole. A branch's is dispatched from `arrangeBelow`, and
  its box comes from its cells.
- [ ] Tests:
  - the header spans the table;
  - siblings are one below the other in order;
  - a parent's cell spans its children;
  - leaves of different depths end in the same column;
  - a folded topic is a leaf;
  - a tree table branch is clear of its siblings.

### Task C2: Drawing the tree table

**Files:** `layout/TreeTableLayout.kt`, `MindMap.kt`; tests `TreeTableDrawTest` (new), `ThemeDrawingTest`

- [ ] `cellsOf(owner)` gives the cells, and `drawTables` draws them. No lines between its topics.
- [ ] A golden image, `tree-table-feature-tour.png`, of a small list of costs by area, with leaves at two depths.

## Phase D: Grid

### Task D1: The grid layout

**Files:** `layout/GridLayout.kt` (new), `MindMapManager.kt`; tests `GridLayoutTest` (new)

- [ ] The owner as a header across the top. The main topics in reading order, `gridColumnsOf(owner)` to a row, with
  equal column widths. Each cell has its main topic at the top and its children stacked below, each branch laid out
  → or in its own structure.
- [ ] A summary of main topics is a row below the grid, across its width.
- [ ] Laid out whole for a map, from `arrangeBelow` for a branch, with its box from its cells.
- [ ] Tests:
  - the default count is one row of at most 4;
  - setting 2 columns wraps 5 topics into 3 rows;
  - a cell's children are stacked below its main topic;
  - the columns are equal;
  - a grid branch is clear of its siblings.

### Task D2: Drawing the grid

**Files:** `layout/GridLayout.kt`, `MindMap.kt`; tests `GridDrawTest` (new), `ThemeDrawingTest`

- [ ] `cellsOf(owner)`: each cell, with a rule under its main topic. No lines from a main topic to its children.
- [ ] A golden image, `grid-feature-tour.png`, of a plan in three columns.

## Phase E: The app and the docs

### Task E1: In the app

**Files:** desktop `menuBar/StructureMenu.kt`, `MindMapApp.kt`, the topic menu; tests `StructureAppTest`

- [x] "Tree Table" and "Grid" in View › Structure, Edit › Branch Structure, a topic's menu and Preferences › New
  maps, each with a picture (done in A1).
- [ ] Edit › Grid Columns ▸ 1 to 8, and "As Many as Fit",, shown when the map or the selected branch is a grid.
- [ ] Tests: each set from the menus, and that the column count is shown and set.

### Task E2: Docs

**Files:** `docs/user-manual/user-manual.html`, `README.md`, `desktop/manual/MindMapView-User-Manual.pdf`,
`docs/structures/`

- [ ] The manual's "Structures" section: a picture and a paragraph for each, and grid columns. Rebuild the PDF with
  `docs/user-manual/make-pdf.sh`.
- [ ] The README: the two structures, `gridColumnsOf` / `setGridColumns`, and that XMind's grid is not yet read.

## Phase F: Against XMind's files

### Task F1: XMind names and shapes

**Files:** `model/MapStructure.kt`, `io/XMind.kt`, the layouts, tests `XMindTest.kt`, samples in
`library/src/jvmTest/resources/`

- [ ] **Needs files saved from XMind:** a tree table with leaves at different depths, a grid of 3 columns with 5
  main topics, and a mind map with a tree table branch and a grid branch.
- [ ] Confirm the tree table's class name. Find the grid's class name and where it keeps its column count, then read
  both.
- [ ] Open each in MindMapView and compare with XMind: the header, the spanning, the leaves' alignment, the grid's
  order and widths. Adjust the layouts where they differ.
- [ ] Tests: each sample opens in its structure, with its column count.

---

## Out of scope / follow-ups

- Topics filling their cells as plain text, spreadsheet-like, in place of their shapes.
- Per-cell colors and alignment, which XMind's grid allows.
- A tree table turned top-down.
- Sums or other formulas over a tree table's leaves.
