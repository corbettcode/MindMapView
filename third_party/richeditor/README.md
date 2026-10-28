# Compose Rich Editor (patched)

The node description and note editors use [Compose Rich Editor](https://github.com/MohamedRejeb/compose-rich-editor),
by Mohamed Rejeb, under the Apache License 2.0 (see `LICENSE`).

This folder holds version **1.2.1**, copied unchanged from its published sources jars (`commonMain`, and
`desktopMain` and `wasmJsMain` as `jvmMain` and `wasmJsMain`), built for the desktop and web app only. The desktop
module uses it in place of the published library; the `library` module keeps the published one.

## Changes

- `RichTextState.displayStyle`: restyles the text the editor shows without changing it or what is saved. The app
  uses it to draw emojis in the bundled color emoji font, as on the desktop the system's plain fonts draw their own
  two-tone emojis first. Styling the text through the state's span styles instead would add undo steps as you type.
  The change is in `src/commonMain/.../model/RichTextState.kt` (`displayStyle` and `showAnnotatedString`), with tests
  in `src/commonTest/.../model/DisplayStyleTest.kt`. Worth offering upstream, so this copy can go.

## Updating

To move to a newer release, copy its sources jars in the same way, re-apply the change above and run
`./gradlew :richeditor:jvmTest`.
