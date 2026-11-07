package com.mindsync.mindmapview.desktop

/**
 * A command and the [keys] that do it. Where [isPair], each of the keys does the one of the things the [command]
 * names in the same place, as for next and previous; otherwise any of the keys do it.
 */
data class KeyboardShortcut(
    val keys: List<String>
    , val command: String
    , val isPair: Boolean = false
)

/** The keyboard shortcuts of one part of the app, under a [title]. */
data class KeyboardShortcutGroup(
    val title: String
    , val shortcuts: List<KeyboardShortcut>
)

/** All of the keyboard shortcuts of the app, as the Keyboard Shortcuts dialog and the user manual list them. */
val KEYBOARD_SHORTCUTS = listOf(
    KeyboardShortcutGroup(
        "Files"
        , listOf(
            KeyboardShortcut(listOf("Ctrl+N"), "New map")
            , KeyboardShortcut(listOf("Ctrl+O"), "Open a map")
            , KeyboardShortcut(listOf("Ctrl+S"), "Save")
            , KeyboardShortcut(listOf("Ctrl+Shift+S"), "Save As")
            , KeyboardShortcut(listOf("Alt+F"), "Open the File menu")
        )
    )
    , KeyboardShortcutGroup(
        "Editing"
        , listOf(
            KeyboardShortcut(listOf("Tab"), "Add a child and type its text")
            , KeyboardShortcut(listOf("Enter"), "Add a sibling below and type its text")
            , KeyboardShortcut(listOf("Shift+Enter"), "Add a sibling above and type its text")
            , KeyboardShortcut(listOf("F2"), "Type the text of the selected node")
            , KeyboardShortcut(listOf("Any letter"), "Type the text of the selected node, starting with that letter")
            , KeyboardShortcut(listOf("Alt+Enter"), "Edit the selected node in the edit dialog")
            , KeyboardShortcut(listOf("+"), "Add a child in the edit dialog")
            , KeyboardShortcut(listOf("Ctrl+Z"), "Undo")
            , KeyboardShortcut(listOf("Ctrl+Y", "Ctrl+Shift+Z"), "Redo")
            , KeyboardShortcut(listOf("Ctrl+X"), "Cut")
            , KeyboardShortcut(listOf("Ctrl+C"), "Copy")
            , KeyboardShortcut(listOf("Ctrl+V"), "Paste")
            , KeyboardShortcut(listOf("Alt+F9"), "Edit attributes")
            , KeyboardShortcut(listOf("Ctrl+L"), "Add an arrow link from the selected node")
            , KeyboardShortcut(listOf("Ctrl+B"), "Add a boundary around the selected nodes")
            , KeyboardShortcut(
                listOf("Ctrl+M", "Ctrl+Shift+M")
                , "Move the branch clockwise / counterclockwise around the root"
                , isPair = true
            )
            , KeyboardShortcut(listOf("Ctrl+,"), "Open the Preferences")
        )
    )
    , KeyboardShortcutGroup(
        "While typing on the map"
        , listOf(
            KeyboardShortcut(listOf("Enter"), "Finish typing")
            , KeyboardShortcut(listOf("Tab"), "Finish typing and add a child")
            , KeyboardShortcut(listOf("Esc"), "Cancel: a new node goes, and a node's old text comes back")
            , KeyboardShortcut(listOf("Shift+Enter"), "A new line")
            , KeyboardShortcut(listOf("Ctrl+B", "Ctrl+I", "Ctrl+U"), "Bold / italic / underline", isPair = true)
        )
    )
    , KeyboardShortcutGroup(
        "In the edit dialog"
        , listOf(
            KeyboardShortcut(listOf("Enter"), "Save & Close (in the note: a new line)")
            , KeyboardShortcut(listOf("Shift+Enter", "Alt+Enter"), "A new line in the node's text")
        )
    )
    , KeyboardShortcutGroup(
        "Selecting and finding"
        , listOf(
            KeyboardShortcut(listOf("Arrow keys"), "Select the nearest node that way")
            , KeyboardShortcut(listOf("Ctrl+A"), "Select all nodes")
            , KeyboardShortcut(listOf("Esc"), "Clear the selection, or cancel adding an arrow link")
            , KeyboardShortcut(listOf("Ctrl+Left"), "Select the parent")
            , KeyboardShortcut(listOf("Ctrl+Right"), "Select the first child")
            , KeyboardShortcut(listOf("Ctrl+Down", "Ctrl+Up"), "Select the next / previous sibling", isPair = true)
            , KeyboardShortcut(listOf("Ctrl+F"), "Find")
            , KeyboardShortcut(listOf("F3", "Shift+F3"), "Find next / previous", isPair = true)
        )
    )
    , KeyboardShortcutGroup(
        "View and folding"
        , listOf(
            KeyboardShortcut(listOf("Ctrl+Alt+C"), "Center the selected node")
            , KeyboardShortcut(listOf("Space"), "Fold or unfold the selected node")
            , KeyboardShortcut(listOf("Ctrl+Space"), "Fold or unfold its children")
            , KeyboardShortcut(listOf("Alt+PageUp", "Alt+PageDown"), "Fold / unfold one level", isPair = true)
            , KeyboardShortcut(listOf("Alt+Home", "Alt+End"), "Fold / unfold all", isPair = true)
        )
    )
)
