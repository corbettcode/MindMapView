# Emoji Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat 96-emoji picker with a phone-style picker (category tabs, every emoji, "In this map" row,
skin tones, search) that draws emojis in color in the picker and on map nodes, on desktop and web.

**Architecture:** A Gradle task in the library generates the full emoji catalog (groups, names, keywords, skin-tone
variants) from checked-in Unicode files into `EmojiCatalog`, which also lets `DescriptionEmoji` recognize any emoji.
The desktop app bundles Noto Color Emoji, loads it per platform, and passes it to `MindMap` (which puts emoji runs in
that font) and to the new picker.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.1, Material 3, Kotlin/Wasm, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-28-emoji-picker-design.md`

## Global Constraints

- Compose Multiplatform 1.12, Kotlin 2.4; the `desktop` module targets JVM and `wasmJs`.
- The library is published (1.0.0); changes to its public API must be additive (deprecate, don't remove).
- Follow `CLAUDE.md` Kotlin style: leading commas on multi-line parameter lists, K&R braces, 120 columns, 4 spaces.
- Nothing is fetched from the internet at run time or build time; the font ships with the app.
- Unicode data is pinned to **Emoji 16.0** (`emoji-test.txt` 16.0, CLDR 46 `en.xml`), matching the font release.
- Commit directly on `main`. Never stage `desktop/feature_tour.mmv` (the user's unrelated change).
- Deviation from the spec's "generated source is checked in": the Unicode input files are checked in and Kotlin is
  generated at build time, like the existing `generateAppInfo` task. Builds still need no network.

## Review Focus

1. A map saved with an emoji that was in the old list but not offered by the new grid's display (e.g. 😀, 👍) must
   still be recognized as the node's emoji → Task 2 test `oldListEmojisAreStillRecognized`.
2. A description starting with a symbol that is not a fully qualified emoji ("© 2024", "#1", "1. step") must not lose
   its first character to the emoji slot → Task 1 `textSymbolsAreNotEmojis`, Task 2 `textSymbolIsNotSplitOff`.
3. Search typed with odd case and spacing ("  Thumbs   UP ") finds what a tidy query finds → Task 1
   `searchIgnoresCaseAndSpacing`.
4. The emoji font failing to load (missing resource, web fetch failing offline) leaves the app working with system
   fonts → Task 4 `missingFontGivesNull`.
5. A stored skin-tone preference that is corrupt or names an emoji/tone that doesn't exist is ignored, not a crash →
   Task 5 `unknownEmojiTonesAreIgnored`.

---

### Task 1: Emoji catalog generated from Unicode data

**Files:**
- Create: `library/emoji/emoji-test.txt` (from `https://unicode.org/Public/emoji/16.0/emoji-test.txt`)
- Create: `library/emoji/annotations-en.xml` (from
  `https://raw.githubusercontent.com/unicode-org/cldr/release-46/common/annotations/en.xml`)
- Create: `library/emoji/README.md` (two lines: where each file came from, and that they are Unicode-licensed)
- Modify: `library/build.gradle.kts` (new `generateEmojiData` task, registered as a `commonMain` source dir like
  `desktop/build.gradle.kts` does with `generateAppInfo`)
- Create: `library/src/commonMain/kotlin/com/mindsync/library/emoji/EmojiCatalog.kt`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/emoji/EmojiCatalogTest.kt`

**Interfaces:**
- Produces (public, package `com.mindsync.library.emoji`):
  ```kotlin
  data class Emoji(
      val value: String              // fully qualified, no skin tone
      , val name: String             // Unicode name, e.g. "thumbs up"
      , val subgroup: String
      , val keywords: List<String>   // CLDR keywords, lowercase
      , val tones: List<String>      // the 5 single-tone variants, light → dark, or empty
  )
  data class EmojiGroup(val name: String, val emojis: List<Emoji>)
  object EmojiCatalog {
      val groups: List<EmojiGroup>
      fun find(emoji: String): Emoji?            // base or any of its tone variants → the base Emoji
      fun groupOf(emoji: String): EmojiGroup?
      fun emojiAt(text: String, index: Int): String?   // longest emoji (base or tone variant) starting at index
      fun findEmojis(text: String): List<IntRange>     // UTF-16 ranges, inclusive, left to right, non-overlapping
      fun search(query: String): List<Emoji>
  }
  ```

**Generator rules** (in the Gradle task; it writes `com/mindsync/library/emoji/EmojiData.kt` into
`build/generated/emojiData/kotlin`):
- From `emoji-test.txt`, keep `fully-qualified` lines only; track `# group:` / `# subgroup:`; drop group `Component`.
- An entry whose name ends with `: <tone> skin tone` for exactly one tone (light, medium-light, medium, medium-dark,
  dark) is a tone variant of the entry named by the part before the colon; other toned entries (mixed tones, e.g.
  `light skin tone, dark skin tone`) are dropped.
- Keywords come from `<annotation cp="…">a | b | c</annotation>` lines (not `type="tts"`), looked up by the emoji
  with U+FE0F removed.
- Emit one line per base emoji: `group\tsubgroup\temoji\tname\tkw1|kw2\ttone1 tone2 tone3 tone4 tone5`, split into
  string literals of at most 8,000 UTF-16 chars each (the JVM limits one constant to 65,535 bytes), exposed as
  `internal val EMOJI_DATA: List<String>`. `EmojiCatalog` parses it lazily on first use.

- [ ] **Step 1: Download the two Unicode files** to `library/emoji/` with `curl -L -o`, and write the README.

- [ ] **Step 2: Write the failing tests** in `EmojiCatalogTest`:

```kotlin
@Test fun groupsFollowUnicodeOrderWithoutComponent() = assertEquals(
    listOf("Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places"
        , "Activities", "Objects", "Symbols", "Flags")
    , EmojiCatalog.groups.map { it.name }
)
@Test fun everyFaceIsThere() = assertTrue(EmojiCatalog.groups[0].emojis.size >= 160)
@Test fun thumbsUpHasFiveTones() =
    assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), EmojiCatalog.find("👍")!!.tones)
@Test fun aToneVariantFindsItsBase() = assertEquals("👍", EmojiCatalog.find("👍🏽")!!.value)
@Test fun twoPersonEmojiOffersOnlySingleTones() =
    assertEquals("🧑🏻‍🤝‍🧑🏻", EmojiCatalog.find("🧑‍🤝‍🧑")!!.tones.first())
@Test fun facesHaveNoTones() = assertEquals(emptyList(), EmojiCatalog.find("😀")!!.tones)
@Test fun keywordsComeFromCldr() = assertTrue("grin" in EmojiCatalog.find("😀")!!.keywords)
@Test fun gesturesAreInPeopleAndBody() = assertEquals("People & Body", EmojiCatalog.groupOf("👋🏽")?.name)
@Test fun emojiAtTakesTheLongest() {
    assertEquals("👨‍👩‍👧", EmojiCatalog.emojiAt("👨‍👩‍👧 x", 0))
    assertEquals("👍🏽", EmojiCatalog.emojiAt("a👍🏽", 1))
}
@Test fun findEmojisGivesRanges() = assertEquals(listOf(2..5, 9..10), EmojiCatalog.findEmojis("a 👍🏽 b ❤️"))
@Test fun textSymbolsAreNotEmojis() {
    assertEquals(emptyList(), EmojiCatalog.findEmojis("© 2024 #1 1. step"))
    assertNull(EmojiCatalog.emojiAt("🏽", 0))  // a bare tone swatch is a component, not an emoji
}
@Test fun searchMatchesNamesAndKeywords() {
    val thumbs = EmojiCatalog.search("thumbs").map { it.value }
    assertTrue("👍" in thumbs && "👎" in thumbs)
    assertTrue(EmojiCatalog.search("happy").isNotEmpty())
    assertEquals(emptyList(), EmojiCatalog.search("   "))
}
@Test fun searchIgnoresCaseAndSpacing() =
    assertEquals(EmojiCatalog.search("thumbs up"), EmojiCatalog.search("  Thumbs   UP "))
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :library:jvmTest --tests "com.mindsync.library.emoji.EmojiCatalogTest"`
Expected: compilation fails, `EmojiCatalog` unresolved.

- [ ] **Step 4: Write the generator task and `EmojiCatalog`.** Search: lowercase and split the query on
  whitespace; an emoji matches when every query word is a prefix of some word in its name or keywords; results keep
  catalog order. `emojiAt`: try lengths from the longest emoji's length down to 1 against a `Set` of every base and
  tone value.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :library:jvmTest --tests "com.mindsync.library.emoji.EmojiCatalogTest"`
Expected: PASS. Also `./gradlew :library:compileKotlinWasmJs` succeeds (large literals compile on wasm).

- [ ] **Step 6: Commit**

```bash
git add library/emoji library/build.gradle.kts library/src/commonMain/kotlin/com/mindsync/library/emoji \
  library/src/commonTest/kotlin/com/mindsync/library/emoji
git commit -m "feat: add the full Unicode emoji catalog to the library"
```

### Task 2: Recognize any emoji at the start of a description

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/util/DescriptionEmoji.kt`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/util/DescriptionEmojiTest.kt` (new)

**Interfaces:**
- Consumes: `EmojiCatalog.emojiAt(text, index)` (Task 1).
- Produces: `DescriptionEmoji.split` / `prepend` unchanged in signature, now accepting any catalog emoji.
  `DescriptionEmoji.EMOJIS` stays, marked `@Deprecated("Use EmojiCatalog", ReplaceWith("EmojiCatalog.groups"))`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun anyEmojiIsRecognized() = assertEquals("🫠" to "<p>hi</p>", DescriptionEmoji.split("<p>🫠 hi</p>"))
@Test fun aToneVariantIsRecognized() = assertEquals("👍🏽" to "ok", DescriptionEmoji.split("👍🏽 ok"))
@Test fun oldListEmojisAreStillRecognized() = listOf("😀", "😂", "😍", "😎", "😢", "😡", "👍", "👎", "❤️", "⭐")
    .forEach { assertEquals(it, DescriptionEmoji.split(DescriptionEmoji.prepend(it, "<p>x</p>")).first) }
@Test fun textSymbolIsNotSplitOff() = assertEquals(null to "<p>© 2024</p>", DescriptionEmoji.split("<p>© 2024</p>"))
```

- [ ] **Step 2: Run to see `anyEmojiIsRecognized` and `aToneVariantIsRecognized` fail**

Run: `./gradlew :library:jvmTest --tests "com.mindsync.library.util.DescriptionEmojiTest"`

- [ ] **Step 3: Make `split` use `EmojiCatalog.emojiAt(text, 0)`**; remove `longestFirst`; deprecate `EMOJIS`.

- [ ] **Step 4: Run the whole library suite**

Run: `./gradlew :library:jvmTest`
Expected: PASS, including the existing emoji tests in `MindMapTest`.

- [ ] **Step 5: Commit** `library/src/.../util/DescriptionEmoji.kt` and the new test:
  `feat: recognize any emoji in front of a node's description`.

### Task 3: Draw emojis on the map in a given font

**Files:**
- Create: `library/src/commonMain/kotlin/com/mindsync/library/util/EmojiFont.kt`
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/layout/MeasureTextSize.kt` (constructor + `measure`)
- Modify: `library/src/commonMain/kotlin/com/mindsync/library/MindMap.kt:150-174`
- Test: `library/src/commonTest/kotlin/com/mindsync/library/util/EmojiFontTest.kt`

**Interfaces:**
- Consumes: `EmojiCatalog.findEmojis` (Task 1).
- Produces:
  - `fun AnnotatedString.withEmojiFont(fontFamily: FontFamily?): AnnotatedString` — returns `this` when null, else
    adds `SpanStyle(fontFamily = fontFamily)` over each emoji range (end exclusive = `range.last + 1`).
  - `MeasureTextSize(..., emojiFontFamily: FontFamily? = null)` as a new last constructor parameter.
  - `MindMap(..., emojiFontFamily: FontFamily? = null, ...)`, placed right after `fontFamily`; added to the
    `remember(textMeasurer, density, fontFamily, emojiFontFamily)` keys so the map re-measures when the font arrives.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun emojiRunsGetTheFont() {
    val styled = AnnotatedString("a 👍🏽 b").withEmojiFont(FontFamily.Monospace)
    assertEquals(listOf(AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace), 2, 6))
        , styled.spanStyles)
}
@Test fun noFontLeavesTextAlone() =
    assertEquals(AnnotatedString("a 👍"), AnnotatedString("a 👍").withEmojiFont(null))
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :library:jvmTest --tests "*EmojiFontTest"`

- [ ] **Step 3: Implement `withEmojiFont`**, apply it in `MeasureTextSize.measure` to the parsed rich text before
  caching, and thread `emojiFontFamily` from `MindMap`.

- [ ] **Step 4: Run** `./gradlew :library:jvmTest` — Expected: PASS.

- [ ] **Step 5: Commit**: `feat: let the mind map draw emojis in their own font`.

### Task 4: Bundle Noto Color Emoji and use it in the app

**Files:**
- Create: `desktop/fonts/NotoColorEmoji.ttf` and `desktop/fonts/OFL.txt` (noto-emoji release `v2.047`, Emoji 16.0;
  the COLRv1 build `fonts/Noto-COLRv1.ttf` saved under this name, or the bitmap build `fonts/NotoColorEmoji.ttf`
  if COLRv1 fails the check in Step 6; license from `fonts/LICENSE`)
- Modify: `desktop/build.gradle.kts` (add `fonts` as a resources dir of `jvmMain` and `wasmJsMain`, so the file is on
  the JVM classpath at `/NotoColorEmoji.ttf` and served next to `index.html` on the web)
- Create: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/EmojiFont.kt`
- Create: `desktop/src/jvmMain/kotlin/com/mindsync/mindmapview/desktop/EmojiFont.jvm.kt`
- Create: `desktop/src/wasmJsMain/kotlin/com/mindsync/mindmapview/desktop/EmojiFont.wasmJs.kt`
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/MindMapApp.kt` (load once, provide, pass to
  `MindMap` at line ~780)
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/EmojiFontTest.kt`

**Interfaces:**
- Produces:
  - `const val EMOJI_FONT_FILE = "NotoColorEmoji.ttf"` (commonMain)
  - `expect suspend fun loadEmojiFontFamily(file: String = EMOJI_FONT_FILE): FontFamily?` — null on any failure.
    JVM: class-loader resource bytes → `androidx.compose.ui.text.platform.Font(identity = file, data = bytes)`.
    Web: reuse `jsFetchBase64(file)` from `BrowserApi.kt`, decode with `kotlin.io.encoding.Base64`, same `Font`.
  - `val LocalEmojiFontFamily = staticCompositionLocalOf<FontFamily?> { null }` (commonMain)
- `MindMapApp` keeps `emojiFontFamily` in state (null until loaded), loads it in a `LaunchedEffect(Unit)`, wraps
  content in `CompositionLocalProvider(LocalEmojiFontFamily provides emojiFontFamily)`, passes
  `emojiFontFamily = emojiFontFamily` to `MindMap`. On web it also calls
  `LocalFontFamilyResolver.current.preload(family)` so all other text (the description editor) falls back to it.

- [ ] **Step 1: Download the font and license** into `desktop/fonts/` with `curl -L -o`.

- [ ] **Step 2: Write the failing tests**

```kotlin
@Test fun bundledFontLoads() = runBlocking { assertNotNull(loadEmojiFontFamily()) }
@Test fun missingFontGivesNull() = runBlocking { assertNull(loadEmojiFontFamily("missing.ttf")) }
```

- [ ] **Step 3: Run to see them fail** — `./gradlew :desktop:jvmTest --tests "*EmojiFontTest"`

- [ ] **Step 4: Implement the loaders, the resource dirs and the `MindMapApp` wiring.**

- [ ] **Step 5: Run** `./gradlew :desktop:jvmTest :desktop:compileKotlinWasmJs` — Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Check color rendering in both apps** (use the `run` skill): add nodes whose emojis are 😀, 👍🏽,
  ❤️, ✋ and 🫩 (Emoji 16.0). All five must draw in color on the map, on the JVM desktop app and in the web app
  (`./gradlew :desktop:wasmJsBrowserDevelopmentRun`). If COLRv1 draws blank or two-tone on either, switch to the
  bitmap build and recheck. Note which build was kept in the commit message.

- [ ] **Step 7: Commit** `desktop/fonts`, `desktop/build.gradle.kts`, the three `EmojiFont` files, the test and
  `MindMapApp.kt`: `feat: bundle Noto Color Emoji so emojis draw in color`.

### Task 5: Remember the chosen skin tone per emoji

**Files:**
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/dialog/PreferencesDialog.kt`
  (`UserPreferences` gains `val emojiTones: Map<String, String> = emptyMap()`; nothing shown in the dialog)
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/UserPreferences.kt`
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/UserPreferencesTest.kt`

**Interfaces:**
- Consumes: `EmojiCatalog.find` (Task 1).
- Produces: `UserPreferences.emojiTones` (base emoji → chosen tone variant), stored under
  `UserPreferencesStore.EMOJI_TONES = "emojiTones"` as `base=variant` pairs joined by `,`. Reading keeps only pairs
  whose variant is in `EmojiCatalog.find(base)?.tones`.

- [ ] **Step 1: Write the failing tests** (using the file's existing `store()` helper and `stored` map)

```kotlin
@Test fun emojiTonesAreKept() {
    store().update(UserPreferences(emojiTones = mapOf("👍" to "👍🏽", "✋" to "✋🏿")))
    assertEquals(mapOf("👍" to "👍🏽", "✋" to "✋🏿"), store().preferences.emojiTones)
}
@Test fun unknownEmojiTonesAreIgnored() {
    stored[UserPreferencesStore.EMOJI_TONES] = "👍=👍🏽,😀=😀🏽,x=y,garbage"
    assertEquals(mapOf("👍" to "👍🏽"), store().preferences.emojiTones)
}
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :desktop:jvmTest --tests "*UserPreferencesTest"`
- [ ] **Step 3: Implement** the field, read and write.
- [ ] **Step 4: Run** `./gradlew :desktop:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit**: `feat: remember the skin tone chosen for each emoji`.

### Task 6: Picker logic

**Files:**
- Create: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/component/EmojiPickerModel.kt`
- Test: `desktop/src/jvmTest/kotlin/com/mindsync/mindmapview/desktop/EmojiPickerModelTest.kt`

**Interfaces:**
- Consumes: `EmojiCatalog`, `DescriptionEmoji.split`, `Tree.doPreorderTraversal`.
- Produces:
  - `fun emojisInMap(tree: Tree<*>): List<String>` — each node's leading emoji, preorder, distinct.
  - `fun Emoji.shown(emojiTones: Map<String, String>): String` — the remembered tone variant, or `value`.
  - `fun startGroup(selectedEmoji: String?): EmojiGroup` — the selected emoji's group, else the first group.
  - `val GROUP_ICONS: Map<String, String>` — tab icon per group: 😀 👋 🐶 🍎 🚗 ⚽ 💡 ❤️ 🏁, in group order.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun mapEmojisAreLeadingOnesInOrderWithoutRepeats() {
    val tree = Tree<Any>()  // root description is "🌱 root"
    // add children "<p>⭐ a</p>", "<p>b 🚀</p>", "<p>⭐ c</p>", "<p>👍🏽 d</p>" with the Tree API used in MindMapTest
    assertEquals(listOf("🌱", "⭐", "👍🏽"), emojisInMap(tree))
}
@Test fun rememberedToneIsShown() {
    val thumbs = EmojiCatalog.find("👍")!!
    assertEquals("👍🏽", thumbs.shown(mapOf("👍" to "👍🏽")))
    assertEquals("👍", thumbs.shown(emptyMap()))
}
@Test fun pickerStartsOnTheSelectedEmojisGroup() {
    assertEquals("People & Body", startGroup("👋🏿").name)
    assertEquals("Smileys & Emotion", startGroup(null).name)
}
@Test fun everyGroupHasAnIcon() = assertEquals(EmojiCatalog.groups.map { it.name }, GROUP_ICONS.keys.toList())
```

- [ ] **Step 2: Run to see them fail** — `./gradlew :desktop:jvmTest --tests "*EmojiPickerModelTest"`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit**: `feat: add the emoji picker's map emojis, tones and start category`.

### Task 7: The new picker

**Files:**
- Modify (rewrite): `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/component/EmojiPicker.kt`
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/dialog/EditNodeDialog.kt` (new params,
  passed through to `EmojiPicker` at line ~192)
- Modify: `desktop/src/commonMain/kotlin/com/mindsync/mindmapview/desktop/MindMapApp.kt` (the `EditNodeDialog` call
  at line ~937)

**Interfaces:**
- Consumes: Tasks 1, 4 (`LocalEmojiFontFamily`), 5, 6.
- Produces:
  ```kotlin
  @Composable fun EmojiPicker(
      selectedEmoji: String?
      , mapEmojis: List<String>
      , emojiTones: Map<String, String>
      , onToneChange: (base: String, variant: String) -> Unit   // variant == base means no tone
      , onSelect: (String?) -> Unit
  )
  ```
  `EditNodeDialog` gains `mapEmojis: List<String>`, `emojiTones: Map<String, String>`,
  `onToneChange: (String, String) -> Unit`. `MindMapApp` passes `emojisInMap(state.tree)`, `preferences.emojiTones`,
  and an `onToneChange` that updates the preferences store (removing the key when `variant == base`).

**Behaviour** (from the spec; the layout follows mockup A):
- The button still shows the selected emoji or "None". The open picker, top to bottom: search field (focused on open)
  → "None" → "In this map" grid (hidden when empty) → tab row of `GROUP_ICONS` → grid of the selected group,
  8 columns of 36 dp cells, fixed height of 6 rows, scrolling. While the search text is not blank, the tabs and
  group grid are replaced by a grid of `EmojiCatalog.search(text)`, or "No emojis found". Group and search cells
  both show `emoji.shown(emojiTones)`.
- Every emoji is drawn with `fontFamily = LocalEmojiFontFamily.current` (button, cells, tabs, tone popup).
- The selected emoji's cell is outlined, as today. Clicking a cell picks `emoji.shown(emojiTones)` and closes.
- Cells of emojis with tones show a small corner mark. Long press or right-click on such a cell opens a popup above
  it with the base and its 5 tones. Clicking one, or releasing a long press over one, calls `onToneChange` and
  `onSelect` with it and closes the picker. Right-click detection: in `pointerInput`, a press whose
  `currentEvent.buttons.isSecondaryPressed` is true.

- [ ] **Step 1: Rewrite `EmojiPicker` and thread the new parameters** through `EditNodeDialog` and `MindMapApp`.
- [ ] **Step 2: Build and run the tests** — `./gradlew :desktop:jvmTest :desktop:compileKotlinWasmJs`
  Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 3: Check it in the desktop and web apps** (use the `run` skill), going through each item:
  tabs switch categories and the smileys tab shows every face; "In this map" lists the map's leading emojis and stays
  visible on every tab; typing "thumbs" shows 👍 and 👎, and clearing the field brings the tabs back; long press and
  right-click on 👋 open the tone popup; picking 👋🏽 sets it, and reopening shows 👋🏽 in the grid; "None" removes
  the emoji; reopening on a node with 👋🏽 starts on the People & Body tab; all emojis draw in color.
- [ ] **Step 4: Commit**: `feat: pick emojis by category, with skin tones and search`.
