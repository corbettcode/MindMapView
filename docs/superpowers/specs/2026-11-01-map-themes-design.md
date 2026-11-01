# Map themes

## Why

XMind lets a whole map take a ready-made look in one step. The user wants the same: a **theme** that restyles the
map itself. Today the only Theme setting is Preferences › Theme (System / Light / Dark), which switches the app
window; the look of the map comes from fixed defaults in `DrawInfo` (branch line colors, line color and width) and
from each shape's default fill and text color in `NodeShape`.

## Goals

- A **theme** is a named look for the whole map, picked from a **built-in set** that ships with the app (the user's
  choice). Custom themes are for later (the user's choice), so the design should not shut that door, but builds
  none of it.
- The theme is **saved in the map** (the `.mmv` file), so the map looks the same to anyone who opens it, as in
  XMind (the user's choice).
- **What a theme sets** (the user's list, plus the mockup they agreed with):
  - the **background** of the map;
  - the **fill and text colors** of the root, of main topics (children of the root) and of subtopics (everything
    below);
  - the **branch line colors**, one per branch from the root, taking the next color for each branch as today;
  - **line thickness** (the default line width, which `NodeLine` settings inherit);
  - **border style**: none or a line, with its width and color, per level as above *(per level is my call)*;
  - the **font** of node text.
- **Hand-set styling wins** (the user's choice: "keep my colors, theme only changes the rest"). A node's own colors,
  border, and line color, width or style, when set by hand, are kept whatever the theme.
- Changing the theme is **one undoable change**, like any other edit, and marks the map as modified. *(My call.)*
- A **picker** with a small preview of each theme, opened from a menu item, applies a theme as soon as it is chosen.
  *(My call; placement left to the builder, see below.)*
- **Starter set of about six themes** *(my call)*: **Classic** (exactly today's look), **Ocean**, **Night**,
  **Paper** (from the mockup in `.superpowers/brainstorm/*/content/theme-samples.html`), and two more of the
  builder's design, so the set covers light, dark, colorful and minimal looks.

## Non-goals

- Custom or user-made themes, importing or sharing themes.
- Changing node **shapes** or the **layout** (not asked for; *my call* to leave out).
- Themes for the app window: Preferences › Theme stays as it is (see "The app's dark mode and fonts").
- Theme support in FreeMind, OPML, Markdown or HTML exports beyond what they already carry. PNG, SVG and PDF
  exports draw the map as themed (see Exports).
- Android View (`MindMapView`) API beyond what falls out of the library changes.

## Anti-goals

- **A theme overwriting colors the user picked.** Applying a theme and then switching back must leave hand-colored
  nodes exactly as they were.
- **Existing maps changing look when opened.** A map saved before themes, or with no theme in its file, must look
  exactly as it does today (it is **Classic**).
- Applying a theme by rewriting every node's stored colors. The theme is resolved at draw time, so switching themes
  is cheap, undo is one step, and hand-set values stay distinguishable.

## How hand-set styling is told apart

- Lines and borders already distinguish "set" from "inherited" (`NodeLine` with inherited settings, `NodeBorder`
  with `NONE` as default). The theme supplies what is inherited at the top (today `DrawInfo.rootLineColors`,
  `lineWidth`) and the default border.
- **Node colors are always stored** today: every node carries `color` and `textColor`, starting as its shape's
  default (`NodeShape.defaultColor` / `defaultTextColor`), and `.mmv` writes them all. So:
  - From now on a node's fill and text color are each **optional**: unset means "follow the theme".
  - **Reading existing maps** *(my call)*: a stored color equal to the node's shape default reads as unset. The rare
    color a user picked that happens to equal the default will follow the theme. New files write only colors that
    are set.
  - New nodes start with no colors, so they follow the theme. The existing behavior where a new node takes its last
    sibling's look (commit `177f8b6`) copies only what that sibling has set by hand. *(My call.)*
  - The edit dialog's color pickers show the theme's color for an unset color, and offer a way back to "theme"
    once set. *(My call; the builder picks the control.)*
- `NodeShape.defaultColor` stays as what **Classic** gives each shape, so Classic is today's look exactly.

## Saving

- The `.mmv` file gains the map's theme as a stable id (e.g. `theme="ocean"`), written only when it is not Classic.
  Unknown ids read as Classic. *(My call.)*
- A theme is data (a Kotlin object listing the values above), not code paths, so a custom theme later is the same
  data saved with or beside the map.

## Exports

- PNG, SVG and PDF draw the map with its theme, background included, replacing today's fixed "light colors on
  white". *(My call: the export should look like the map.)*
- `.mmv` keeps the theme; FreeMind export writes resolved colors, so the map looks themed in FreeMind. *(My call.)*

## Library API

- The library owns themes, as it owns drawing: a `MapTheme` type, the built-in set, and the tree's current theme,
  with a `MindMapState` call to set it inside one undo step. Names and shape are left to the builder.

## The app's dark mode and fonts

- **The map keeps its theme's look in a dark window** (the user's choice). Preferences › Theme only changes the window
  around the map; the map always shows its theme's background and colors, as in XMind, so the screen and the
  exports match. A dark map is the Night theme. Today's dark-mode map drawing (`darkModeLineColor`, the gray lines
  and the dark canvas under `isSystemInDarkTheme()` in `MindMap`) is replaced by the theme's values.
- **The theme's font wins** (the user's choice): a themed map shows its theme's font to everyone who opens it.
  Preferences › Node font stays, but only sets the font of **Classic**, so maps without a theme look as they do
  today on each computer. *(Keeping it for Classic is my call.)* The preference's label says so.
- Theme fonts are the bundled ones (Inter, Lora, Nunito, Caveat, JetBrains Mono) or the platform default, since the
  web app has no other fonts and the file must look the same everywhere. *(My call.)*

## Left to the builder

- Where the Theme item lives (Tool menu, a Format menu, or the toolbar) and how the picker looks, in the style of the
  existing dialogs (`PreferencesDialog`, `DropdownPicker`).
- The two extra starter themes and the exact values of all six, keeping text readable on its fill (contrast at
  least 4.5:1 for node text).
- How unset colors are represented in `NodeData` and in the `.mmv` format, and the migration code, as long as the
  Anti-goals hold.
- How the theme reaches drawing (`DrawInfo`, `NodeShape`, `MindMap` parameters).
