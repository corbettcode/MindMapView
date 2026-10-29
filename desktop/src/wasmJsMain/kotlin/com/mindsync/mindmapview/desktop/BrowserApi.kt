@file:OptIn(ExperimentalWasmJsInterop::class)

package com.mindsync.mindmapview.desktop

import kotlin.js.Promise

// What the app asks of the browser, in JavaScript. A file is a JavaScript object with the name of the file
// and either a handle to write it back with, where the browser has the File System Access API, or the file
// itself.

/**
 * Asks the user for a file with one of the [extensions], as ".a,.b", resolving to the file or to null when none
 * is chosen.
 */
internal fun jsChooseFileToOpen(
    extensions: String
): Promise<JsAny?> = js(
    """(async () => {
        if (window.showOpenFilePicker) {
            try {
                const [handle] = await window.showOpenFilePicker({
                    types: [{
                        description: 'Mind maps',
                        accept: { 'application/x-mindmapview': extensions.split(',') }
                    }]
                });
                return { name: handle.name, handle: handle };
            } catch (e) {
                if (e.name === 'AbortError') return null;
                throw e;
            }
        }
        return await new Promise(resolve => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = extensions;
            input.onchange = () => resolve(input.files[0] ? { name: input.files[0].name, file: input.files[0] } : null);
            input.oncancel = () => resolve(null);
            input.click();
        });
    })()"""
)

/**
 * Asks the user where to save a file named [name], resolving to it or null when none is chosen. Without the File
 * System Access API it cannot be asked, and the file is downloaded under that name.
 */
internal fun jsChooseFileToSave(
    name: String
    , description: String
    , mimeType: String
    , extension: String
): Promise<JsAny?> = js(
    """(async () => {
        if (!window.showSaveFilePicker) return { name: name };
        try {
            const accept = {};
            accept[mimeType] = [extension];
            const handle = await window.showSaveFilePicker({
                suggestedName: name,
                types: [{ description: description, accept: accept }]
            });
            return { name: handle.name, handle: handle };
        } catch (e) {
            if (e.name === 'AbortError') return null;
            throw e;
        }
    })()"""
)

internal fun jsFileName(file: JsAny): String = js("file.name")

// The recent mind maps are kept by recent-maps.js, which the page loads before the app.

internal fun jsHasRecentMaps(): Boolean = js("window.mindMapViewRecent.isSupported()")

internal fun jsLoadRecentMaps(): Promise<JsAny?> = js("window.mindMapViewRecent.load()")

internal fun jsRecentMapKeys(): String = js("window.mindMapViewRecent.keys()")

internal fun jsAddRecentMap(file: JsAny): Promise<JsString> = js("window.mindMapViewRecent.add(file)")

internal fun jsRemoveRecentMap(key: String): Promise<JsString> = js("window.mindMapViewRecent.remove(key)")

internal fun jsRecentMapFile(key: String): JsAny = js("window.mindMapViewRecent.file(key)")

/** Resolves to "available", "missing" or "denied". */
internal fun jsRecentMapAccess(key: String): Promise<JsString> = js("window.mindMapViewRecent.access(key)")

internal fun jsHasHandle(file: JsAny): Boolean = js("!!file.handle")

internal fun jsReadFile(file: JsAny): Promise<JsString> =
    js("(file.handle ? file.handle.getFile() : Promise.resolve(file.file)).then(f => f.text())")

/** Writes the bytes in [base64] to [file], or downloads them under its name when it has no handle. */
internal fun jsWriteFile(
    file: JsAny
    , base64: String
): Promise<JsAny?> = js(
    """(async () => {
        const blob = new Blob([Uint8Array.from(atob(base64), c => c.charCodeAt(0))]);
        if (file.handle) {
            const writable = await file.handle.createWritable();
            await writable.write(blob);
            await writable.close();
            return null;
        }
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = file.name;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(link.href), 10000);
        return null;
    })()"""
)

/**
 * Resolves to the bytes at [url] in base64, or to null when they cannot be fetched, as another site may not
 * allow.
 */
internal fun jsFetchBase64(url: String): Promise<JsString?> = js(
    """fetch(url)
        .then(response => response.ok ? response.arrayBuffer() : null)
        .then(buffer => {
            if (buffer === null) return null;
            const bytes = new Uint8Array(buffer);
            let text = '';
            for (let i = 0; i < bytes.length; i += 0x8000) {
                text += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
            }
            return btoa(text);
        })
        .catch(() => null)"""
)

internal fun jsWriteClipboard(text: String): Promise<JsBoolean> = js(
    """navigator.clipboard
        ? navigator.clipboard.writeText(text).then(() => true, () => false)
        : Promise.resolve(false)"""
)

internal fun jsReadClipboard(): Promise<JsString?> = js(
    """navigator.clipboard && navigator.clipboard.readText
        ? navigator.clipboard.readText().catch(() => null)
        : Promise.resolve(null)"""
)

internal fun jsStorageGet(
    key: String
): String? = js("(() => { try { return localStorage.getItem(key); } catch (e) { return null; } })()")

internal fun jsStorageSet(
    key: String
    , value: String
): Unit = js("(() => { try { localStorage.setItem(key, value); } catch (e) { } })()")

internal fun jsSetTitle(title: String): Unit = js("document.title = title")

/** Opens [url] in a new tab, returning whether the browser allowed it. */
internal fun jsOpenInNewTab(url: String): Boolean = js("window.open(url, '_blank') !== null")

/** Has the browser ask before the page is left, as while the mind map has unsaved changes. */
internal fun jsSetLeaveWarning(isOn: Boolean): Unit = js(
    "window.onbeforeunload = isOn ? (event => { event.preventDefault(); event.returnValue = ''; }) : null"
)

/**
 * Gives the page's focus back to the canvas the app is drawn on. Text is typed into an element of its own,
 * and the page's focus stays off the canvas once that is gone, so keys no longer reach the app.
 */
internal fun jsFocusCanvas(): Unit = js(
    """(() => {
        const host = [...document.querySelectorAll('#app *')].find(element => element.shadowRoot);
        const canvas = host && host.shadowRoot.querySelector('canvas');
        if (canvas) canvas.focus();
    })()"""
)
