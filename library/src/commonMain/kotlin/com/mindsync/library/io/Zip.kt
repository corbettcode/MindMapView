package com.mindsync.library.io

/**
 * Reads the files of a ZIP archive, which an XMind map is kept in. It is written here, not taken from the
 * platform, so that it reads the same on the desktop and in a browser. Only what such a map needs is read:
 * files that are stored or deflated, and no ZIP64 archives.
 *
 * @throws IllegalArgumentException when [bytes] are not a ZIP archive.
 */
internal class ZipArchive(
    private val bytes: ByteArray
) {
    private class Entry(
        val method: Int
        , val flags: Int
        , val compressedSize: Int
        , val size: Int
        , val localHeaderOffset: Int
    )

    private val entries: Map<String, Entry> = readCentralDirectory()

    val names: Set<String>
        get() = entries.keys

    /**
     * Returns the content of the file named [name], or null when the archive has none.
     *
     * @throws IllegalArgumentException when the file cannot be read.
     */
    fun read(name: String): ByteArray? {
        val entry = entries[name] ?: return null
        if (entry.flags and ENCRYPTED != 0) throw IllegalArgumentException("$name is encrypted")
        val header = entry.localHeaderOffset
        if (int32(header) != LOCAL_HEADER) throw malformed()
        val start = header + LOCAL_HEADER_SIZE + int16(header + 26) + int16(header + 28)
        val end = start + entry.compressedSize
        if (entry.compressedSize < 0 || end > bytes.size) throw malformed()
        return when (entry.method) {
            STORED -> bytes.copyOfRange(start, end)
            DEFLATED -> Inflater(bytes, start, end, entry.size).inflate()
            else -> throw IllegalArgumentException("$name is compressed in a way that cannot be read")
        }
    }

    private fun readCentralDirectory(): Map<String, Entry> {
        val end = endOfCentralDirectory()
        val count = int16(end + 10)
        var position = int32(end + 16)
        val entries = LinkedHashMap<String, Entry>()
        repeat(count) {
            if (int32(position) != CENTRAL_HEADER) throw malformed()
            val nameLength = int16(position + 28)
            val nameStart = position + CENTRAL_HEADER_SIZE
            if (nameStart + nameLength > bytes.size) throw malformed()
            entries[bytes.decodeToString(nameStart, nameStart + nameLength)] = Entry(
                method = int16(position + 10)
                , flags = int16(position + 8)
                , compressedSize = int32(position + 20)
                , size = int32(position + 24)
                , localHeaderOffset = int32(position + 42)
            )
            position = nameStart + nameLength + int16(position + 30) + int16(position + 32)
        }
        return entries
    }

    // The record is the last thing in the archive but for a comment, of up to 65535 bytes.
    private fun endOfCentralDirectory(): Int {
        val last = bytes.size - END_OF_CENTRAL_DIRECTORY_SIZE
        val first = maxOf(0, last - 0xFFFF)
        for (position in last downTo first) {
            if (int32(position) == END_OF_CENTRAL_DIRECTORY) return position
        }
        throw IllegalArgumentException("Not a ZIP archive")
    }

    private fun int16(position: Int): Int {
        if (position < 0 || position + 2 > bytes.size) throw malformed()
        return (bytes[position].toInt() and 0xFF) or ((bytes[position + 1].toInt() and 0xFF) shl 8)
    }

    private fun int32(position: Int): Int = int16(position) or (int16(position + 2) shl 16)

    private fun malformed() = IllegalArgumentException("The ZIP archive is damaged")

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
        const val LOCAL_HEADER_SIZE = 30
        const val CENTRAL_HEADER_SIZE = 46
        const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
        const val ENCRYPTED = 1
        const val STORED = 0
        const val DEFLATED = 8
    }
}

/**
 * Decompresses the deflated bytes of [input] from [start] to [end] (RFC 1951), which are [expectedSize] bytes
 * once decompressed. It decodes one bit at a time, which is slow but plenty for the text of a mind map.
 */
internal class Inflater(
    private val input: ByteArray
    , start: Int
    , private val end: Int
    , expectedSize: Int
) {
    private class Huffman(
        lengths: IntArray
        , offset: Int
        , count: Int
    ) {
        val counts = IntArray(MAX_BITS + 1)
        val symbols = IntArray(count)

        init {
            for (index in 0 until count) counts[lengths[offset + index]]++
            var left = 1
            for (length in 1..MAX_BITS) {
                left = (left shl 1) - counts[length]
                if (left < 0) throw IllegalArgumentException("The compressed data is damaged")
            }
            val offsets = IntArray(MAX_BITS + 2)
            for (length in 1 until MAX_BITS) offsets[length + 1] = offsets[length] + counts[length]
            for (index in 0 until count) {
                val length = lengths[offset + index]
                if (length != 0) symbols[offsets[length]++] = index
            }
        }
    }

    private var position = start
    private var output = ByteArray(expectedSize.coerceIn(MIN_BUFFER, MAX_INITIAL_BUFFER))
    private var size = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun inflate(): ByteArray {
        do {
            val isLast = bits(1) == 1
            when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LENGTH_CODE, FIXED_DISTANCE_CODE)
                2 -> dynamic()
                else -> throw malformed()
            }
        } while (!isLast)
        return output.copyOf(size)
    }

    private fun bits(count: Int): Int {
        while (bitCount < count) {
            if (position >= end) throw malformed()
            bitBuffer = bitBuffer or ((input[position++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        val value = bitBuffer and ((1 shl count) - 1)
        bitBuffer = bitBuffer ushr count
        bitCount -= count
        return value
    }

    // A stored block starts at a byte, so the bits left of the one before it are dropped.
    private fun stored() {
        bitBuffer = 0
        bitCount = 0
        if (position + 4 > end) throw malformed()
        val length = byteAt(position) or (byteAt(position + 1) shl 8)
        val complement = byteAt(position + 2) or (byteAt(position + 3) shl 8)
        if (length != complement.inv() and 0xFFFF) throw malformed()
        position += 4
        if (position + length > end) throw malformed()
        ensureRoom(length)
        input.copyInto(output, size, position, position + length)
        size += length
        position += length
    }

    private fun dynamic() {
        val lengthCount = bits(5) + 257
        val distanceCount = bits(5) + 1
        val codeLengthCount = bits(4) + 4
        if (lengthCount > MAX_LENGTH_CODES || distanceCount > MAX_DISTANCE_CODES) throw malformed()
        val codeLengths = IntArray(CODE_LENGTH_ORDER.size)
        for (index in 0 until codeLengthCount) codeLengths[CODE_LENGTH_ORDER[index]] = bits(3)
        val codeLengthCode = Huffman(codeLengths, 0, codeLengths.size)

        val lengths = IntArray(lengthCount + distanceCount)
        var index = 0
        while (index < lengths.size) {
            val symbol = decode(codeLengthCode)
            if (symbol < 16) {
                lengths[index++] = symbol
                continue
            }
            val value = if (symbol == 16) lengths.getOrNull(index - 1) ?: throw malformed() else 0
            val repeat = when (symbol) {
                16 -> 3 + bits(2)
                17 -> 3 + bits(3)
                else -> 11 + bits(7)
            }
            if (index + repeat > lengths.size) throw malformed()
            repeat(repeat) { lengths[index++] = value }
        }
        if (lengths[END_OF_BLOCK] == 0) throw malformed()
        codes(Huffman(lengths, 0, lengthCount), Huffman(lengths, lengthCount, distanceCount))
    }

    private fun codes(
        lengthCode: Huffman
        , distanceCode: Huffman
    ) {
        while (true) {
            val symbol = decode(lengthCode)
            when {
                symbol < END_OF_BLOCK -> {
                    ensureRoom(1)
                    output[size++] = symbol.toByte()
                }
                symbol == END_OF_BLOCK -> return
                else -> {
                    val lengthIndex = symbol - END_OF_BLOCK - 1
                    if (lengthIndex >= LENGTH_BASE.size) throw malformed()
                    val length = LENGTH_BASE[lengthIndex] + bits(LENGTH_EXTRA[lengthIndex])
                    val distanceIndex = decode(distanceCode)
                    if (distanceIndex >= DISTANCE_BASE.size) throw malformed()
                    val distance = DISTANCE_BASE[distanceIndex] + bits(DISTANCE_EXTRA[distanceIndex])
                    if (distance > size) throw malformed()
                    ensureRoom(length)
                    repeat(length) {
                        output[size] = output[size - distance]
                        size++
                    }
                }
            }
        }
    }

    // Canonical Huffman codes are read a bit at a time, the codes of each length following those before them.
    private fun decode(huffman: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (length in 1..MAX_BITS) {
            code = code or bits(1)
            val count = huffman.counts[length]
            if (code - count < first) return huffman.symbols[index + code - first]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw malformed()
    }

    private fun byteAt(index: Int): Int = input[index].toInt() and 0xFF

    private fun ensureRoom(count: Int) {
        if (size + count <= output.size) return
        output = output.copyOf(maxOf(output.size * 2, size + count))
    }

    private fun malformed() = IllegalArgumentException("The compressed data is damaged")

    private companion object {
        const val MAX_BITS = 15
        const val MAX_LENGTH_CODES = 286
        const val MAX_DISTANCE_CODES = 30
        const val END_OF_BLOCK = 256
        const val MIN_BUFFER = 64
        const val MAX_INITIAL_BUFFER = 1 shl 24

        val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val LENGTH_BASE = intArrayOf(
            3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195,
            227, 258
        )
        val LENGTH_EXTRA = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
        )
        val DISTANCE_BASE = intArrayOf(
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073,
            4097, 6145, 8193, 12289, 16385, 24577
        )
        val DISTANCE_EXTRA = intArrayOf(
            0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
        )

        val FIXED_LENGTH_CODE = Huffman(
            IntArray(288) {
                when {
                    it < 144 -> 8
                    it < 256 -> 9
                    it < 280 -> 7
                    else -> 8
                }
            }
            , 0
            , 288
        )
        val FIXED_DISTANCE_CODE = Huffman(IntArray(MAX_DISTANCE_CODES) { 5 }, 0, MAX_DISTANCE_CODES)
    }
}
