# Outliner View Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The map can be seen and typed as an **outline**: one indented row per topic, as in XMind's Outliner,
Workflowy or Logseq. It is the same map, not a copy, so a change in either view shows in the other at once, and undo,
find, the filter, the menus and the edit dialog all work in it as they do on the map.

Today a map can be exported as OPML or Markdown and read back, but there is no live outline. Typing a long list of
ideas on the map means a lot of Tab and Enter while the layout moves under the cursor; in an outline it is plain
typing.

**Architecture:**

- **One state:** the outline is a new library composable, `MindMapOutline(state)`, that reads and changes the same
  `MindMapState` as `MindMap`. Selection, folding, undo, the task filter and `textEditNodeId` are shared, so there is
  nothing to sync.
- **Rows:** a pure function `outlineRows(tree)` gives the visible topics in preorder with their depth, which a
  `LazyColumn` draws. Folded branches are left out, as on the map.
- **Moves:** indent, outdent, move up and move down are new `MindMapState` operations built on one new
  `Tree.moveNode(id, parentId, index, side)`. They act on the selection, each as one undo step, so the Edit menu can
  offer them on the map too.
- **Typing:** a row being typed in shows a rich text editor in the row and uses the inline-editing API that exists
  (`startTextEdit`, `previewText`, `commitTextEdit`, `addNodeForTextEdit`), so a typed topic is one undo step, as on
  the map.
- **Keys:** a pure `outlineKeyAction(...)` in the desktop app, beside `inlineKeyAction`, decides what each key does
  while the outline has the focus. The app sends keys to one or the other by which view has the focus.

**Build order:** phase A (moves and a read-only outline you can click, select and fold in) can ship on its own;
phase B adds typing and the outliner keys; phase C adds the app's view modes, menus and docs.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Where it shows | View › **Map**, **Outliner**, **Map and Outliner** (radio items). The third splits the window with the outline on the left and a divider that can be dragged. The choice is remembered in `UserPreferences`, not in the map file. |
| Shortcut | Ctrl+Shift+O cycles Map → Outliner → Map and Outliner. It is free today (only Ctrl+O is used); check again before taking it. |
| Order of the root's children | `root.children` order, which is the file order, whatever side each is on, as XMind's Outliner does. Moving up or down at that level changes that order and keeps the side, so on the map the topic may not seem to move when its neighbour is on another side. |
| Side of a topic outdented to the root | The side of the branch it came from (`sideOf` before the move). A new root topic added with Enter takes its sibling's side. |
| Enter while typing | Ends the typing and starts a new sibling below, so a list is typed without stopping (outliner habit, unlike the map, where Enter only ends the typing). Enter on a just-added row that is still empty removes it and ends the typing. Esc ends the typing. |
| Tab / Shift+Tab | Indent / outdent the selected topics, whether typing or not; typing goes on in the same topic. On the map, Tab keeps adding a child. |
| Move up / down | Alt+Shift+Up / Alt+Shift+Down, in both views, also in Edit › Move Up / Move Down. Indent and Outdent are also in the Edit menu for the map, without a key. |
| Backspace on an empty row | While typing, a topic whose text is empty and that has no children is removed, and typing goes on at the end of the row above, as one undo step. |
| Arrow keys | Up/Down go to the row above or below; Left folds the topic, or goes to its parent when it is folded already or has no children; Right unfolds it, or goes to its first child. Ctrl+Up/Down still go between siblings. |
| What a row shows | Fold chevron, a bullet in the topic's fill colour, emoji and task badges, then the text with its bold, italic, underline and links (no images, which show as a 🖼 sign). A note icon and an attribute count after the text, which open the same flyovers as on the map. The due date in grey, red when overdue. |
| Summaries, boundaries, arrow links | A summary is its own row, after the siblings it is about, in italics with a `}` before it. Boundaries and arrow links are not shown. Moving a topic out of a summary's or boundary's range follows the tree's rules, which already drop it from them. |
| Filter and find | Rows that don't match the task filter are faded, as on the map. Find selects matches, and the outline scrolls to the selected row. |
| Fonts and size | The outline uses the topic kind's font family at one size (14 sp root bold, 14 sp the rest), not the map's sizes, so the levels read by indentation, not size. |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Every change made from the outline is **one** undo step: a typed topic with the topic added for it, an indent of
  five selected topics, a Backspace that removes a row.
- **Only one text editor at a time.** `textEditNodeId` is shared, so in Map and Outliner mode the map must not show
  its `NodeTextEditor` while the outline is typing in the same topic, and the other way round. This is the easiest
  thing to miss.
- Works in the web app (wasm): only common Compose, no JVM-only APIs in the library or `commonMain`.
- Nothing is added to the map file; maps open and save exactly as before.

## Review Focus

1. **Moves:** a topic can't be moved under itself or its own branch; the root can't be moved; the new parent is
   unfolded; attributes, tasks, lines, borders, notes and arrow links stay with the topic (they are keyed by id).
   Indenting a selection of siblings puts them all, in order, under the sibling before the first of them.
2. **One editor:** typing in a row never also opens the map's editor, and switching views while typing commits the
   text first.
3. **Focus and keys:** Tab in the outline indents and never takes the focus to the menus; the map's Tab still adds a
   child; keys reach the view that has the focus, and the menus' shortcuts still work from both.
4. **Scrolling:** a selection made on the map, by find or by the filter's navigation brings its row into view; a
   long map (5,000 topics) scrolls smoothly, since only visible rows are composed.

---

## Phase A: Moves and a read-only outline

### Task A1: Moving a topic anywhere in the tree

**Files:** `data/Tree.kt`, test `TreeMoveTest.kt` (common)

- [x] `Tree.moveNode(targetId, parentId, index: Int?, side)`: takes the topic from its parent and puts it at `index`
  among the new parent's children (counted as `addNode` counts it, null for the end). It throws for the root, a
  missing topic or parent, or a parent in the topic's own branch. It unfolds the new parent and sets the root child
  side when the new parent is the root, and drops it otherwise. `onTouchUp`'s remove-then-attach uses it.
- [x] Tests (9): to another parent at an index; within the same parent, up and down; to the end; with its branch; under its own
  descendant refused; the root refused; a folded parent unfolded; to the root with a side; a topic in a summary's range
  moved out leaves the summary about the rest.

### Task A2: Indent, outdent and move in the state

**Files:** `MindMapState.kt`, test `OutlineMoveStateTest.kt`

- [ ] `indent()`: the selected branches, grouped by parent, each group goes as the last children of the sibling
  before its first one (the previous sibling in `children` order, any side). A group with no sibling before it stays.
- [ ] `outdent()`: each selected branch whose parent is not the root goes after its parent, among its grandparent's
  children, with the side rule from the decisions. The siblings after it stay where they are (XMind's behaviour, not
  Workflowy's, which would make them its children).
- [ ] `moveAmongSiblings(isUp: Boolean)`: a contiguous group of selected siblings swaps with the sibling before or
  after it; at the end nothing happens.
- [ ] Each is one `recordChange`, keeps the selection on the moved topics, and animates the map. A change that moves
  nothing records nothing.
- [ ] Tests (10): indent one, then undo; indent three siblings together; indent the first child does nothing; outdent
  to the root keeps the side; outdent two; move up and down; move at either end does nothing; the selection is kept;
  each is one undo step; a summary indented is an ordinary topic.

### Task A3: Outline rows

**Files:** new `library/.../outline/OutlineRows.kt`, test `OutlineRowsTest.kt`

- [ ] `data class OutlineRow(id, depth, hasChildren, isFolded, isSummary)` and `outlineRows(tree): List<OutlineRow>`,
  the visible topics in preorder from the root (depth 0). Summaries come after the siblings they are about, which is
  where they are in `children`.
- [ ] `rowIndexOf(rows, id)` and `rowAbove/rowBelow(rows, id)` for the arrow keys, which skip nothing that is shown.
- [ ] Tests (6): order and depth; folded branches left out; a summary's place; the root alone; after a move; after a
  fold change.

### Task A4: The outline composable

**Files:** new `library/.../outline/MindMapOutline.kt`, new `outline/OutlineRowView.kt`, `MindMap.kt` (share the task
badge and note marker drawing), test `MindMapOutlineTest.kt` (JVM, Compose UI)

- [ ] `MindMapOutline(state, modifier, topicFontFamilies, emojiFontFamily, dateLabel, onLinkClick, onRowMenu)`: a
  `LazyColumn` of `outlineRows`, 24 dp per level, with the parts of a row from the decisions table, in the theme's
  background and text colours so dark themes read.
- [ ] Click selects (`state.select`), Ctrl+click and Shift+click as on the map (`toggleSelected`, `selectRange`), a
  click on the chevron folds, a right click calls `onRowMenu` with the topic, so the app shows its node menu.
- [ ] When the selected topic changes, its row is scrolled into view (`animateScrollToItem` only when it is off the
  screen, so clicking doesn't jump).
- [ ] Rows that don't match `state.taskFilter` are drawn at 30% alpha, unless a topic below them matches, from the
  same `taskFilterView()` the map uses.
- [ ] Tests (7): rows in order with their indentation; a click selects and the map's `selectedNode` is it; the chevron
  folds and the rows below go; a change on the map (add, remove, edit text) shows in the outline; a selection made in
  the state scrolls its row into view; filtered rows faded; a right click calls `onRowMenu`.

---

## Phase B: Typing in the outline

### Task B1: The row editor

**Files:** `outline/OutlineRowView.kt`, new `outline/OutlineTextEditor.kt` (from `NodeTextEditor`'s editing part,
without its map placement), `MindMapState.kt` (`textEditHost`), `MindMap.kt`, test `OutlineTypingTest.kt`

- [ ] `enum class TextEditHost { MAP, OUTLINE }` and `state.textEditHost`, set by whichever view starts the typing;
  `MindMap` shows `NodeTextEditor` only for `MAP` and the outline its editor only for `OUTLINE`.
- [ ] The row being typed in shows a `BasicRichTextEditor` in place of the text, in the row's style, with the same
  Ctrl+B/I/U as the map's editor and `previewText` on each change, so the map grows as the text is typed.
- [ ] Double click or F2 starts typing; typing a letter on a selected row replaces its text, as on the map.
- [ ] Tests (5): double click, type, Enter: one undo step; typing a letter replaces the text; the map's editor isn't
  shown; Esc gives back the old text; the map's node is the new text before the typing ends.

### Task B2: The outliner keys

**Files:** new `desktop/.../OutlineKeys.kt` (`outlineKeyAction`), `MindMapApp.kt` (send keys by focused view),
`MindMapState.kt` (`removeEmptyAndEditAbove`), tests `OutlineKeysTest.kt`, `OutlineAppTest.kt`

- [ ] `outlineKeyAction(key, codePoint, isShift, isCtrl, isAlt, isMeta, isEditing, isTextEmpty, hasSelectedNode)`
  returning the existing `InlineKeyAction`s plus `Indent`, `Outdent`, `MoveUp`, `MoveDown`, `RemoveEmptyAndEditAbove`,
  `SelectRow(isUp)`, `FoldOrParent`, `UnfoldOrChild`, with the keys in the decisions table.
- [ ] Enter while typing commits and adds a sibling below to type in, through `addNodeForTextEdit`, so the new topic
  and its text are one step. Enter on an empty row just added cancels it, which removes it.
- [ ] Tab and Shift+Tab while typing commit, move, and start typing again in the same topic with the caret where it
  was, all as one step (`groupChanges`).
- [ ] `removeEmptyAndEditAbove()`: one step that removes the empty leaf and starts typing at the end of the row above.
- [ ] Tests: `OutlineKeysTest` (12) for each key, typing and not; `OutlineAppTest` (6): type three topics with Enter,
  indent the second with Tab while typing, outdent with Shift+Tab, Alt+Shift+Up, Backspace on an empty row, then undo
  each step back to the start.

---

## Phase C: In the app

### Task C1: View modes

**Files:** `MindMapApp.kt`, `menuBar/ViewMenu.kt`, `menuBar/EditMenu.kt` (Move Up, Move Down, Indent, Outdent),
`UserPreferences.kt` (`viewMode`), new `component/SplitPane.kt`, `KeyboardShortcuts.kt`, test `ViewModeAppTest.kt`

- [ ] `enum class ViewMode { MAP, OUTLINER, BOTH }`, remembered in the preferences. View › Map / Outliner / Map and
  Outliner with a tick, and Ctrl+Shift+O to cycle. Changing the mode while typing commits the text first.
- [ ] Map and Outliner: a `SplitPane` with the outline on the left (35% to start, kept in the preferences, 200 dp at
  least on either side); below 700 dp of window width it shows the outline only, as the web app on a phone.
- [ ] In Map and Outliner, a row selected in the outline centres its topic on the map only when the topic is off the
  map's screen (`centerNode`), so the map doesn't move under a mouse that is on it.
- [ ] The node menu, the edit dialog (Alt+Enter), Delete, cut, copy and paste, folding keys, task keys, boundaries,
  summaries and find all act on the selection, so they work from the outline with no change; each is tried once in
  the tests.
- [ ] Tests (7): the menu and Ctrl+Shift+O switch modes and are remembered; Tab in the outline indents and Tab on the
  map adds a child; Edit › Move Up on the map; Ctrl+F finds and selects in the outline; Alt+Enter opens the dialog
  from a row; switching modes while typing keeps the text; the narrow window shows the outline alone.

### Task C2: Documentation

**Files:** `docs/user-manual/user-manual.html` (new chapter "The outliner" after Folding, rows in the shortcut
tables), PDF (`make-pdf.sh`), `README.md` (feature list and feature tour text), `KeyboardShortcuts.kt`, test
`KeyboardShortcutsManualTest` (kept passing)

- [ ] The chapter: what the outline is, the view modes, typing a list, indent, outdent and moving, what a row shows,
  and that boundaries and arrow links are only on the map.
- [ ] A screenshot of Map and Outliner on `desktop/feature_tour.mmv`, for the README and the manual.

---

## Out of scope / follow-ups

- Dragging rows to move them (moves are by keys and menus in this plan).
- Notes typed in the outline, below their row, as Workflowy does.
- Zooming into a branch, so it is the whole outline for a while ("drill down"), which would also suit the map.
- Copying rows as plain indented text, and pasting indented text as rows (pasting already reads Markdown and OPML).
- Showing boundaries or arrow links in the outline.
- Printing or exporting the outline itself (the OPML, Markdown and HTML exports already cover it as text).
