package top.chiloven.lukosbot2.commands.definition.parser

import top.chiloven.lukosbot2.commands.definition.CommandParseException

object ShellWords {

    fun split(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0

        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' || c == '\'' -> {
                    if (current.isEmpty()) {
                        i = readQuoted(input, i, c, current)
                    } else {
                        current.append(c)
                        i++
                    }
                }

                c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    i++
                }

                else -> {
                    current.append(c)
                    i++
                }
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    private fun readQuoted(input: String, start: Int, quote: Char, current: StringBuilder): Int {
        var i = start + 1
        while (i < input.length) {
            if (input[i] == quote) {
                return i + 1
            }
            current.append(input[i])
            i++
        }
        throw CommandParseException("引号未闭合", input = input, cursor = start)
    }

}
