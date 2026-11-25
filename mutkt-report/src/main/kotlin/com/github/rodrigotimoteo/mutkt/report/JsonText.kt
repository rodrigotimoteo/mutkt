package com.github.rodrigotimoteo.mutkt.report

private const val INDENT = "  "
private const val FIRST_PRINTABLE = 0x20
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
private const val FORM_FEED = 0x0C

/** Renders maps, lists, strings, numbers, booleans and null as indented JSON; anything else is a bug. */
internal fun renderJson(value: Any?): String = StringBuilder().also { it.appendJson(value, 0) }.toString()

private fun StringBuilder.appendJson(
    value: Any?,
    depth: Int,
) {
    when (value) {
        null -> append("null")
        is String -> appendString(value)
        is Number, is Boolean -> append(value)
        is Map<*, *> ->
            appendContainer('{', '}', value.entries, depth) { entry ->
                appendString(entry.key as String)
                append(": ")
                appendJson(entry.value, depth + 1)
            }
        is List<*> -> appendContainer('[', ']', value, depth) { appendJson(it, depth + 1) }
        else -> throw IllegalArgumentException("Not a JSON value: " + value::class)
    }
}

private fun <T> StringBuilder.appendContainer(
    open: Char,
    close: Char,
    items: Collection<T>,
    depth: Int,
    appendItem: (T) -> Unit,
) {
    append(open)
    items.forEachIndexed { index, item ->
        append(if (index == 0) "\n" else ",\n")
        append(INDENT.repeat(depth + 1))
        appendItem(item)
    }
    if (items.isNotEmpty()) append('\n').append(INDENT.repeat(depth))
    append(close)
}

// U+2028 and U+2029 are legal JSON but end a line in JavaScript before ES2019, and the HTML report embeds this text.
private fun StringBuilder.appendString(text: String) {
    append('"')
    for (char in text) {
        when (char.code) {
            '"'.code -> append("\\\"")
            '\\'.code -> append("\\\\")
            '\n'.code -> append("\\n")
            '\r'.code -> append("\\r")
            '\t'.code -> append("\\t")
            '\b'.code -> append("\\b")
            FORM_FEED -> append("\\f")
            in 0 until FIRST_PRINTABLE, LINE_SEPARATOR, PARAGRAPH_SEPARATOR -> append("\\" + "u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}
