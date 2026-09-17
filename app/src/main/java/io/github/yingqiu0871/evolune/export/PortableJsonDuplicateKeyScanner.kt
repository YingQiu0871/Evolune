package io.github.yingqiu0871.evolune.export

/**
 * Detects duplicate object keys at any nesting level of a JSON document (frozen contract §17).
 *
 * The scan is intentionally self-contained (independent of the JSON library in use) and fully
 * decodes string escapes before comparing keys, so `"a"` and `"\u0061"` collide. Malformed input
 * is not classified here: the scan simply reports "no duplicate" and the strict decoder rejects
 * the document as malformed.
 */
internal object PortableJsonDuplicateKeyScanner {

    fun hasDuplicateKey(text: String): Boolean {
        val parser = Parser(text)
        return try {
            parser.parseValue(0)
            parser.duplicateFound
        } catch (_: Exception) {
            parser.duplicateFound
        }
    }

    private class Parser(private val text: String) {
        private var index = 0
        var duplicateFound = false

        fun parseValue(depth: Int) {
            require(depth <= MAX_DEPTH) { "nesting too deep" }
            skipWhitespace()
            when (peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't', 'f', 'n' -> parseLiteral()
                else -> parseNumber()
            }
        }

        private fun parseObject(depth: Int) {
            expect('{')
            skipWhitespace()
            if (peek() == '}') {
                index += 1
                return
            }
            val seen = HashSet<String>()
            while (true) {
                skipWhitespace()
                val key = parseString()
                if (!seen.add(key)) duplicateFound = true
                skipWhitespace()
                expect(':')
                parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index += 1
                    '}' -> {
                        index += 1
                        return
                    }

                    else -> throw IllegalStateException("malformed object")
                }
            }
        }

        private fun parseArray(depth: Int) {
            expect('[')
            skipWhitespace()
            if (peek() == ']') {
                index += 1
                return
            }
            while (true) {
                parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index += 1
                    ']' -> {
                        index += 1
                        return
                    }

                    else -> throw IllegalStateException("malformed array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                val character = next()
                when (character) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        val escaped = next()
                        when (escaped) {
                            '"', '\\', '/' -> builder.append(escaped)
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                val hex = text.substring(index, index + 4)
                                index += 4
                                builder.append(hex.toInt(16).toChar())
                            }

                            else -> throw IllegalStateException("bad escape")
                        }
                    }

                    else -> builder.append(character)
                }
            }
        }

        private fun parseLiteral() {
            while (peek().isLetter()) index += 1
        }

        private fun parseNumber() {
            while (true) {
                val character = peek()
                if (character == '-' || character == '+' || character == '.' ||
                    character == 'e' || character == 'E' || character.isDigit()
                ) {
                    index += 1
                } else {
                    return
                }
            }
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index += 1
        }

        private fun peek(): Char =
            if (index < text.length) text[index] else throw IllegalStateException("unexpected end")

        private fun next(): Char =
            if (index < text.length) text[index++] else throw IllegalStateException("unexpected end")

        private fun expect(expected: Char) {
            skipWhitespace()
            if (next() != expected) throw IllegalStateException("expected '$expected'")
        }

        private companion object {
            const val MAX_DEPTH = 128
        }
    }
}
