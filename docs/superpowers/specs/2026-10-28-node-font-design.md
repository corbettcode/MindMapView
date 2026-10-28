# Node font preference

## Why

The user wants to choose the font that node text is drawn in on the mind map. Today node text always uses the
platform's default font. The web app has no system fonts, so the choices are a small set of Google Fonts bundled
with the app, the same on desktop and web.

## Goals

- A **"Node font"** choice in the Preferences dialog, next to Theme and New node shape.
- Choices, in this order: **Default** (today's font, so nothing changes until the user picks), **Inter**
  (sans-serif), **Lora** (serif), **Nunito** (rounded), **Caveat** (handwritten), **JetBrains Mono** (monospace).
  *(The five fonts are my picks; the user asked me to choose a small set.)*
- The chosen font is used for **all node text on the map**: descriptions, the attributes table under a node, and
  exported PNG images. *(Attributes and export are my calls.)*
- Bold, italic and text sizes in node descriptions keep working: each font ships with regular, bold, italic and bold
  italic. Caveat has no italic, so its italic is synthesized (slanted).
- Emojis keep drawing in the bundled color emoji font.
- The map lays itself out again as soon as the font changes, since text widths change node sizes.

## Non-goals

- Fonts for menus, dialogs, the description editor or the note editor.
- A font per map or per node; fonts saved in the map file.
- Loading fonts installed on the user's computer.
- Changing the Android app or the library's defaults.

## Anti-goals

- A map that looks different to someone else because of this preference (it must not change the saved file).
- Fonts fetched from the internet at run time (they ship with the app).

## How it behaves (my calls)

- The preference is app-wide and remembered on that computer or browser, like the other preferences (desktop
  preferences; browser local storage), under its own key. An unknown stored value falls back to Default.
- In the dialog, each font's name is drawn in that font once it has loaded; until then, in the dialog's own font.
- Fonts load only when needed: the chosen font when the app starts or the choice changes, and all five when the
  Preferences dialog opens (to draw the names). Until the chosen font has loaded, node text uses Default. A font
  that fails to load leaves node text in Default; nothing crashes.
- The change takes effect when the user presses OK, like the other preferences.

## How it gets made (my calls)

- Font files: static TTFs (Regular, Bold, Italic, BoldItalic; Caveat Regular and Bold) from each family's official
  Google Fonts / upstream release, with their OFL licence files, in `desktop/fonts/` — already a resource directory
  for both the JVM app and the web app (see `desktop/build.gradle.kts` and the emoji font). Expected size: roughly
  2–3 MB in all.
- Loading reuses the emoji font's approach (`desktop/.../EmojiFont.kt` and its JVM and web `actual`s): read bytes
  from the classpath on the JVM, fetch next to `index.html` on the web, build
  `FontFamily(Font(identity, data, weight, style), …)`.
- The map already takes `MindMap(fontFamily = …)`, which re-measures when it changes; the app passes the loaded
  family (null for Default). No library change is needed.
- The dialog reuses its existing `ChoicePicker`, which needs a way to draw each choice's label in a given font.

## Constraints

- Compose Multiplatform 1.12, Kotlin 2.4; the `desktop` module targets JVM and `wasmJs`.
- Follow the project's Kotlin style in `CLAUDE.md` (leading commas, K&R braces, 120 columns).
- Commit directly on `main`.

## Left to the builder

- The exact font release versions and download sources (official ones only).
- How the per-choice font is passed to `ChoicePicker`.
- Whether the preference is an enum or a string key, as long as unknown values fall back to Default.
