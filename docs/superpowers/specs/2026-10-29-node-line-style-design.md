# Node line style, color and width

## Why

FreeMind lets each node set the style, color and width of the line into it, and its branch inherits them. The user
wants the same: today every line from a parent to a child is a 5 dp bezier curve in its root branch's color, and a
FreeMind file's `<edge>` settings are dropped on import.

## Goals

- Each node can set the **style**, **color** and **width** of the line into it, each on its own, in the **edit
  dialog** next to the shape and colors (the user's choice).
- **Styles:** Linear (straight), Bezier (today's curve), Sharp Linear and Sharp Bezier (filled wedges, as wide as
  the line's width at the parent and narrowing to a point at the child, as FreeMind draws them).
- **Inheritance, like FreeMind:** a setting on a node applies to its line and to every line below it in its branch,
  except where a node below sets its own. Each of the three settings is inherited separately. Changing a node's
  setting redraws its whole branch at once.
- **Inherited** is a choice for each setting, so a node can go back to following its parent. *(My call.)*
- A map with nothing set looks exactly as today: Bezier, 5 dp, and each branch from the root in its color from
  `DrawInfo.rootLineColors`. These are what the top of the tree inherits. *(My call.)*
- The root has no line into it, but it can hold line settings too, which every branch inherits unless a node below
  sets its own, as in FreeMind, whose files often set an `<edge>` on the root. The root's edit dialog shows the
  same settings, for the lines of its branches. *(My call.)*
- Settings are saved in `.mmv` files, only on the nodes that set them; maps saved before this read as before.
- FreeMind import reads `<edge STYLE="linear|bezier|sharp_linear|sharp_bezier" COLOR="#rrggbb" WIDTH="thin|n"/>`
  on a node, and FreeMind export writes it for the nodes that set something. *(Export is my call.)*
- Undo and redo cover these settings like every other change to a node.
- Copy, cut and paste inside the app carry a node's own settings with it, as its attributes are carried today
  (`CopiedNode` in `MindMapState.kt`); pasted nodes get them under their new ids. *(My call.)*
- A chosen line color is drawn as chosen in both light and dark themes; only the default branch colors stay as they
  are today. *(My call.)*
- The add buttons and fold markers, which take their color from the line into the node today, follow the resolved
  line color, so a recolored branch looks whole. *(My call.)*
- The edit dialog acts on one node, as it does today, so there is no mixed-value case.

## Widths

Choices: **Thin, 1, 2, 4, 8**, as FreeMind offers; a number is that many dp, and Thin is the thinnest line drawn
clearly (about 1 dp). The default of 5 dp is not one of the choices; it is what "Inherited" gives at the top. FreeMind
`WIDTH` values read as the nearest choice, an unknown one as inherited. *(Mapping is my call.)*

## Non-goals

- A map-wide default line setting, or one in Preferences.
- Changing arrow links (they keep their own line style and thickness).
- Line settings on the node's right-click menu or the toolbar.
- Other FreeMind edge styles or attributes beyond these three.

## Anti-goals

- Maps saved before this, or with nothing set, looking different.
- A node's own setting silently lost on save, load, undo, cut and paste, or a FreeMind round trip.

## How it gets made (my calls)

- Follow the tree's pattern for per-node extras (`nodeAttributes`, `foldedIds` in `Tree.kt`, included in
  `TreeSnapshot` for undo): a map from node id to the node's own line settings, each of the three nullable for
  "inherited", plus a function that resolves the settings a line is drawn with by walking up to the root.
- Drawing stays in `MindMap.kt`'s line drawing, including the root's slanted sides, which keep their bend; the
  sharp styles fill the shape instead of stroking it. The line drawn while a node is dragged uses the same settings.
- `.mmv`: a small attribute set on `<node>` (for example `lineStyle`, `lineColor`, `lineWidth`), written only when
  set, read leniently (unknown values are inherited).
- The edit dialog's settings use the existing pickers' look: a choice row for style and width, the color picker for
  color, each with Inherited.

## Constraints

- The library is published; its public API changes must be additive: new parameters and fields come last with
  defaults. A data class that gains a field (for example `TreeSnapshot`) changes its `copy` for compiled callers;
  that is accepted, as 1.1.0 already changes `MindMap`'s signature. *(My call.)*
- Follow `CLAUDE.md` Kotlin style (leading commas, K&R braces, 120 columns).
- Commit directly on `main`.

## Left to the builder

- The data and API names, and where the resolving function lives.
- The exact wedge geometry of the sharp styles, including on the root's slanted sides and the line drawn while a
  node is dragged.
- The wording and layout of the dialog row.
