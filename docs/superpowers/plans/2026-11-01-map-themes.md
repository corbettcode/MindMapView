# Map Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Built-in map themes, saved in the `.mmv` file, that restyle background, node colors per level, branch
lines, line width, borders and font, while colors chosen by hand keep winning.

**Architecture:** A `MapTheme` value lives on the `Tree` (so undo, "modified" and saving come with the tree) and is
resolved at draw time: a node whose stored color still equals its shape's default follows the theme, any other
color is the user's. Drawing (`MindMap.kt`, `NodeLines.kt`, `NodeDrawerFactory`, exports) reads resolved values from
the tree instead of `DrawInfo`/`NodeShape` defaults and the dark-mode switch. The desktop app gets a theme picker
and takes the node font from the theme.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.1.

**Spec:** `docs/superpowers/specs/2026-11-01-map-themes-design.md`

## Global Constraints

- Style per `CLAUDE.md` (no formatter configured): 4 spaces, ≤120 columns, K&R braces, leading commas in multi-line
  lists, booleans as questions, comments say *why*. Commit on `main`, `feat:`/`docs:` messages ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **"Hand-set" rule (plan decision, within the spec's "left to the builder"):** a node's fill is *its own* when it
  differs from its default fill (`Tree.ROOT_COLOR` for the root, `shape.defaultColor` otherwise); likewise text
  color against its default text color. Otherwise the theme decides. This is the rule the spec gives for existing
  maps, used for all maps, so `NodeData`, the `.mmv` node format, undo and copy/paste need no change. The edit
  dialog already swaps defaults on shape change with the same rule (`EditNodeDialog.kt:247`).
- A border is the node's own when it is not `NONE`; otherwise the theme's border for its level applies. A line
  setting is its own when set in `NodeLine`; otherwise the theme's top-level values apply.
- **Classic must look exactly as today in a light window**: its levels take the node's stored colors, line color
  `Color.Black`, branch colors = today's `DrawInfo.rootLineColors`, line width 5 dp, no borders, background white,
  no font of its own.
- The map shows its theme in a dark window too (the user's choice): remove the `isSystemInDarkTheme()` switch in
  `MindMap` (`darkModeLineColor`); Preferences › Theme only affects the window.
- A theme with no font of its own (Classic) uses Preferences › Node font; a theme's own font wins (the user's
  choice). Theme fonts are bundled `NodeFont` labels or null.
- Changing the theme is one undo step and marks the map modified. A file without a theme, or with an unknown id,
  opens as Classic.
- Levels: **root** (no parent), **main topic** (parent is the root), **subtopic** (everything else).

## Review Focus

1. **An existing map opened with Classic looks exactly as before.** Pinned in Task 3 `classicFeatureTourLooksAsBefore`
   (golden image captured before drawing changes).
2. **Switching themes back and forth never changes a hand-colored node or loses the theme on undo.** Pinned in
   Task 2 `handColorsSurviveThemeChanges` and `themeChangeIsOneUndoStep`.
3. **Text on a line-shaped node** (no fill, drawn on the background) is readable in every theme. Pinned in Task 1
   `lineNodeTextReadsOnTheBackground`.
4. **A file with an unknown or missing theme id** opens as Classic, no error. Pinned in Task 4
   `unknownThemeReadsAsClassic`.
5. **Exports match the screen**: PNG/SVG background and colors are the theme's, not fixed white. Pinned in Task 3
   `exportUsesTheThemesBackground`.

---

### Task 1: Theme model and built-in set

**Files:**
- Create: `library/src/commonMain/kotlin/com/mindsync/library/model/MapTheme.kt`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MapThemeTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class LevelStyle(val fill: Color?, val text: Color?, val border: NodeBorder = NodeBorder())
  data class MapTheme(
      val id: String, val name: String, val background: Color
      , val root: LevelStyle, val mainTopic: LevelStyle, val subtopic: LevelStyle
      , val lineNodeText: Color?        // text of a line-shaped node, drawn on the background; null = shape default
      , val lineColor: Color            // fold markers and default lines, today DrawInfo.lineColor
      , val branchColors: List<Color>, val lineWidth: Dp, val fontName: String?
  ) { val isDark: Boolean /* background luminance < 0.5 */ }
  object MapThemes { val CLASSIC: MapTheme; val all: List<MapTheme>; fun byId(id: String?): MapTheme }
  ```
  `null` fill/text in a `LevelStyle` means "the node's stored color" (Classic). `byId` returns `CLASSIC` for null
  or unknown ids.

- [ ] **Step 1: Write the failing tests** in `MapThemeTest`:
  - `classicIsTodaysLook`: `CLASSIC.id == "classic"`, all three levels have null fill and text and `NodeBorder()`,
    `lineNodeText == null`, `lineColor == Color.Black`, `branchColors == DrawInfo().rootLineColors`,
    `lineWidth == Dp(5f)`, `background == Color.White`, `fontName == null`.
  - `thereAreSixThemesWithUniqueIds`: `all.size == 6`, ids unique, `all.first() == CLASSIC`, ids include
    `"ocean"`, `"night"`, `"paper"`.
  - `unknownIdIsClassic`: `byId(null)`, `byId("nope")` are `CLASSIC`; `byId("ocean").id == "ocean"`.
  - `nodeTextReadsOnItsFill`: for every theme and level with non-null fill and text, WCAG contrast ≥ 4.5.
  - `lineNodeTextReadsOnTheBackground`: for every theme but Classic, `lineNodeText` is non-null with contrast ≥ 4.5
    against `background`.
  - `nightIsDark`: `byId("night").isDark`, `!CLASSIC.isDark`.
  - `fontsAreBundledOnes`: every non-null `fontName` is one of `Inter`, `Lora`, `Nunito`, `Caveat`,
    `JetBrains Mono`.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.MapThemeTest'` — Expected: unresolved `MapThemes`.

- [ ] **Step 3: Implement.** Values for the mockup themes (from `theme-samples.html`):
  - **Ocean** `ocean`: background `#F2F8FB`; root `#0B4F6C`/`#FFFFFF`; main `#20A4C3`/`#000000`; sub `#D6EEF5`/`#0B4F6C`;
    lineNodeText `#0B4F6C`; lineColor `#0B4F6C`; branches `#0B4F6C #20A4C3 #01BAEF #3D7EA6`; width 5; font null.
  - **Night** `night`: background `#1E1F26`; root `#F2C14E`/`#1E1F26`; main `#3A3D4D`/`#F5F5F5`;
    sub `#2A2C36`/`#C9CCD6`; lineNodeText `#F5F5F5`; lineColor `#9AA0B4`; branches
    `#F2C14E #F78154 #4D9078 #5FAD9E`; width 5; font null.
  - **Paper** `paper`: background `#FBF8F1`; all fills `#FBF8F1`, texts `#3B3B3B`; borders `LINE` in `#3B3B3B`
    (root `FOUR`, main `TWO`, sub `ONE` width — use `NodeLineWidth` entries); lineNodeText `#3B3B3B`; lineColor and
    single branch color `#3B3B3B`; width 2 dp; font `Lora`.
  - Two more of your design: one **colorful light** (e.g. `meadow`, greens/yellows, font `Nunito`) and one
    **minimal dark or high-contrast** look; they must pass the tests. Adjust any mockup value only if a contrast test
    fails, and ledger it.
  Contrast: WCAG relative luminance, `(L1 + 0.05) / (L2 + 0.05)`; put it in the file as an `internal` helper.

- [ ] **Step 4: Run the tests and see them pass** (same command).
- [ ] **Step 5: Commit** `feat: add map themes and a built-in set`

---

### Task 2: The tree's theme, resolution and undo

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/data/Tree.kt` (state next to `nodeBorders`, ~line 125)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/data/TreeSnapshot.kt` (field + `hasSameContentAs`)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMapState.kt` (next to `editNodeBorder`, ~line 720)
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MapThemeTreeTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces (on `Tree`):
  - `var theme: MapTheme` (default `MapThemes.CLASSIC`), saved in `TreeSnapshot` and compared in `hasSameContentAs`.
  - `fun fillOf(id: String): Color`, `fun textColorOf(id: String): Color` — own color per the hand-set rule, else
    the level's color, else the stored color. For a `LineNodeData` with default text: `theme.lineNodeText` ?: stored.
  - `fun drawnBorderOf(id: String): NodeBorder` — `borderOf(id)` unless `NONE`, then the level's border.
  - `internal fun levelStyleOf(id: String): LevelStyle`.
- Produces (on `MindMapState`): `fun setTheme(theme: MapTheme)` — one `recordChange`, then `invalidate()`; no-op when
  equal.

- [ ] **Step 1: Write the failing tests** (trees built as in `OpmlTest`):
  - `untouchedNodesFollowTheTheme`: rectangle child with default colors; after `tree.theme = byId("ocean")`,
    `fillOf(child) == #D6EEF5` for a subtopic and `#20A4C3` for a main topic; root `#0B4F6C`.
  - `handColorsWin`: `updateNodeColor(child, Color(0xFFFF7F50))` → `fillOf` is coral under every theme.
  - `classicGivesTheStoredColors`: under Classic, `fillOf`/`textColorOf` equal the node's `color`/`textColor` for
    a root, a rectangle and a circle.
  - `ownBorderWinsAndNoneTakesTheThemes`: Paper gives a `LINE` border to a node with `NONE`; a node with its own
    `LINE` border in red keeps it.
  - `lineNodeTextFollowsTheTheme`: a `LINE`-shaped node's `textColorOf` is Night's `lineNodeText`.
  - `handColorsSurviveThemeChanges` (MindMapState): color a node coral, `setTheme(ocean)`, `setTheme(night)`,
    `setTheme(CLASSIC)` → node `color` still coral and `fillOf` coral.
  - `themeChangeIsOneUndoStep`: `setTheme(ocean)`; `canUndo`; `undo()` → `tree.theme == CLASSIC`; `redo()` → ocean.
  - `themeChangeMarksTheMapModified`: after `markSaved()`, `setTheme(ocean)` → `isModified`; `undo()` → not.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.MapThemeTreeTest'`
- [ ] **Step 3: Implement** as in Interfaces. Default text of the root: whatever `Tree`'s initial root uses
  (`Tree.kt` ~line 53); keep it next to `ROOT_COLOR` as `ROOT_TEXT_COLOR` if it is not a named constant yet.
- [ ] **Step 4: Run them and see them pass**, then `./gradlew :library:jvmTest` (undo/modified suites).
- [ ] **Step 5: Commit** `feat: give a mind map a theme that its untouched nodes follow`

---

### Task 3: Drawing and exports use the theme

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMap.kt` (`MindMap` ~line 165–195: background,
  `lineColor`, `addIconColor`; `drawTree` ~line 432; `rootLineColor` helper ~line 965)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/NodeLines.kt` (`forEachNodeWithLine` ~line 17–50)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/node/NodeDrawerFactory.kt` (fills/text ~lines 40–80)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MapImage.kt` (`drawExportedMap`)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/model/DrawInfo.kt` (remove `darkModeLineColor`)
- Create: `library/src/jvmTest/resources/classic-feature-tour.png` (golden, Step 1)
- Test: `library/src/jvmTest/kotlin/com/mindsync/library/ThemeDrawingTest.kt`

**Interfaces:**
- Consumes: Task 2 (`fillOf`, `textColorOf`, `drawnBorderOf`, `theme`).
- Produces: nothing new for later tasks; `MindMap` draws `tree.theme.background` behind the map.

- [ ] **Step 1: Capture the golden image before touching drawing.** In `ThemeDrawingTest`, a helper renders
  `../desktop/feature_tour.mmv` with `exportImage(pixelsPerDp = 1f)` (setup as in `ExportSvgTest.shownState`).
  Run it once at the current commit to write `classic-feature-tour.png` into `src/jvmTest/resources`; commit it in
  this task's commit. Then write:
  - `classicFeatureTourLooksAsBefore`: same size, and ≤ 0.5% of pixels differ by more than 8 in any channel.
  - `oceanColorsTheNodes`: two-child map, `setTheme(ocean)` → `exportImage` contains `#20A4C3` (main fill) and
    `#0B4F6C` (root fill) pixels; Classic image contains neither.
  - `handColoredNodeKeepsItsColor`: a coral child under Night → coral pixels present.
  - `exportUsesTheThemesBackground`: Night → PNG pixel (0,0) is `#1E1F26`; SVG's first `rect` fill is `#1E1F26`
    (case-insensitive).
  - `themeLineWidthIsDrawn`: Paper → no pixel of Classic's first branch color `#5B9A63`, and `#3B3B3B` present.
- [ ] **Step 2: Run them and see the new ones fail** (golden passes)
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.ThemeDrawingTest'`
- [ ] **Step 3: Implement.** Node fills/text from `tree.fillOf/textColorOf`; borders from `drawnBorderOf`; branch
  colors from `theme.branchColors`, top-level width `theme.lineWidth`, default line/fold-marker color
  `theme.lineColor`; `addIconColor = if (theme.isDark) Color.Black else Color.White` as today's dark/light pair;
  `MindMap` fills `theme.background` behind the drawing; `drawExportedMap` clears to `theme.background`. Remove the
  `isSystemInDarkTheme()` switch and `darkModeLineColor`.
- [ ] **Step 4: Run them and see them pass**, then `./gradlew :library:jvmTest` (Export*, NodeLine, SixSides).
- [ ] **Step 5: Commit** `feat: draw the map in its theme, on screen and in exports`

---

### Task 4: Themes in files

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/io/MindMapViewFile.kt` (write ~line 130, read ~line 59)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/io/FreeMind.kt` (node colors in `write`)
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MindMapViewFileThemeTest.kt`

**Interfaces:**
- Consumes: Task 2.

- [ ] **Step 1: Write the failing tests:**
  - `themeRoundTrips`: `tree.theme = byId("night")` → `read(write(tree)).theme.id == "night"`.
  - `classicIsNotWritten`: the written text has no `theme=`.
  - `unknownThemeReadsAsClassic`: hand-edit `theme="nope"` into written text → `CLASSIC`, no exception.
  - `freeMindGetsTheDrawnColors`: Ocean map, untouched child → `FreeMind.write` has `BACKGROUND_COLOR` of the
    child's `fillOf` (lower-case hex as FreeMind writes it today).
- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.MindMapViewFileThemeTest'`
- [ ] **Step 3: Implement:** `theme="<id>"` attribute on the file's top element, only when not Classic; read with
  `MapThemes.byId`. FreeMind writes `fillOf`/`textColorOf` instead of stored colors.
- [ ] **Step 4: Run them and see them pass**, then `./gradlew :library:jvmTest`.
- [ ] **Step 5: Commit** `feat: save a map's theme in its file`

---

### Task 5: Theme picker and fonts in the desktop app

**Files:**
- Create: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/dialog/ThemeDialog.kt`
- Modify: `desktop/.../menuBar/ToolMenu.kt` (item `Map Theme...` above Import), `MindMapApp.kt` (dialog state,
  font loading ~line 610–620 and `fontFamily` ~line 897), `NodeFont.kt`, `dialog/PreferencesDialog.kt` (label),
  `dialog/EditNodeDialog.kt` (swatch label)
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/ThemeFontTest.kt`

**Interfaces:**
- Consumes: `MapThemes.all`, `MindMapState.setTheme`, `tree.theme`.
- Produces: `internal fun nodeFontFor(theme: MapTheme, preference: NodeFont): NodeFont` in `NodeFont.kt`.

- [ ] **Step 1: Write the failing tests** in `ThemeFontTest`:
  - `classicUsesThePreference`: `nodeFontFor(CLASSIC, NodeFont.LORA) == LORA`.
  - `themeFontWins`: `nodeFontFor(byId("paper"), NodeFont.INTER) == LORA`.
  - `everyThemeFontIsANodeFont`: for each theme with a `fontName`, a `NodeFont` with that `label` exists.
- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :desktop:jvmTest --tests '*ThemeFontTest'`
- [ ] **Step 3: Implement** `nodeFontFor` (match `NodeFont.label`), and load the family from
  `nodeFontFor(state.tree.theme, preferences.nodeFont)`, reloading when either changes. `ThemeDialog`: an
  `AppDialog` with one card per theme (a small `Canvas` preview: background, root oval, two main pills, two sub pills
  and branch strokes in the theme's colors, the name under it in the theme's font). Clicking a card calls
  `state.setTheme` at once; the current one is outlined; one `Close` button, laid out like `PreferencesDialog`'s
  buttons. Preferences label `Node font` → `Font of the Classic theme`. In the edit dialog, the shape's default
  swatch is labeled `Theme` (tooltip or text), since picking it means "follow the theme".
- [ ] **Step 4: Run them and see them pass**, then `./gradlew :desktop:jvmTest :desktop:compileKotlinWasmJs`.
- [ ] **Step 5: Check by hand** (`./gradlew :desktop:run`): open `feature_tour.mmv`, try each theme, undo, save,
  reopen; switch Preferences › Theme to Dark and see the map keep its theme. Record what you checked, or that you
  could not, in the ledger.
- [ ] **Step 6: Commit** `feat: pick a map theme from the Tool menu`

---

### Task 6: Docs

**Files:** `README.md` (library section near `exportImage`; `MapTheme`, `MindMapState.setTheme`, `tree.theme`,
`fillOf`), `docs/user-manual/user-manual.html` (a "Themes" section after Preferences or with Tool menu items, and
the Preferences font row), regenerate `desktop/manual/MindMapView-User-Manual.pdf` with
`docs/user-manual/make-pdf.sh`.

- [ ] **Step 1:** README: themes, the hand-set rule ("a color equal to the shape's default follows the theme"),
  saved in `.mmv` as `theme="<id>"`, exports drawn in the theme, dark window no longer changes the map.
- [ ] **Step 2:** Manual: picking a theme, what it changes and keeps, the `Theme` swatch, the font preference now
  applying to Classic only, and the dark-window behavior.
- [ ] **Step 3:** Run `docs/user-manual/make-pdf.sh`; expected `Wrote …MindMapView-User-Manual.pdf`.
- [ ] **Step 4: Commit** `docs: document map themes`
