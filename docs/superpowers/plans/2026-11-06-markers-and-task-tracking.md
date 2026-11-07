# Markers and Task Tracking Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Topics can carry real task data: a **priority** (1–9), **progress** (not started, 25%, 50%, 75%, done)
and optional **start and due dates**. These show as small badges on the topic, and a **filter** dims every topic
that doesn't match (for example "priority 1–2, not done", or "overdue").

Today XMind's priority and task markers are imported as emoji typed into the text, so nothing can filter or sort by
them, and FreeMind's icons are ignored entirely.

**Architecture:**

- **Data:** a node's task data is one value, `NodeTask(priority, progress, start, due)`, kept by the tree in a map
  like line settings and borders. It is part of undo snapshots, copy and paste, and the "modified" check.
- **Dates:** a small `TaskDate(year, month, day)` with ISO `YYYY-MM-DD` text form, so the library takes on no new
  dependency. "Today" comes from the app (`state.today`), because only the platform knows the local date.
- **Badges:** drawn at the start of the topic's text line, inside the node, so the node grows to fit them, as XMind
  draws markers. The due date is a small line below the text, red when overdue.
- **Filter:** drawing only. Topics that don't match are drawn faded, along with their lines; ancestors of matching
  topics stay solid so the path to them reads. Nothing is hidden, so the layout doesn't jump.

**Build order:** phase A (priority and progress, end to end) can ship on its own; phase B adds dates; phase C adds the
filter.

## Decisions (proposed, for review)

| Question | Proposal |
|---|---|
| Progress steps | Not started, 25%, 50%, 75%, Done: Freeplane's steps. XMind's eighths are rounded to the nearest quarter on import. |
| Keys | Ctrl+1 … Ctrl+9 set the priority and Ctrl+0 removes it (XMind's keys). Ctrl+D toggles Done ↔ Not started (unused today). |
| Clicking the progress badge | Steps it on: Not started → 25% → 50% → 75% → Done → Not started. |
| Where to edit it all | Edit › Task submenu, the node menu, and a **Task** section in the edit dialog (priority, progress, start and due date pickers). |
| Badge look | Priority: a filled circle with its number, red, orange, yellow, green, blue, then grey for 6–9. Progress: a ring that fills by quarters, with a tick for Done. Both are drawn in the theme's colours where possible, readable in dark themes. |
| Due date label | "Due 30 Nov", or with the year when it isn't this year. Red when overdue (before today and not done). The app formats it; the library defaults to ISO. |
| Filter UI | View › Filter… opens a bar like the find bar, with chips: priorities, Not done / Done, Overdue, Due in 7 days, and Clear. Esc closes it and clears the filter. |
| Filter and exports | Exports ignore the filter and show the whole map. Exporting the filtered view is a follow-up. |
| Progress roll-up | Not in this plan. A parent's progress is its own, not computed from its children (a follow-up, as in MindManager). |

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists, booleans named as questions.
- Comments explain *why*. Public library API gets KDoc in the style of `MindMapState`.
- One commit per task on a branch, merged into `main` and the branch deleted, messages `feat:` / `fix:` / `docs:`
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Every change of task data is **one** undo step, also when applied to several selected topics at once.
- Badges and the due label are drawn by the node drawing that exports share, so PNG, SVG and PDF show them. The
  filter's fading is not applied in exports.
- The inline text editor from the inline-editing plan (`NodeTextEditor`) sits over the text only, so it must shift
  right by the badges' width. This is the easiest thing to miss.
- Maps saved before this feature open unchanged. In older versions, maps saved with it lose only the task data.

## Review Focus

1. **Measuring:** a node with badges and a due label is sized to fit them; the inline editor, link hit-testing
   (`findLink`) and the note and attribute positions all account for the badges' width.
2. **Undo:** setting priority on five selected topics is one step; Ctrl+Z restores each one's old value.
3. **Round trips:** `.mmv` keeps everything. FreeMind keeps priority and done/progress as built-in icons and dates as
   attributes. XMind priority and task markers become task data, not emoji, and other markers stay emoji.
4. **Dates:** invalid dates in files are ignored, not fatal. A start after the due date can't be entered.
   "Overdue" uses the app's today, and nothing in the library reads the clock.

---

## Phase A: Priority and progress

### Task A1: Task data in the tree

**Files:** new `library/.../data/NodeTask.kt`, `data/Tree.kt`, `data/TreeSnapshot.kt`, `MindMapState.kt`
(`CopiedNode`), test `NodeTaskTreeTest.kt`

- [x] `data class NodeTask(priority, progress, start, due)` with `isEmpty` and `PRIORITIES = 1..9`; a priority
  outside them throws. `enum class TaskProgress(percent)` with NOT_STARTED(0), QUARTER(25), HALF(50),
  THREE_QUARTERS(75), DONE(100), and `next`, which goes from Done back to Not started.
- [x] `data class TaskDate(year, month, day)`, comparable, years 1–9999, which throws for no such day.
  `toIsoString()` pads to `YYYY-MM-DD`. `TaskDate.parse(text)` trims, accepts only `YYYY-MM-DD`, and returns null for
  no such day, so 2026-02-29 and 2100-02-29 are null while 2000-02-29 is valid. No new dependency.
- [x] `Tree.taskOf(id)` (empty when none) and `Tree.setTask(id, task)` (an empty task removes the entry, and a node
  not in the tree throws). Tasks are in the snapshot, `restore` and `hasSameContentAs`, and `CopiedNode` carries them.
- [x] Tests (13): ISO writing and reading; no such day; calendar order; progress steps; priority range; `isEmpty`;
  setting; clearing back to an unchanged map; a missing node; snapshot and restore; copy and paste of a branch.

### Task A2: Task data in the state

**Files:** `MindMapState.kt`, test `NodeTaskStateTest.kt`

- [x] `setPriority(priority: Int?)` (a priority outside 1–9 throws before changing anything), `setProgress(progress:
  TaskProgress?)`, `toggleDone()` and `stepProgress(id)` for the badge click. Each applies to all selected nodes, the
  root included, as one change, and keeps the rest of each node's task. A shared `editTasks` re-measures the tree and
  animates it, since badges change node sizes from A3 on.
- [x] `toggleDone()` makes all the selected nodes Done unless all already are, in which case it makes them all Not
  started. `stepProgress` gives a node without progress Not started, and does nothing for a node that isn't there.
- [x] Tests (9): several nodes in one step, with the rest of the task kept, then undo; taking a priority away; an
  invalid priority; the root; toggling with a mixed selection; stepping round, then undo; a missing node; nothing
  selected; setting a value a node already has records no change.

### Task A3: Badges

**Files:** `layout/MeasureTextSize.kt` (`NodeContent` gains the badges' width), `MindMap.kt` (drawing), new
`TaskBadges.kt` (geometry and colours), `NodeTextEditor.kt`, `MindMapState.kt` (`findLink`, hit test), test
`TaskBadgeTest.kt`

- [ ] `NodeContent` gets `badgesWidth` (badge size 14 dp, gap 4 dp, then 6 dp before the text), included in `width`.
  The text, note and attribute table keep their own positions, so only the first line shifts.
- [ ] Draw the priority circle with its number, and the progress ring or tick, left of the text, vertically centred on
  the first line. Colours: a fixed priority palette checked for contrast on light and dark backgrounds, plus the
  node's text colour for the progress ring.
- [ ] `NodeTextEditor` and `findLink` shift by `badgesWidth`.
- [ ] `state.findProgressBadge(position)`, and a click on it calls `stepProgress` instead of selecting or starting a
  drag.
- [ ] Tests: a node with badges is wider by exactly `badgesWidth`; the badge colours are in a PNG export; a click on
  the progress badge steps it and one on the text doesn't; the inline editor is over the text, not the badges.

### Task A4: Priority and progress in files

**Files:** `io/MindMapViewFile.kt`, `io/FreeMind.kt`, `io/XMind.kt`, `io/Markdown.kt`, `io/Opml.kt`,
`io/HtmlOutline.kt`, tests

- [ ] `.mmv`: `priority="2"` and `progress="half"` on `<node>`.
- [ ] FreeMind: priority as `<icon BUILTIN="full-N"/>`, FreeMind's own number icons. Done as `<icon
  BUILTIN="button_ok"/>`. Before writing code, check Freeplane's progress icon names against its source, as was done
  for summaries in the boundaries plan. If Freeplane has 0–100% icons, use them for the quarters; otherwise keep the
  quarters in an attribute, as the shape is kept today. Read all of these back; other icons stay as they are read
  today, which is ignored.
- [ ] XMind: `priority-N` becomes the priority, and `task-start` / `task-oct` / `task-quarter` / `task-3oct` /
  `task-half` / `task-5oct` / `task-3quar` / `task-7oct` / `task-done` become progress, rounded to the nearest
  quarter (`task-oct` → 25%, `task-3oct` → 50%, `task-5oct` → 75%, `task-7oct` → Done). These markers no longer become
  emoji; all other markers still do.
- [ ] Markdown: a list item with progress is written `- [ ]`, or `- [x]` when Done, and read back that way. Headings
  can't hold a checkbox and keep none. OPML: `_priority` and `_progress` attributes. HTML: the badges as text in front
  of the topic, such as `[P1] [50%]`.
- [ ] Tests for each, including XMind's emoji no longer appearing for those markers.

### Task A5: Priority and progress in the app

**Files:** `MindMapApp.kt`, `menuBar/EditMenu.kt`, `dialog/EditNodeDialog.kt`, `KeyboardShortcuts.kt`, manual and
PDF, `README.md`, test `TaskAppTest.kt`

- [ ] Keys: Ctrl+1…9 and Ctrl+0, Ctrl+D. Like Ctrl+B, they act only outside typing. Check that none clashes with the
  text field's own keys while typing (Ctrl+D doesn't in compose-rich-editor; confirm).
- [ ] Edit › Task submenu: Priority ▸ (None, 1–9), Progress ▸ (None, the five steps), Toggle Done. The same in the node
  menu.
- [ ] The edit dialog gets a **Task** section with priority and progress dropdowns; phase B adds the dates.
- [ ] Shortcuts, a manual section "Tasks", the PDF, and the README.
- [ ] App tests: Ctrl+2 on two topics, then undo; Ctrl+D twice; the dialog sets progress.

---

## Phase B: Dates

### Task B1: Start and due dates

**Files:** `MindMapState.kt` (`setDates`, `today`), `layout/MeasureTextSize.kt` (the due label line), `MindMap.kt`
(`dateLabel` parameter), tests

- [ ] `setDates(start: TaskDate?, due: TaskDate?)` on the selected nodes, as one change; a start after the due date
  throws. `state.today: TaskDate?` is set by the app, and `isOverdue(id)` is due before today and not Done.
- [ ] The due date is a small line below the text (and above the note), measured into the node, in the theme's text
  colour, or red when overdue. `MindMap(dateLabel = { date -> String })` formats it, defaulting to ISO. The start
  date shows only in the dialog and the files.
- [ ] Tests: the label line grows the node; overdue colour with a given `today`; nothing reads the system clock.

### Task B2: Dates in files and the app

**Files:** `io/*`, `desktop/.../AppPlatform.kt` (an `expect`/platform `today()`), `dialog/EditNodeDialog.kt`, docs,
tests

- [ ] `.mmv`: `start="2026-11-01" due="2026-11-30"`. FreeMind: `<attribute NAME="start">` and `due`, kept apart from
  the node's own attributes as `shape` is. XMind: check its task info extension (start, due) against a file saved by
  XMind before reading it; if it can't be confirmed, leave XMind dates out. OPML: `_start` and `_due`. Markdown: none.
- [ ] Platform `today()`: on desktop `java.time.LocalDate.now()`, in the browser the JS `Date`. The app refreshes
  `state.today` when it starts and when its window regains focus, so a map left open overnight updates.
- [ ] The dialog's Task section gets start and due fields, each a Material3 `DatePicker` in a popup with a Clear
  button. The dialog refuses a start after the due date, with a message.
- [ ] The app formats the label as "Due 30 Nov", or "Due 30 Nov 2027" in another year.
- [ ] Docs and tests.

---

## Phase C: Filter

### Task C1: Filtering in the library

**Files:** new `library/.../model/TaskFilter.kt`, `MindMapState.kt`, `MindMap.kt`, test `TaskFilterTest.kt`

- [ ] `data class TaskFilter(priorities: Set<Int> = all, progress: Set<ProgressFilter> = all, isOverdueOnly: Boolean =
  false, dueWithinDays: Int? = null)`. A node matches when it meets every condition it sets. `state.taskFilter:
  TaskFilter?` is not part of the map and isn't undone.
- [ ] Draw non-matching nodes, their lines and badges at 30% alpha, unless they are an ancestor of a match. Arrow
  links between two faded nodes are faded too. Folded branches that hold a match get a small dot on their fold ring,
  so a hidden match is visible.
- [ ] `exportImage`/`exportSvg` draw without the filter.
- [ ] Tests: matching rules, combinations, ancestors kept solid, exports unaffected, a hidden match dot.

### Task C2: The filter bar

**Files:** new `desktop/.../FilterBar.kt`, `MindMapApp.kt`, `menuBar/ViewMenu.kt`, `KeyboardShortcuts.kt`, docs,
test `FilterBarTest.kt`

- [ ] View › Filter… (Ctrl+Shift+F) opens a bar under the menu bar, like the find bar. Its chips are Priority 1 … 9
  (multi-select), Not done, Done, Overdue, Due in 7 days, then a match count and Clear. Esc closes the bar and clears
  the filter.
- [ ] Find (Ctrl+F) and the filter work together: F3 steps only through matches that also pass the filter.
- [ ] Docs and app tests.

---

## Out of scope / follow-ups

- Progress roll-up from children to parents.
- Assignees, resources, effort, and recurring tasks.
- Reminders or notifications.
- A Gantt/timeline view.
- Sorting siblings by priority or due date.
- Exporting the filtered view.
- Custom marker sets: XMind's flags, stars and people stay emoji in the text, as today.
