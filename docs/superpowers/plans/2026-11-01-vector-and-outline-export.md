# SVG, PDF, OPML and Markdown Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four new File › Export items (SVG, PDF, OPML, Markdown) in the MindMapView desktop and web app, with
the SVG, OPML and Markdown writers in the library.

**Architecture:** OPML and Markdown are pure-Kotlin writers in `library/.../io/`, next to `HtmlOutline`. SVG reuses
the PNG export's drawing (`drawMap` in `MapImage.kt`) on a Skia `SVGCanvas`, in a new library source set shared by
the Skiko targets (JVM, wasm, iOS). PDF converts that SVG on the desktop JVM with Apache FOP's `PDFTranscoder`,
which keeps text as text and embeds the bundled fonts.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.1 (Skiko 0.150.1), Apache FOP transcoder
(desktop JVM only), Apache PDFBox (desktop tests only).

**Spec:** `docs/superpowers/specs/2026-11-01-vector-and-outline-export-design.md`

## Global Constraints

- No formatter is configured. Follow `CLAUDE.md` by hand: 4 spaces, max 120 columns, K&R braces, leading commas in
  multi-line parameter and argument lists (as in `Markdown.kt`), booleans named as questions.
- Comments explain *why*, never *what*. Public functions get a KDoc in the style of `HtmlOutline`/`Markdown`.
- Commit directly on `main`, one commit per task, messages `feat: ...` / `docs: ...` in the style of `git log`,
  ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- SVG and PDF come from the same drawing code as the screen and PNG (`DrawScope.drawMap` in `MindMap.kt`). Never
  write a second drawing of the map. If a step seems to need one, stop and ask.
- SVG and PDF show what the PNG shows: the whole map, only the nodes that are shown (folded branches stay hidden),
  light-theme colors on white, with the `IMAGE_MARGIN` (24 dp) margin.
- The Markdown importer (`Markdown.read`) is not changed (the user's choice).
- PDF is desktop only. Its menu item is not shown in the web app.
- No new dependency in the library or in the web app for PDF. The license must be compatible with MIT (Apache 2.0
  is fine).
- Menu labels, in this order after the existing three: `As SVG Drawing...`, `As PDF Document...`,
  `As OPML Outline...`, `As Markdown...`.

## Review Focus

1. **Text with Markdown characters** (`*`, `_`, `~`, `#`, `[`, `` ` ``, `|`, a leading `-`, or `1.`) has to come
   back from export plus import as the same text. Pinned in Task 2's `markupCharactersRoundTripAsText`.
2. **A map that is only a root**, with no children, note or attributes, has to export without errors to every
   format. Pinned in Task 1 `rootOnlyMapIsOneOutline`, Task 3 `rootOnlyMapIsOneHeading`, and Task 4
   `rootOnlyMapIsOneShape`.
3. **Exporting SVG or PDF before the map is shown** has to show an error, as PNG does today, and not crash.
   Handled in Task 5 and Task 6, through `exportSvg()` returning null (Task 4 `thereIsNoSvgBeforeTheMapIsShown`).
4. **A very large map in PDF:** a page has to stay within 14,400 pt on each side (the PDF viewer limit), with the
   drawing scaled down to fit. Pinned in Task 6 `largeMapIsScaledToTheLargestPage`.
5. **Text the bundled fonts cannot draw** (Chinese, as in `desktop/运维工作.xmind`, or emoji) has to produce a PDF,
   even if those glyphs fall back or are missing, never an exception. Pinned in Task 6 `textWithoutABundledFontStillMakesAPdf`.

---

### Task 1: OPML writer

**Files:**
- Create: `library/src/commonMain/kotlin/com/mindsync/library/io/Opml.kt`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/OpmlTest.kt`

**Interfaces:**
- Consumes: `Tree.getRootNode()`, `Tree.getNode(id)`, `NodeData.children/description/note`,
  `Tree.attributesOf(id): List<NodeAttribute>`, `RichText.parse(html).text`, `XmlParser(source).parse()` and
  `XmlElement(name, attributes, children)` (tests only).
- Produces: `object Opml { const val EXTENSION = "opml"; fun write(tree: Tree<*>): String }`

- [ ] **Step 1: Write the failing tests** in `OpmlTest`. Build trees with `Tree<Any>()` and `tree.addNode(...)` as
  `ExportImageTest.stateWithChildren` does, and parse the output with `XmlParser`.
  - `rootOnlyMapIsOneOutline`: `<opml version="2.0">`, the `<head><title>` is the root's plain text, and `<body>` has
    exactly one `<outline>` with `text` = the root's text and no children.
  - `nodesNestAsTheTree`: root › A › A1, root › B gives the same nesting in the same order.
  - `foldedBranchesAreWrittenToo`: a folded node's children are in the output.
  - `textIsPlainAndEscaped`: description `<b>Tom &amp; "Jerry"</b> &lt;3` gives `text` = `Tom & "Jerry" <3` after
    parsing, and the raw output has no `<b>`.
  - `noteIsPlainTextInNote`: note `<p>one</p><p>two</p>` gives `_note` = `"one\ntwo"`. A node without a note has
    no `_note`.
  - `attributesAreOutlineAttributes`: attributes `Owner=Ann`, `Due date=Friday` give `Owner="Ann"` and
    `Due_date="Friday"`.
  - `attributeNamesThatClashOrAreInvalidAreMadeValid`: names `text`, `_note`, `1st` and `a:b` give `attr_text`,
    `attr__note`, `_1st` and `a_b`. The same name twice gives `Owner` and `Owner_2`.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.OpmlTest'`
  Expected: compile failure, `Unresolved reference 'Opml'`.

- [ ] **Step 3: Implement `Opml.write(tree: Tree<*>): String`**
  Write `<?xml version="1.0" encoding="UTF-8"?>`, then `<opml version="2.0">` with `<head><title>` and `<body>`, and
  the root as the one top-level `<outline>`. Nodes are recursive, in `children` order, folded ones included. Text and
  notes are `RichText.parse(html).text`, trimmed, with newlines kept in `_note`. XML-escape `& < > "` and write a
  newline in an attribute as `&#10;`. Attribute-name rules, in this order: replace each character that isn't
  `[A-Za-z0-9_.-]` with `_`; put `_` in front of a name that doesn't start with a letter or `_`; put `attr_` in
  front of `text`, `_note`, `type` and any name already used on that outline; number repeats `_2`, `_3`.
  Emit no `_status`.

- [ ] **Step 4: Run the tests and see them pass** (same command). Expected: all pass.

- [ ] **Step 5: Commit** `feat: write a mind map as an OPML outline`

---

### Task 2: Rich text to Markdown

The Markdown writer's inline part, on its own because Review Focus 1 depends on it. The importer has **no
backslash escapes**. Its `inline()` keeps the text of a `` `code span` `` literally, without the backticks, so
literal markup characters are wrapped in backticks.

**Files:**
- Create: `library/src/commonMain/kotlin/com/mindsync/library/io/MarkdownText.kt`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MarkdownTextTest.kt`

**Interfaces:**
- Consumes: `RichText.parse(html): AnnotatedString` (span styles plus `RichText.LINK_TAG` annotations).
- Produces, all `internal object MarkdownText`:
  - `fun inline(html: String): String`: one line, with no newline in it. Line breaks become a single space.
  - `fun paragraphs(html: String): List<String>`: each line of the text as one Markdown paragraph, with blank lines
    dropped.
  - `fun cell(text: String): String`: plain text made safe for a table cell (backtick-wrapped markup, with `|` as
    `\|`).

- [ ] **Step 1: Write the failing tests.** Each test round-trips through the importer:
  `Markdown.read("# " + MarkdownText.inline(html), "t").getRootNode().description` compared with the expected HTML.
  - `boldItalicStrikeAndLinksAreMarkdown`: `<b>a</b> <i>b</i> <s>c</s> <a href="https://e.org">d</a>` round-trips
    to the same four tags around the same words.
  - `boldAndItalicTogether`: `<b><i>x</i></b>` round-trips with `x` both bold and italic.
  - `styledSpansFromTheEditorAreRead`: `<span style="font-weight: bold">a</span>` comes back as `<b>a</b>`.
  - `underlineColorAndSizeAreDropped`: `<u>a</u>` and `<span style="color: red">a</span>` give plain `a`.
  - `markupCharactersRoundTripAsText`: for each of `2*3*4`, `snake_case_name`, `~~x~~`, `[x](y)`, `` a`b ``,
    `# not a heading`, `- not an item`, `1. not an item`, `a | b`, `<https://x>`, `Tom & Jerry <3`: the imported
    plain text equals the input, except that a backtick comes back as `'` (it can't be expressed, my call).
  - `lineBreaksInADescriptionBecomeSpaces`: `a<br>b` gives `a b`.
  - `noteParagraphsAreItsLines`: `paragraphs("<p>one</p><p>two<br>three</p>")` is `["one", "two", "three"]`.
  - `noteImagesAreMarkdownImages`: `paragraphs("<p>see <img src=\"https://e.org/x.png\" alt=\"cat\"></p>")` contains
    `![cat](https://e.org/x.png)`.
  - `paragraphStartingWithBoldIsNotBoldAtItsStart`: `paragraphs("<b>Note:</b> text")` is `["Note: text"]`, so the
    importer doesn't read it as a node. This is lossy on purpose (my call, from the importer's bold-lead rule).
  - `cellEscapesPipes`: `cell("a|b")` is `a\|b`.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.MarkdownTextTest'`
  Expected: compile failure, `Unresolved reference 'MarkdownText'`.

- [ ] **Step 3: Implement `MarkdownText`.** Walk the `AnnotatedString` in segments, cut at every span and link
  boundary. Bold is `fontWeight >= FontWeight.Bold` and becomes `**`. Italic is `FontStyle.Italic`; use `_`
  when bold is also open and `*` otherwise, so `***` never appears. Strike is `TextDecoration.LineThrough` and
  becomes `~~`. Links become `[label](url)`. Open and close the markers only where the style changes. Put the
  markers outside leading and trailing spaces of a segment, because `*x *` doesn't match the importer. Wrap each
  run of `` *_~[]#<>| `` in backticks. At the start of a line, also wrap `-`, `+`, and a run of digits followed by
  `.` or `)`. Replace `` ` `` with `'`. Before parsing, replace each `<img>` with a private-use placeholder, and
  after escaping, turn it into `![alt](src)`.

- [ ] **Step 4: Run the tests and see them pass** (same command).

- [ ] **Step 5: Commit** `feat: turn rich text into Markdown the importer reads back`

---

### Task 3: Markdown writer

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/io/Markdown.kt`. Add `write` and update the object's
  KDoc. **Leave `read` and everything it uses unchanged.**
- Test: `library/src/commonTest/kotlin/com/mindsync/library/MarkdownWriteTest.kt`

**Interfaces:**
- Consumes: `MarkdownText.inline/paragraphs/cell` (Task 2), `Markdown.read(content, title)`.
- Produces: `fun Markdown.write(tree: Tree<*>): String`

**Layout (decided; the tests pin it):**
- The root is `# text`. Its children are `##` and theirs are `###`. A node at depth 3 is a list item `- text` at
  indent 0, with 4 more spaces per level below that.
- After a node's line comes its note. Under a heading, the note's paragraphs are each preceded by a blank line.
  Under a list item, they are indented by the item's indent + 4, with blank lines between paragraphs.
- **Leaf children that have attributes** are written as one table *right after the parent's line and note,
  before its other children*. The importer adds a table's rows to the most recent heading or list item, so the
  table can't sit between siblings. These children therefore move to the front of their siblings. **This is a
  known order change (my call; tell the user).** The table has an empty first header cell, then the attribute
  names. A name used n times in one row gives n columns, and the header is the union over the rows in first-seen
  order. Empty cells are written where a node has no value. A blank line comes before and after the table.
- A table row can't hold a note, so a table-row node's note is dropped (spec). A node with children keeps its
  children and loses its attributes (spec). An attribute with a blank value is dropped, because the importer
  skips blank cells.

- [ ] **Step 1: Write the failing tests.** Each one builds a tree, does `Markdown.read(Markdown.write(tree), "t")`,
  and compares an outline of the result (reuse `MarkdownTest`'s `outline()` idea: plain text of the description
  plus the note, indented by depth).
  - `rootOnlyMapIsOneHeading`: the output is `# Root\n`, and the round-trip gives the same root.
  - `deepTreeRoundTrips`: six levels deep, two siblings at each level, comes back the same, including the list
    levels.
  - `notesUnderHeadingsAndListItemsRoundTrip`: two-paragraph notes at depth 1 (heading) and depth 4 (list item).
  - `noteOfTheRootRoundTrips`.
  - `leafAttributesRoundTripAndComeFirst`: P has children A (leaf, `Owner=Ann`), B (has children), and C (leaf,
    `Owner=Bo`, `Due=Fri`). The result is P › [A, C, B] with the same attributes on A and C.
  - `attributesOfANodeWithChildrenAreDropped`.
  - `richTextAndMarkupRoundTripInHeadingsListsAndCells`: uses the strings from Task 2's markup test, at depth 1,
    depth 4, and as an attribute value.
  - `featureTourRoundTrips`: read `desktop/feature_tour.mmv` with `MindMapViewFile.read` (as `MindMapViewFileTest`
    reads files; if commonTest can't read a file, put this one test in `library/src/jvmTest`). Then compare
    outlines, allowing only the documented changes: leaf-with-attribute children moved to the front, and
    attributes on nodes with children dropped.

- [ ] **Step 2: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.MarkdownWriteTest'`
  Expected: compile failure, `Unresolved reference 'write'`.

- [ ] **Step 3: Implement `Markdown.write`** following the layout above. Also run `MarkdownTest` to check that
  `read` hasn't changed.

- [ ] **Step 4: Run the tests and see them pass**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.Markdown*'`

- [ ] **Step 5: Commit** `feat: write a mind map as Markdown that imports back as the same map`

---

### Task 4: SVG drawing in the library

**Files:**
- Modify: `library/build.gradle.kts` (sourceSets block, around lines 168–190)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MapImage.kt`
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMapState.kt` (next to `exportImage`, ~line 813)
- Create: `library/src/skikoMain/kotlin/com/mindsync/library/MapSvg.skiko.kt`
- Create: `library/src/androidMain/kotlin/com/mindsync/library/MapSvg.android.kt`
- Test: `library/src/jvmTest/kotlin/com/mindsync/library/ExportSvgTest.kt`

**Interfaces:**
- Consumes: `mapBounds`, `IMAGE_MARGIN` and `drawMap` as `mapImage` uses them today.
- Produces:
  - In `MapImage.kt`: `internal fun DrawScope.drawExportedMap(state: MindMapState, bounds: Rect, measureTextSize:
    MeasureTextSize)`, which draws the white background, translates by `bounds` and calls `drawMap`. Also
    `internal fun Density.exportBounds(tree: Tree<*>): Rect` (rename `mapBounds`). `mapImage` is rewritten to use
    both, with no change in output; `ExportImageTest` must still pass unchanged.
  - `internal expect fun mapSvg(state: MindMapState, density: Density, measureTextSize: MeasureTextSize): String?`
    in commonMain. The Android actual returns `null`.
  - `fun MindMapState.exportSvg(): String?` (a member, KDoc like `exportImage`), null until the map is shown.

- [ ] **Step 1: Add the `skikoMain` source set.** In `sourceSets`:
  `val skikoMain by creating { dependsOn(commonMain.get()) }`, and `jvmMain`, `wasmJsMain`, `iosArm64Main` and
  `iosSimulatorArm64Main` each `dependsOn(skikoMain)`. If the default hierarchy template objects, use
  `applyDefaultHierarchyTemplate { common { group("skiko") { withJvm(); withWasmJs(); group("ios") { withIos() } } } }`
  instead. Skia classes come through `compose.ui` on those targets. Only if `org.jetbrains.skia` doesn't resolve,
  add `org.jetbrains.skiko:skiko` at the version Compose 1.12.1 uses (0.150.1) to `skikoMain.dependencies`.
  Check: `./gradlew :library:compileKotlinJvm :library:compileKotlinWasmJs` succeeds.

- [ ] **Step 2: Write the failing tests** in `ExportSvgTest`, set up like `ExportImageTest.stateWithChildren`
  (`runComposeUiTest`), parsing with `XmlParser`.
  - `thereIsNoSvgBeforeTheMapIsShown`: `exportSvg()` is null.
  - `rootOnlyMapIsOneShape`: a valid `<svg>` with the root's text in a `<text>` element.
  - `svgIsTheSizeOfTheMapInDp`: the `width`/`height` of `<svg>` equal the PNG's size from `exportImage(pixelsPerDp =
    1f)`, within 1.
  - `nodeTextsAreText`: each shown node's description appears as `<text>` content.
  - `foldedNodesAreNotDrawn`: after `toggleFold()` on a node with a child, the child's text is absent.
  - `arrowLinksAreDrawn`: adding an arrow link adds at least one `<path>`.
  - `backgroundIsWhite`: the first drawn element is a rectangle filled `#FFFFFF` (or `white`) covering the whole
    SVG.

- [ ] **Step 3: Run them and see them fail**
  Run: `./gradlew :library:jvmTest --tests 'com.mindsync.library.ExportSvgTest'`

- [ ] **Step 4: Implement.** The skiko actual: `DynamicMemoryWStream()`, then
  `SVGCanvas.make(org.jetbrains.skia.Rect.makeWH(w, h), stream, convertToPaths = false, prettyXML = true)` with
  `w`, `h` = bounds in dp (1 px per dp). Then `CanvasDrawScope().draw(density, LayoutDirection.Ltr,
  skiaCanvas.asComposeCanvas(), Size(w, h)) { scale(1 / density.density, Offset.Zero) { drawExportedMap(...) } }`.
  **Close the SVG canvas before** `stream.detachAsData()` (it writes `</svg>` on close), then decode UTF-8.

- [ ] **Step 5: Check emoji by eye.** Write the SVG of `desktop/feature_tour.mmv` to a scratch file (a throwaway
  test or `main`) and open it in Chrome and Firefox. If emoji are missing or drawn as boxes, switch to
  `convertToPaths = true`, update `nodeTextsAreText` to check for `<path>` instead, and note in the commit message
  that SVG text is paths. Record which choice you made for Task 7.

- [ ] **Step 6: Run the tests and see them pass**, together with `ExportImageTest`:
  `./gradlew :library:jvmTest --tests 'com.mindsync.library.Export*'`
  Also check that the other targets still compile: `./gradlew :library:compileKotlinWasmJs :library:compileAndroidMain`
  (use the Android compile task that `./gradlew :library:tasks --all | grep -i compile` lists).

- [ ] **Step 7: Commit** `feat: export the mind map as an SVG drawing`

---

### Task 5: SVG, OPML and Markdown in the Export menu

**Files:**
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/MapFormats.kt:71-72`. Add
  `SVG_EXTENSION = ".svg"`, `OPML_EXTENSION = ".opml"`, `MARKDOWN_EXTENSION = ".md"`.
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/menuBar/FileMenu.kt`, in `FileActions`
  and in the export list at lines ~100–104.
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/MindMapApp.kt`, `fileActions` at ~385.

**Interfaces:**
- Consumes: `state.exportSvg()` (Task 4), `Opml.write` (Task 1), `Markdown.write` (Task 3), and the existing
  `export(extension) { bytes }` helper.
- Produces: `FileActions.exportSvg: () -> Unit`, `exportOpml: () -> Unit`, `exportMarkdown: () -> Unit`, and
  `exportPdf: (() -> Unit)?` (declared here and passed as `null` for now, so the menu skips it; Task 6 fills it in).

- [ ] **Step 1: Add the actions and menu items** in the order from Global Constraints. The PDF item appears only
  when `actions.exportPdf != null`. `exportSvg` handles null like `exportImage`: `error = "Could not export the
  drawing: the mind map is not shown yet"`.

- [ ] **Step 2: Build and run both apps**
  Run: `./gradlew :desktop:jvmTest :desktop:run`. Export `feature_tour.mmv` to all three formats. Open the SVG
  in a browser, the OPML in a text editor, and import the `.md` back with Tool › Import › Markdown.
  Then `./gradlew :desktop:wasmJsBrowserDevelopmentRun` and check that the three downloads work in Chrome.

- [ ] **Step 3: Commit** `feat: export as SVG, OPML and Markdown from the File menu`

---

### Task 6: PDF on the desktop

**Files:**
- Modify: `gradle/libs.versions.toml`. Add `fop = "2.10"` (or the latest 2.x) and `pdfbox = "3.0.5"` (or the
  latest 3.0.x), plus the libraries `fop-transcoder = { group = "org.apache.xmlgraphics", name =
  "fop-transcoder-allinone", version.ref = "fop" }` and `pdfbox = { group = "org.apache.pdfbox", name = "pdfbox",
  version.ref = "pdfbox" }`.
- Modify: `desktop/build.gradle.kts`. Add `jvmMain.dependencies { implementation(libs.fop.transcoder) }` and
  `jvmTest` `implementation(libs.pdfbox)`.
- Create: `desktop/src/jvmMain/kotlin/com/mindsync/mindmapview/desktop/PdfExport.kt`
- Modify: `AppPlatform.kt` (interface), `DesktopPlatform.kt`, `wasmJsMain/.../WebPlatform.kt`, `MindMapApp.kt`
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/PdfExportTest.kt`

**Interfaces:**
- Consumes: the SVG string from `state.exportSvg()` (Task 4), and the font files on the desktop classpath root
  (`desktop/fonts` is a resources dir: `Lora-Regular.ttf`, `Inter-*.ttf`, `Nunito-*.ttf`, `Caveat-*.ttf`,
  `JetBrainsMono-*.ttf`, `NotoColorEmoji.ttf`).
- Produces:
  - `internal fun pdfFromSvg(svg: String): ByteArray` in `PdfExport.kt` (throws `IOException` on failure).
  - `AppPlatform.pdfFromSvg: ((svg: String) -> ByteArray)?`. On the desktop it wraps `pdfFromSvg` and turns
    exceptions into `MapFileException`, as `encodePng` does. On the web it is `null`.
  - `MindMapApp` passes `exportPdf = platform.pdfFromSvg?.let { ... }` to `FileActions`, using `PDF_EXTENSION =
    ".pdf"` (add it to `MapFormats.kt`).

- [ ] **Step 1: Spike the route (throwaway, not committed).** Convert the feature tour SVG with FOP's
  `org.apache.fop.svg.PDFTranscoder`. Configure it with a `DefaultConfiguration` whose `fonts` has a `directory`
  pointing at a temp folder holding the bundled `.ttf` files copied out of the resources. Check with PDFBox's
  `PDFTextStripper` that node text is extractable, and check by eye in a PDF viewer that the Lora and Inter text
  looks like the SVG. Write down the size FOP adds to `./gradlew :desktop:packageDistributionForCurrentOS`.
  **If text comes out as paths, the fonts don't apply, or FOP adds more than about 15 MB, stop and report back to
  the user before going on** (spec: "Go back to the user before writing a second drawing").

- [ ] **Step 2: Write the failing tests** in `PdfExportTest`, using a small SVG from a fixed string with a `<rect>`
  and `<text font-family="Inter">Hello map</text>`, plus one SVG from `exportSvg()` of a two-child map (set up as
  in `ExportImageTest`).
  - `pdfHasOnePageOfTheDrawingsSize`: PDFBox loads it, it has 1 page, and the `MediaBox` is the SVG's width and
    height converted at FOP's pixel unit, within 1 pt.
  - `textIsSearchable`: `PDFTextStripper` text contains `Hello map` and the node descriptions.
  - `fontsAreEmbedded`: every font on the page has `isEmbedded == true`.
  - `largeMapIsScaledToTheLargestPage`: an SVG 30000 × 1000 gives a page whose longer side is ≤ 14,400 pt, with the
    aspect ratio kept within 1%.
  - `textWithoutABundledFontStillMakesAPdf`: `<text>运维工作 😀</text>` gives a valid 1-page PDF with no exception.

- [ ] **Step 3: Run them and see them fail**
  Run: `./gradlew :desktop:jvmTest --tests 'com.mindsync.mindmapview.desktop.PdfExportTest'`

- [ ] **Step 4: Implement `pdfFromSvg`** with the configuration from the spike. Copy the fonts out once per run into
  a temp directory (`deleteOnExit`, as `userManualFile` in `DesktopPlatform.kt` does). Scale large drawings by
  setting `KEY_WIDTH`/`KEY_HEIGHT` on the transcoder, keeping the aspect ratio, when either side would exceed
  14,400 pt. Then wire `AppPlatform.pdfFromSvg`, `DesktopPlatform`, `WebPlatform` (`null`) and `MindMapApp`.
  Handle the "not shown yet" error as `exportSvg` does.

- [ ] **Step 5: Run the tests and see them pass**, then `./gradlew :desktop:jvmTest` for the whole module, and
  `./gradlew :desktop:compileKotlinWasmJs` to confirm the web build has no FOP in it.

- [ ] **Step 6: Check by eye.** In `./gradlew :desktop:run`, export `feature_tour.mmv` as PNG, SVG and PDF, and
  compare them side by side: same layout, colors, lines, arrow links and fonts. Note how emoji look in the PDF for
  Task 7. In `wasmJsBrowserDevelopmentRun`, check that the PDF item isn't in the menu.

- [ ] **Step 7: Commit** `feat: export the mind map as a PDF document on the desktop`

---

### Task 7: README and user manual

**Files:**
- Modify: `README.md`, in the library section (near `exportImage`, `HtmlOutline`) and the web-app differences list
- Modify: `docs/user-manual/user-manual.html`, in "Exporting" (~line 692)
- Regenerate: `desktop/manual/MindMapView-User-Manual.pdf` via `docs/user-manual/make-pdf.sh`

- [ ] **Step 1: README.** Document `state.exportSvg()` (null until shown, null on Android, 1 px per dp,
  text as text or paths per Task 4), `Opml.write(tree)` (`_note`, attributes as outline attributes), and
  `Markdown.write(tree)` (round-trips with `read`, with the three documented losses: leaf-with-attribute children
  move to the front, nodes with children lose their attributes, table rows lose their notes; plus line breaks in
  descriptions become spaces and backticks become `'`). In the web list, add: "PDF export is only in the desktop
  app; print the SVG to PDF from the browser instead."

- [ ] **Step 2: User manual.** One paragraph per new export, saying what it keeps and drops (same facts as the
  README, in user words), including the emoji outcome from Task 6, and that PDF is desktop only.

- [ ] **Step 3: Regenerate the PDF:** run `docs/user-manual/make-pdf.sh`. Expected: it exits 0 and the PDF's
  modified time is updated.

- [ ] **Step 4: Commit** `docs: document the SVG, PDF, OPML and Markdown exports`
