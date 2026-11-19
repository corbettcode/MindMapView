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
| Enter while typing | Ends the typing and starts a new sibling below, so a list is typed without stopping (outliner habit, unlike the map, where Enter only ends the typing). Enter on a just-added row left as it was, or empty, removes it and ends the typing. Esc cancels the typing, as on the map. |
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

- [x] `indent()`: the selected branches, grouped by parent, each group goes as the last children of the sibling
  before its first one (the previous sibling in `children` order, any side). A group with no sibling before it stays.
- [x] `outdent()`: each selected branch whose parent is not the root goes after its parent, among its grandparent's
  children, with the side rule from the decisions. The siblings after it stay where they are (XMind's behaviour, not
  Workflowy's, which would make them its children).
- [x] `moveAmongSiblings(isUp: Boolean)`: a contiguous group of selected siblings swaps with the sibling before or
  after it; at the end nothing happens.
- [x] Each is one `recordChange`, keeps the selection on the moved topics, and animates the map. A change that moves
  nothing records nothing.
- [x] Tests (11): indent one, then undo; indent three siblings together; indent the first child does nothing; outdent
  to the root keeps the side; outdent two; move up and down; move at either end does nothing; the selection is kept;
  children of the root keep their side; a summary indented is an ordinary topic.

### Task A3: Outline rows

**Files:** new `library/.../outline/OutlineRows.kt`, test `OutlineRowsTest.kt`

- [x] `data class OutlineRow(id, depth, hasChildren, isFolded, isSummary)` and `outlineRows(tree): List<OutlineRow>`,
  the visible topics in preorder from the root (depth 0). Summaries come after the siblings they are about, which is
  where they are in `children`.
- [x] `rowIndexOf(rows, id)` and `rowAbove/rowBelow(rows, id)` for the arrow keys, which skip nothing that is shown.
- [x] Tests (6): order and depth; folded branches left out; a summary's place; the root alone; after a move; after a
  fold change.

### Task A4: The outline composable

**Files:** new `library/.../outline/MindMapOutline.kt`, new `outline/OutlineRowView.kt`, `MindMap.kt` (share the task
badge and note marker drawing), test `MindMapOutlineTest.kt` (JVM, Compose UI)

- [x] `MindMapOutline(state, modifier, topicFontFamilies, emojiFontFamily, dateLabel, onLinkClick, onRowMenu)`: a
  `LazyColumn` of `outlineRows`, 24 dp per level, with the parts of a row from the decisions table, in the theme's
  background and text colours so dark themes read.
- [x] Click selects (`state.select`), Ctrl+click and Shift+click as on the map (`toggleSelected`, `selectRange`), a
  click on the chevron folds, a right click calls `onRowMenu` with the topic, so the app shows its node menu.
- [x] A click then calls `onRowClick`, as the map's `onNodeClick`, which the app shares with the map, so while an
  arrow link is being added (Ctrl+L) a click on a row picks its target. Test: one in `OutlineAppTest`.
- [x] When the selected topic changes, its row is scrolled into view (`animateScrollToItem` only when it is off the
  screen, so clicking doesn't jump).
- [x] Rows that don't match `state.taskFilter` are drawn at 30% alpha, unless a topic below them matches, from the
  same `taskFilterView()` the map uses.
- [x] The note and attribute markers report the pointer on them (`onNoteHover`, `onAttributeHover`), also with null
  when their row goes while the pointer is on it, and the app shows its flyovers for them, whatever the topic's own
  way of showing its note and attributes on the map, as a row shows neither. Tests: two in `MindMapOutlineTest`, one
  in `OutlineAppTest`.
- [x] Tests (7): rows in order with their indentation; a click selects and the map's `selectedNode` is it; a second
  click is a double click; the arrow folds and the rows below go, and undo brings them back; a change on the map (add,
  remove) shows in the outline; a selection made in the state scrolls its row into view; a right click calls
  `onRowMenu`. The fading of filtered rows uses the map's `taskFilterView()`, which `TaskFilterTest` covers, and
  needs a check by eye here.

---

## Phase B: Typing in the outline

### Task B1: The row editor and its keys

**Files:** new `outline/OutlineTextEditor.kt` (`OutlineTextEditor`, `outlineEditorAction`), `outline/OutlineRowView.kt`,
`outline/MindMapOutline.kt` (`onAddSiblingToType`), `MindMapState.kt` (`TextEditHost`, `textEditHost`,
`isTextEditOfAddedNode`, `removeEmptyAndEditAbove`, `startTextEdit(isCaretAtEnd)`, moves while typing), `MindMap.kt`,
test `OutlineTypingTest.kt`

The keys while typing in a row are the editor's own, in the library, as the editor is; the app only decides the keys
while nothing is typed (B2).

- [x] `enum class TextEditHost { MAP, OUTLINE }` and `state.textEditHost`: a press on the map makes it `MAP`, a press
  in the outline or its focus `OUTLINE`. `MindMap` shows `NodeTextEditor` only for `MAP`, the outline its editor only
  for `OUTLINE`.
- [x] The row being typed in shows a `BasicRichTextEditor` in place of its text, in the row's style, with
  `previewText` on each change, so the map grows as the text is typed.
- [x] A double click starts typing (`onRowDoubleClick`, by default `startTextEdit`).
- [x] Keys (`outlineEditorAction`): Enter ends the typing and goes on in a sibling below (`onAddSiblingToType`, which
  the app gives to add it in its style); Enter in a new topic left as it was, or with no text, removes it and ends.
  Shift+Enter is a new line. Tab / Shift+Tab indent / outdent, Alt+Shift+Up / Down move, Esc cancels as on the map,
  Backspace in an empty topic with nothing below it removes it and types on at the end of the row above
  (`removeEmptyAndEditAbove`). Ctrl+B / I / U as on the map.
- [x] Moves while typing are part of the typing: one change with the text, which Esc takes back too.
- [x] Tests (6): a double click types in the row and not on the map; typed text shows on the map and Esc gives back
  the old text with nothing to undo; Enter goes on in a new sibling, Enter twice ends, two undos take both back; Tab
  and Shift+Tab while typing, then one undo takes back the moves and the text; Alt+Shift+Up while typing; Backspace in
  an empty topic removes it, types on above, and one undo brings it back.

### Task B2: The outline's keys in the app

**Files:** new `desktop/.../OutlineKeys.kt` (`outlineKeyAction`, `OutlineKeyAction`), `MindMapApp.kt`
(`onOutlineKey`, `selectRow`), tests `OutlineKeysTest.kt`, `OutlineAppTest.kt` (with C1's)

- [x] `outlineKeyAction(key, codePoint, isShift, isCtrl, isAlt, isMeta, hasSelectedNode)` for the outline while
  nothing is typed: Tab and Shift+Tab indent and outdent, Up / Down go to the row above / below, Left folds or goes to
  the parent, Right unfolds or goes to the first child; Enter, Shift+Enter, F2, Alt+Enter and a typed letter do what
  they do on the map. With nothing selected, Down selects the root and Enter adds a topic to it.
- [x] The keys go by the view in use, `state.textEditHost`, which a press on either view sets, rather than by the
  focus: on the desktop the map never has the focus, so it could not take it from the outline. While a row is typed
  in, the app leaves every key to the row's editor but those that end the typing to go on, as Ctrl+S.
- [x] Tests: `OutlineKeysTest` (5); `OutlineAppTest`: type topics one after another with Enter in the app's style,
  then undo; Tab and Shift+Tab in the outline, and Tab on the map still adding a child; the arrow keys along the rows
  and folding.

## Phase C: In the app

### Task C1: View modes

**Files:** new `ViewMode.kt` (`ViewMode`, `OutlineMove`), `MindMapApp.kt`, `menuBar/ViewMenu.kt`,
`menuBar/EditMenu.kt` (Move Up, Move Down, Indent, Outdent), `UserPreferences.kt` and `dialog/PreferencesDialog.kt`
(`viewMode`, `outlineFraction`), new `component/SplitDivider.kt`, `MindMapState.kt` (`showNode`), test
`OutlineAppTest.kt`

- [x] `enum class ViewMode { MAP, OUTLINER, BOTH }`, kept in the preferences. View › Map / Outliner / Map and
  Outliner with a tick, and Ctrl+Shift+O to go round them. Changing the mode ends the typing first.
- [x] Map and Outliner: the outline on the left (35% to start), then a `SplitDivider` that is dragged between 15% and
  85%, kept in the preferences when the drag ends. Below 700 dp of window width the outline is shown alone.
- [x] The map stays composed under the outline in Outliner mode, so it stays laid out for the exports, and its menus
  and popups are placed by where the map is beside the outline (`mapOffset`).
- [x] In Map and Outliner, a topic selected in the outline is brought into view on the map only when it is off it
  (`state.showNode`), so the map doesn't move under a mouse that is on it.
- [x] Alt+Shift+Up / Down move the selected topics on the map and in the outline; the Edit menu has Move Up, Move
  Down, Indent and Outdent for both.
- [x] The node menu (right click on a row), the edit dialog (Alt+Enter), cut, copy and paste, folding keys, task keys,
  boundaries, summaries and find all act on the selection, so they work from the outline with no change.
- [x] Tests (4 more in `OutlineAppTest`): Ctrl+Shift+O goes round the modes, which the preferences keep; a narrow
  window shows the outline alone; Alt+Shift+Up on the map; changing the mode while typing keeps the text. The divider
  drag and the menus need a check by eye.

### Task C2: Documentation

**Files:** `docs/user-manual/user-manual.html` (new chapter 11 "The outliner" after Folding, the later chapters and
the section numbers that point at them one on, rows in the shortcut tables), the PDF (`make-pdf.sh`), `README.md`,
`KeyboardShortcuts.kt`, test `KeyboardShortcutsManualTest` (passing)

- [x] The chapter: what the outline is, the view modes, what a row shows, typing an outline, and moving around and
  rearranging, with the side rule for the root's topics; boundaries and arrow links are only on the map.
- [x] Shortcuts: "In the outliner" and "While typing in the outliner" groups, Alt+Shift+Up / Down under Editing,
  Ctrl+Shift+O under View, and two Mouse rows, in the dialog and the manual alike.
- [ ] A screenshot of Map and Outliner on `desktop/feature_tour.mmv` for the README and the manual (left for later).

## Out of scope / follow-ups

- Dragging rows to move them (moves are by keys and menus in this plan).
- Notes typed in the outline, below their row, as Workflowy does.
- Zooming into a branch, so it is the whole outline for a while ("drill down"), which would also suit the map.
- Copying rows as plain indented text, and pasting indented text as rows (pasting already reads Markdown and OPML).
- Showing boundaries or arrow links in the outline.
- Printing or exporting the outline itself (the OPML, Markdown and HTML exports already cover it as text).
