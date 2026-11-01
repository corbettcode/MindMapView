package com.mindsync.library.io

/**
 * Just enough of a JSON parser for the files of other mind map programs. An object is read as a
 * `Map<String, Any?>` in the order of its keys, an array as a `List<Any?>`, a number as a `Double`, and
 * `true`, `false` and `null` as themselves.
 */
internal class JsonParser(
    source: String
) {
    private val source = source.removePrefix("﻿")
    private var position = 0

    /** @throws IllegalArgumentException when the source is not JSON. */
    fun parse(): Any? {
        val value = value()
        skipWhitespace()
        if (position != source.length) throw malformed()
        return value
    }

    private fun value(): Any? {
        skipWhitespace()
        return when (peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> number()
        }
    }

    private fun obj(): Map<String, Any?> {
        position++
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return map
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') throw malformed()
            val key = string()
            skipWhitespace()
            if (next() != ':') throw malformed()
            map[key] = value()
            skipWhitespace()
            when (next()) {
                ',' -> continue
                '}' -> return map
                else -> throw malformed()
            }
        }
    }

    private fun array(): List<Any?> {
        position++
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return list
        }
        while (true) {
            list.add(value())
            skipWhitespace()
            when (next()) {
                ',' -> continue
                ']' -> return list
                else -> throw malformed()
            }
        }
    }

    private fun string(): String {
        position++
        val text = StringBuilder()
        while (true) {
            when (val char = next()) {
                '"' -> return text.toString()
                '\\' -> text.append(
                    when (next()) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> unicodeEscape()
                        else -> throw malformed()
                    }
                )
                else -> text.append(char)
            }
        }
    }

    // A character outside the Basic Multilingual Plane is written as two escapes, one for each of its surrogates.
    private fun unicodeEscape(): Char {
        if (position + 4 > source.length) throw malformed()
        val code = source.substring(position, position + 4).toIntOrNull(16) ?: throw malformed()
        position += 4
        return code.toChar()
    }

    private fun number(): Double {
        val start = position
        while (position < source.length && source[position] in NUMBER_CHARS) position++
        return source.substring(start, position).toDoubleOrNull() ?: throw malformed()
    }

    private fun literal(
        word: String
        , value: Any?
    ): Any? {
        if (!source.startsWith(word, position)) throw malformed()
        position += word.length
        return value
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun peek(): Char = if (position < source.length) source[position] else throw malformed()

    private fun next(): Char = peek().also { position++ }

    private fun malformed() = IllegalArgumentException("Malformed JSON near character $position")

    private companion object {
        const val NUMBER_CHARS = "+-.0123456789eE"
    }
}
