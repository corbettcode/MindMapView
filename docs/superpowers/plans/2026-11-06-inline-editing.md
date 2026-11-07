# Tab, Enter and Inline Editing Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a map from the keyboard as in XMind and MindNode. Tab adds a child and Enter a sibling, and the new
topic is typed into on the map itself, with no dialog. The edit dialog stays for everything but the text.

**Architecture:** The library gets a *text edit session* in `MindMapState`. It begins with a snapshot of the tree,
changes the node's text live while it is typed, without recording each change, and ends by recording one undo step
(commit) or putting the snapshot back (cancel). Adding a node inside a session makes "add + type" one step that
Escape takes back whole. `MindMap` draws a `BasicRichTextEditor` over the node being edited, placed and scaled with
the view, and leaves out the node's own text under it. The desktop and web app maps the keys onto the session and
picks the new node's style as the Add dialog does today.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, compose-rich-editor (already a library dependency, as
`RichText` uses `RichTextState`).

## Decisions (the user's)

| Key / gesture        | Does                                                                     |
|----------------------|--------------------------------------------------------------------------|
| Tab                  | Add a child of the selected node and edit its text inline                |
| Enter                | Add a sibling below the selected node and edit it inline (root: a child) |
| Shift+Enter          | Add a sibling above (root: a child)                                      |
| F2                   | Edit the selected node's text inline                                     |
| Double-click         | Edit inline (was: open the dialog)                                       |
| Typing a character   | Start editing inline, replacing the text with that character             |
| Alt+Enter            | Open the full edit dialog (was Enter). Freeplane's key for its dialog    |
| `+`                  | Unchanged: add a child through the dialog                                |
| Space                | Unchanged: fold. It does not start type-to-edit                          |

While editing inline: Enter commits, Shift+Enter is a new line, Tab commits and adds a child of the edited node,
Escape cancels, Ctrl+B / Ctrl+I / Ctrl+U toggle bold, italic and underline. A press elsewhere on the map commits.

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library functions get a KDoc in the style of `MindMapState`.
- One commit per task, `feat:` / `docs:` / `test:` in the style of `git log`, ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- A finished edit is **one** undo step, and a new node plus its typed text is one step too. A cancelled edit, or a
  commit that changes nothing, leaves nothing to undo.
- Committing with empty text works as Escape: a new node goes, an existing node keeps its old text.
  The root can never end up empty.
- No shortcut on the map acts while an inline edit is open (arrows, Space, `+`, Ctrl+A/C/X/V/Z/Y, Ctrl+arrows, F3,
  Ctrl+Alt+C). The editor's own undo, copy and paste take those keys.

## Review Focus

1. **Undo:** Tab, type, Enter, then Ctrl+Z removes the new node in one step, and Ctrl+Y brings it back with its text.
2. **Escape on a new node** leaves the tree as it was and `canUndo` as it was.
3. **No spurious change:** F2 then Enter without typing must not record a step or set `isModified`. The rich editor
   rewrites HTML (for example adding `<p>`), so compare normalized text and spans, not raw strings.
4. **Tab focus:** neither Compose's focus traversal nor the browser (wasm) may take Tab off the map.
5. **Big maps:** typing must stay smooth. Re-measure only the edited node, then `arrangeTree()` with no animation per
   keystroke.

---

### Task 1: Insert a child at a position

**Files:** `library/.../data/Tree.kt`, test `library/src/commonTest/.../SiblingInsertTest.kt`

- [ ] `Tree.addNode(..., index: Int? = null)`: inserts into the parent's `children` at `index`, or at the end when
  null (as today).
- [ ] Tests: insert at 0, in the middle and past the end. A child of the root inserted next to a sibling takes that
  sibling's side.

### Task 2: Add a sibling

**Files:** `library/.../MindMapState.kt`, test `SiblingInsertTest.kt`

- [ ] `MindMapState.addSiblingNode(description, shape, color, textColor, isAbove = false, note, line, border)`: adds
  under the selected node's parent, right after it (or before it with `isAbove`), on its side. For the root it adds a
  child on `side` (default `layoutSide`), as `addNode` does. It records one step and animates. As with `addNode`,
  the selected node stays selected and `getAddNode()` returns the new one. Task 5 selects it to edit.
- [ ] Share the node-building `when (shape)` in `addNode` with it instead of copying it a third time.
- [ ] Tests: the order of siblings below and above, a child of the root on UPPER_LEFT stays on UPPER_LEFT, the root
  case, one undo step.

### Task 3: Text edit session in the state

**Files:** `library/.../MindMapState.kt`, `MindMapManager.kt`, test `library/src/commonTest/.../TextEditTest.kt`

- [ ] `val textEditNodeId: String?` (observable): the node being edited, or null.
- [ ] `fun startTextEdit(text: String? = null)`: starts editing the selected node. `text` replaces its text, for
  type-to-edit. It snapshots the tree first, unless a session is already open.
- [ ] `fun addNodeForTextEdit(add: MindMapState.() -> Unit)`: snapshots, runs `add` (for example
  `{ addSiblingNode(...) }`) without recording, selects the added node and opens the session on it, so the add and
  the typing are one step. Built this way so `addNode` and `addSiblingNode` need no flag.
- [ ] `fun previewText(html: String)`: sets the description without recording, re-measures that node, and runs
  `arrangeTree()` with no animation.
- [ ] `fun commitTextEdit()`: an empty text cancels. Otherwise it records one `Step(before)` if the content changed
  (Review Focus 3), then closes the session.
- [ ] `fun cancelTextEdit()`: `tree.restore(before)`, so a new node is gone. It selects the node edited, or the
  parent of a new node.
- [ ] Anything else that changes the map commits the open session first: `select`, `undo`, `redo` and every
  `recordChange`. `setTree` cancels it.
- [ ] Tests: everything in Review Focus 1–3, empty commit of a new node and of an existing one, the root kept from
  empty, and `isModified` false after cancel.

### Task 4: Draw the inline editor

**Files:** `library/.../MindMap.kt`, `layout/MeasureTextSize.kt`, test in `MindMapTest.kt` (compose ui-test, if the
existing tests use it; otherwise a check of the text rectangle as plain math)

- [ ] Put `MindMap`'s `Canvas` in a `Box`. While `textEditNodeId != null`, show a `BasicRichTextEditor` over the
  node's **text** rectangle (from `measureContent`: the text rise and size, not the whole node, since notes and
  attributes sit below it). Place it at `screenPosition`, scale it with `graphicsLayer(scaleX = scaleFactor, ...)`,
  and give it the node's font for its topic kind, the text size, `tree.textColorOf(id)`, centered, and a clear
  background. Pan and zoom move it, as they read the same state.
- [ ] `drawTree` leaves out the text of the node being edited, so it is not drawn twice.
- [ ] The editor asks for focus when it opens, and selects all of its text for a new node, so typing replaces
  "Topic 3". Each change calls `previewText(state.toHtml())`.
- [ ] Handle Ctrl+B / I / U in the editor with `toggleSpanStyle`. Enter, Tab and Escape are left to the app (Task 5),
  so library users choose them. Without an app handler, `MindMap` commits on Enter and cancels on Escape.
- [ ] A press on the map outside the editor commits before it does anything else.
- [ ] Known limits, to check by eye in Task 7: the editor doesn't apply the app's emoji font, and it reads a
  text's own sizes (Small, Large) as CSS pixels rather than as a scale of the node's text. Only text with emoji or
  its own size may show slightly differently while it is typed.
- [x] Find how `MeasureTextSize` wraps long text. It doesn't: text is measured with no width limit, so the editor
  sizes itself to its text, unbounded.

### Task 5: Keys and gestures in the app

**Files:** `desktop/.../MindMapApp.kt`, `menuBar/EditMenu.kt`, new `desktop/.../InlineEditKeys.kt` + test
`desktop/src/jvmTest/.../InlineEditKeysTest.kt`

- [x] End-to-end: `InlineEditAppTest` drives the real `MindMapApp` with a fake `AppPlatform`. It covers Tab, Enter,
  Shift+Enter, F2, Escape, undo, Alt+Enter, and keys going to the text while it is typed. Test key presses carry no
  character, so type-to-edit is only unit-tested (`InlineEditKeysTest`) and checked by hand in Task 7.
- [ ] Pull the key mapping into a pure function, `inlineKeyAction(key, isShift, isCtrl, isAlt, codePoint,
  isEditing): InlineKeyAction?`, so the whole Decisions table is unit-tested without a window.
- [ ] In `onPreviewKeyEvent`, before every other case: while `state.textEditNodeId != null`, handle only Enter
  (commit), Tab (commit, then add a child of that node), Escape (cancel) and the Ctrl+B/I/U the editor takes, and let
  every other key through to the editor. Consume Tab always (Review Focus 4).
- [ ] Not editing, with a node selected: Tab adds a child, Enter a sibling below, Shift+Enter one above, F2 starts an
  edit, Alt+Enter opens the dialog (`editNodeRequest++`, which Enter did). A printable character other than space or
  `+`, with no Ctrl or Alt, starts type-to-edit with that character.
- [ ] Style a new node as the Add dialog does: `newNodeStyle(...)`, `preferences.topicOf(kind).shape`, and text
  "${kind.label} ${nextNodeNumber(...)}". Bump `createdNodeCount` only on commit.
- [ ] `onNodeDoubleClick` starts an inline edit instead of `Operation.Update`. The node menu and Edit menu get
  *Add Child (Tab)*, *Add Sibling (Enter)*, *Edit Text (F2)*, and *Edit... (Alt+Enter)* for the dialog.
- [ ] Opening a dialog, the find bar or a menu commits the open edit.
- [ ] Check `utf16CodePoint` on wasm in Chrome and Firefox. Dead keys and IME input (accents, CJK) may not arrive as
  a single character. If not, type-to-edit only starts on what arrives, and the editor itself handles composition.

### Task 6: Shortcuts, manual and README

**Files:** `desktop/.../KeyboardShortcuts.kt`, `docs/user-manual/user-manual.html`,
`desktop/manual/MindMapView-User-Manual.pdf` (via `docs/user-manual/make-pdf.sh`), `README.md`

- [ ] Update the shortcut table: the new keys, and Enter's old meaning moved to Alt+Enter.
  `KeyboardShortcutsManualTest` keeps the dialog and the manual in step.
- [ ] Manual: a short "Typing a map" section (Tab, Enter, type, Escape) near the start.
- [ ] README: replace "Pressing Enter opens the edit dialog" and document `addSiblingNode`, `startTextEdit`,
  `commitTextEdit`, `cancelTextEdit` and `textEditNodeId`.

### Task 7: Try it

- [ ] `./gradlew :desktop:run` and `./gradlew :desktop:wasmJsBrowserDevelopmentRun`: build a 3-level map with only
  the keyboard. Edit while zoomed at 50% and 200%, on each of the six root sides, with a line-shaped node, with a node
  that shows a note inline, and in a dark theme. Undo and redo all of it.
- [ ] Run `./gradlew :library:allTests :desktop:jvmTest`.

## Out of scope

- The Android `MindMapView` View gets no inline editing. Its hosted composable gets the library parts, but no keys.
- Inserting a parent (XMind's Ctrl+Enter), and moving a node up or down among its siblings with the keyboard. They
  would be a good follow-up, and would use Task 1's `index`.
