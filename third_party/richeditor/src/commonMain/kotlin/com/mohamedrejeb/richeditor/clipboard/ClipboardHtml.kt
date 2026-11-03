package com.mohamedrejeb.richeditor.clipboard

/**
 * Returns [html] read from the clipboard as the program that put it there wrote it. On Linux, a program such as
 * IntelliJ offers UTF-8 HTML without saying so, and Java reads it as UTF-16, which turns each two bytes into one
 * character, mostly CJK ones. Such text has no tag left in it, and its UTF-16 bytes read as UTF-8 are the HTML.
 */
internal fun repairClipboardHtml(
    html: String
): String {
    if ('<' in html) return html
    return listOf(true, false)
        .map { isBigEndian -> utf16Bytes(html, isBigEndian).decodeToString() }
        .firstOrNull { it.trimStart().startsWith('<') }
        ?: html
}

private fun utf16Bytes(
    text: String
    , isBigEndian: Boolean
): ByteArray {
    val bytes = ByteArray(text.length * 2)
    text.forEachIndexed { i, char ->
        val high = (char.code shr 8).toByte()
        val low = char.code.toByte()
        bytes[2 * i] = if (isBigEndian) high else low
        bytes[2 * i + 1] = if (isBigEndian) low else high
    }
    return bytes
}
