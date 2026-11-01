# SVG, PDF, OPML and Markdown export

## Why

The app exports a map as PNG, an HTML outline and a FreeMind `.mm` file. The other main mind-map apps (XMind,
MindNode, Freeplane, SimpleMind) also export vector drawings and outlines that other tools read. The user wants
four more exports so a map can leave MindMapView without losing quality: **SVG** and **PDF** drawings of the map,
and **OPML** and **Markdown** outlines of it.

## Goals

- Four new items in **File › Export**, after the ones there today: **As SVG Drawing...**, **As PDF Document...**,
  **As OPML Outline...**, **As Markdown...**. Each one saves through the same `export(extension) { bytes }` path as
  PNG and HTML, with the file named after the map. *(Labels and order are my call.)*
- **SVG and PDF are real vector drawings** (the user's choice), not the PNG wrapped in another file. Shapes, lines
  and arrow links are vector paths. The text is drawn as text where the format allows, so it stays sharp and, in
  PDF, can be searched.
- **They show what the PNG shows** (the user's choice: "the whole map drawn on a page, like PNG"): the whole map
  whatever part is in view, the nodes that are shown (folded branches stay hidden), their lines, borders and arrow
  links, in the light-theme colors on white, with the same margin as the PNG (`IMAGE_MARGIN`).
- **Markdown round-trips with the Markdown importer** (the user's choice): exporting a map and importing the file
  with Tool › Import › Markdown gives back the same tree of nodes, with the same text, notes and attributes.
- **OPML** is a standard OPML 2.0 outline that common outliners (Workflowy, OmniOutliner, Logseq) can read.
  *(My call, approved in chat.)*
- The SVG, OPML and Markdown writers live in the library, next to `HtmlOutline` and `FreeMind`, so other apps using
  the library get them too. *(My call, approved in chat.)*

## Non-goals

- OPML import, SVG import, PDF import.
- Splitting a PDF across A4 or Letter sheets, page headers, or print settings.
- Exporting only the selected branch.
- PDF export in the web app.
- Making the existing PNG export vector, or changing how it looks.

## Anti-goals

- The SVG or PDF looking different from the PNG of the same map: other colors, other line shapes, nodes moved,
  text wrapped differently. They must come from the **same drawing code** as the screen and the PNG
  (`drawMap(...)` in the library), not a second hand-written drawing of the map that can drift from it.
- A Markdown export that the app's own importer reads back as a different tree.
- A big new dependency in the library or in the web app for the sake of PDF.

## SVG

- The graphics engine under Compose Multiplatform on desktop, web and iOS is Skia (through Skiko), and Skiko has
  `org.jetbrains.skia.svg.SVGCanvas`, which writes what is drawn on it as SVG. It is present in Skiko 0.150.1 for
  both the JVM and wasm.
- So the SVG export draws the map exactly as `mapImage` does today (white background, scale, translate by the map
  bounds, `drawMap`), but onto an `SVGCanvas` wrapped as a Compose `Canvas`, instead of onto an `ImageBitmap`. The
  map bounds, margin and colors are shared with `mapImage`, so the two cannot disagree.
- The SVG is sized in dp of the map at 1 px per dp (its own coordinates; it scales freely). *(My call.)*
- **API:** `MindMapState.exportSvg(): String?`, null until the map is shown, as `exportImage` is.
- **Platforms:** Skia is not under Compose on Android, so `SVGCanvas` lives in a source set shared by the Skiko
  targets (JVM, wasm, iOS). On Android `exportSvg` returns null. The library has only `commonMain` and
  `androidMain` today, so a new intermediate source set is needed. *(My call.)*
- **Text:** use `SVGCanvas`'s default of `<text>` elements, which name the font family. This keeps the text
  searchable and the file small, but a viewer without Lora, Inter, Nunito, Caveat or JetBrains Mono installed will
  substitute fonts. Check how emoji (Noto Color Emoji) come out; if they are dropped or drawn as boxes, convert text
  to paths (`SVGCanvas` has a flag for it) and note in the user manual that SVG text is not searchable. *(My call;
  the builder decides after trying both.)*

## PDF

- **Desktop only** (the user's choice). In the web app the menu item is not shown. *(Hiding rather than greying
  out is my call.)*
- Skiko exposes no PDF backend, so the PDF comes from a JVM library in the desktop app's `jvmMain`.
- **One page, sized to the map** plus its margin, not cut into sheets. *(My call, approved in chat.)*
- **Fonts are embedded**, so the PDF looks the same everywhere, including emoji where the library can manage it.
- **Recommended route:** convert the SVG from `exportSvg()` to PDF. This keeps one drawing of the map (see
  Anti-goals) and leaves the PDF code with nothing to know about mind maps. Candidates: Apache PDFBox with an SVG
  renderer (for example openhtmltopdf's SVG support or Batik with `pdfbox-graphics2d`), or Apache FOP's
  `PDFTranscoder`. Pick one that keeps text as text with embedded subset fonts, handles the five bundled font
  families from `desktop/fonts`, has an MIT-compatible license (Apache 2.0 is fine), and adds the least to the
  desktop download. *(The library choice is the builder's.)*
- If no SVG-to-PDF route draws the map faithfully, draw `drawMap` onto a `PictureRecorder` and play it into
  Java2D, then PDF through `pdfbox-graphics2d`. Go back to the user before writing a second drawing of the map by
  hand.

## Markdown

The writer mirrors `library/.../io/Markdown.kt`, whose doc comment is the spec of what it reads:

- The **root** is the document's single `#` heading, so the importer takes it as the root.
- The **children of the root** are `##` headings, and their children `###` headings. Everything deeper is a
  nested list (`-`, 4 spaces of indent per level) under its `###` heading. *(How many levels become headings is
  my call; three keeps headings readable for typical maps and lists for the rest.)*
- A node's **note** is written as Markdown paragraphs right after its heading or list item; under a list item it
  is indented to belong to it. A note that starts with bold text has to be written so the importer does not read it
  as a node of its own (see "A paragraph that starts with bold text" in the importer).
- **Attributes, and the importer stays as it is** (the user's choice). The importer never gives a table to the
  node it follows: each row becomes a child node, named after its first cell, with the other cells as that
  child's attributes, and a table whose first header is empty puts its rows straight under the heading or item.
  So:
  - A **leaf node with attributes** is written as a row of such a table under its parent (empty first header,
    attribute names as the other headers). Siblings next to each other that are leaves with attributes share one
    table, with the union of their attribute names as columns and empty cells where a node has none. A leaf
    sibling without attributes between them ends the table. *(Grouping is my call.)* Its note cannot go in a
    table row and is dropped, unless the builder finds a form the importer reads back onto that row's node.
  - A **node with children** loses its attributes in Markdown. The `.mmv`, FreeMind and OPML exports keep them,
    and the user manual says so.
  - The round-trip goal holds for everything else: tree shape, text, notes of nodes outside tables, and leaf
    attributes. Rows keep their order among their siblings only if the table sits where they were; the builder
    checks the importer keeps that order, or writes the table so it does.
- **Rich text:** bold, italic, strikethrough and links are written as Markdown (`**`, `*`, `~~`, `[text](url)`). A
  line break inside a description (Shift+Enter) is a hard break. Characters Markdown would read as markup are
  escaped.
- **Dropped:** shapes, colors, fonts, borders, line styles, emoji placement beyond the text itself, arrow links,
  folds and the side of the root a branch is on. Markdown cannot hold them, and the importer would not read them.
- **Notes are HTML** in the tree. The desktop's `noteToMarkdown` uses flexmark, which is JVM only, so the library
  needs its own small HTML-to-Markdown conversion for the rich text the app produces (`RichText` already parses
  it). Images in notes become `![alt](src)`. *(My call.)*
- **API:** `Markdown.write(tree): String` next to `Markdown.read`.

## OPML

- OPML 2.0: `<opml version="2.0"><head><title>` with the root's plain text, and `<body>` holding one `<outline>` for
  the root with every node nested below it. All nodes are written, those in folded branches too, as the HTML
  outline does. *(My call: the root is one outline, not dropped, so the file has a single top item to collapse.)*
- `text` is the node's plain text (no markup), XML-escaped.
- A node's **note** goes in `_note` as plain text with line breaks kept, the convention Workflowy, OmniOutliner and
  Logseq read. *(My call, approved in chat.)*
- A node's **attributes** become extra attributes on its `<outline>`. Names that are not valid XML names, or that
  clash with `text`, `_note` or other OPML attribute names, are made valid. *(My call; the builder picks the
  mangling.)*
- Folded nodes get no `_status` or similar; outliners differ too much. *(My call.)*
- **API:** `Opml.write(tree): String`, with `Opml.EXTENSION = "opml"`.

## Platforms

| Export | Desktop | Web | Library on Android |
|---|---|---|---|
| SVG | yes | yes | returns null |
| PDF | yes | not shown | not in library |
| OPML | yes | yes | yes |
| Markdown | yes | yes | yes |

## Testing

- **Markdown:** round-trip tests through `Markdown.read`, including deep trees past the heading levels, notes under
  headings and list items, notes starting with bold text, leaf attributes in tables (and attributes of nodes with
  children being dropped), rich text, markup characters in text, and
  multi-line descriptions. Also a test that `desktop/feature_tour.mmv` survives export and import with the same
  tree, apart from the attributes of nodes with children.
- **OPML:** the output parses as XML, has the right nesting, escaping, `_note` and attributes.
- **SVG:** the output parses as XML, its size matches the map bounds, and it has one shape per shown node, the
  arrow links, and the text of the nodes; folded nodes are absent. Following `ExportImageTest`.
- **PDF:** a JVM test that the file is a valid one-page PDF of the map's size, and that its text can be extracted
  and holds the node text.
- Open the SVG in a browser and the PDF in a PDF viewer for the feature tour map and compare them with the PNG by
  eye before calling it done.

## Docs

- README: add the new writers and `exportSvg` to the library section, and the new exports to the desktop and web
  notes (PDF desktop only).
- User manual (`docs/user-manual/user-manual.html`): the four exports, what each keeps and drops, and PDF being
  desktop only. Run `docs/user-manual/make-pdf.sh` afterwards.

## Left to the builder

- The PDF library and route, within the constraints above.
- SVG text as `<text>` or as paths, after checking emoji.
- The indentation of notes under list items, and keeping table rows in order among their siblings, as long as the round-trip
  tests pass.
- Attribute-name mangling in OPML.
- How the intermediate Skiko source set is set up in `library/build.gradle.kts`.
