# Emoji picker with categories, skin tones and search

## Why

The node emoji picker in the desktop and web app (`desktop/.../component/EmojiPicker.kt`) shows one flat list of
96 emojis (`DescriptionEmoji.EMOJIS` in the library). Finding an emoji in a particular group, like faces or hand
gestures, means scanning the whole mixed list. Many emojis also come out two-tone on Linux, because plain fonts
(DejaVu Sans, FreeSerif, Noto Sans Symbols2) are picked before the color emoji font. The user wants a picker that
works like a phone's: category tabs, every emoji in each category, skin tones, and search, all drawn in color.

## Goals

- Phone-style category tabs, each showing **every** emoji in that category (from Unicode's full emoji list).
- An **"In this map"** row showing the emojis the open map already uses, always visible above the tabs whichever
  category is shown (layout A in the mockup).
- **Skin tones**, as on a phone: press and hold an emoji that has tones to open a popup with the no-tone version and
  the five tones; release on one, or click one, to pick it. A quick click still picks the emoji at once.
- **Search** by name and keywords across all categories.
- Emojis draw **in color** in the picker and on map nodes, on every system, in the desktop and web app.
- Maps saved before this change keep working: their emoji is still recognized and shown as selected.

## Non-goals

- A "recently used across all maps" row. "In this map" covers reuse.
- Choosing a separate tone for each person in two-person emojis (🧑‍🤝‍🧑): only the single-tone versions are offered.
- Search in other languages than English.
- Changing the Android app (`:app`) or the Android `MindMapView`.

## Anti-goals

- Emojis that still show two-tone in the picker or on the map.
- A picker that needs the internet to show its emojis.
- Existing maps whose emoji is no longer recognized.

## The picker

Opened from the Emoji row in the edit node dialog, as today. From top to bottom:

1. **Search box**, focused when the picker opens. Typing replaces the category grid with matches from every
   category; clearing it brings the tabs back.
2. **None**, which removes the emoji.
3. **In this map**: the leading emoji of each node in the open map (the one the picker sets, as returned by
   `DescriptionEmoji.split()`), not emojis typed elsewhere in the text. In the order they first appear in the
   map, without duplicates. Hidden when the map has none. *(Order is my call.)*
4. **Category tabs**, one icon each, following Unicode's groups: smileys & emotion, people & body (gestures are
   here), animals & nature, food & drink, activities, travel & places, objects, symbols, flags. Unicode's
   "component" group (bare skin-tone and hair swatches) is left out.
5. **Grid** of the selected category, 8 columns, scrolling. A heading per Unicode subgroup is optional, the
   builder's call.

Clicking an emoji picks it and closes the picker. The current emoji is outlined, as today. *(My call: the picker
opens on the current emoji's category, or on smileys when the node has none.)*

### Skin tones

- Emojis that come in tones show a small corner mark. *(My call.)*
- Press and hold opens a popup with the six versions (no tone plus 🏻 🏼 🏽 🏾 🏿). Right-click opens it too, as
  holding a mouse button is unusual on desktop. *(Right-click is my call.)*
- Once a tone is picked for an emoji, the grid shows that version in its spot from then on, as phones do. This is
  remembered per emoji on that computer or browser (desktop preferences; browser local storage), not in the map.
  *(My call.)*

### Search

Matches the emoji's Unicode name and its CLDR keywords (English), so "happy", "smile" or "thumbs" find what you
would expect. Results show remembered skin tones like the grid.

## How it gets made (my calls)

- **Bundled color emoji font** in the desktop and web app, used for emoji text in the picker and passed to the map
  so node text draws emojis in color. Noto Color Emoji (OFL) is the default choice; the COLRv1 build is about
  a third of the size of the bitmap build and Skia draws both, so the builder picks whichever renders correctly on
  JVM desktop and wasm. The font ships with the app; nothing is fetched from the internet.
- **Emoji data generated from Unicode files**: `emoji-test.txt` (groups, subgroups, order, skin-tone variants,
  fully qualified forms) and CLDR `annotations` (keywords). A script in the repo regenerates the Kotlin data; the
  generated source is checked in so builds need no network.
- **Recognizing any emoji in a description**: `DescriptionEmoji.split()` must recognize any fully qualified emoji
  (including tones and ZWJ sequences), not just a fixed list, so emojis from the full set and maps saved with the
  old 96 keep working. Whether it uses the generated list or a pattern is the builder's call, as long as the
  existing `MindMapTest` emoji tests keep passing.

## Constraints

- Compose Multiplatform 1.12, Kotlin 2.4; the `desktop` module targets JVM and `wasmJs`.
- The library is published (1.0.0); changes to its public API should be additive.
- Follow the project's Kotlin style in `CLAUDE.md` (leading commas, K&R braces, 120 columns).

## Starting point (my call)

Build on the working tree as it stands: the uncommitted 96-emoji `DescriptionEmoji.EMOJIS` edit is superseded by
this work. The user's uncommitted `desktop/feature_tour.mmv` change is unrelated and is left out of these commits.

## Left to the builder

- How the bundled font is loaded on wasm (it has no system fonts) and how emoji runs get it on JVM desktop, where
  fontconfig would otherwise pick plain fonts first.
- Where the emoji data lives (library or desktop module), and the popup and grid details.
- The tab icons.
