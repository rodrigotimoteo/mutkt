package com.github.rodrigotimoteo.mutkt.report

private const val BUNDLE = "mutation-test-elements.js"

private const val HEAD =
    "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
        "<title>MutKt mutation report</title>\n</head>\n<body>\n" +
        "<!-- Viewer: mutation-testing-elements 3.9.0 (Apache-2.0) and the code it bundles " +
        "(Lit, Prism, Tailwind CSS, RxJS and others); licenses and notices: " +
        "THIRD-PARTY-NOTICES.txt and mutation-testing-elements.LICENSE in " +
        "com/github/rodrigotimoteo/mutkt/report/ of the mutkt-report jar. -->\n" +
        "<mutation-test-report-app></mutation-test-report-app>\n<script>\n"

private const val DATA = "\n</script>\n<script>\ndocument.querySelector('mutation-test-report-app').report = "

private const val TAIL = ";\n</script>\n</body>\n</html>\n"

/** One offline page: the vendored viewer bundle followed by [json], the report document. */
internal fun htmlPage(json: String): String {
    // JSON has '<' only inside strings; escaping it stops "</script>" or "<!--" in a source from ending the script.
    val data = json.replace("<", "\\" + "u003c")
    return HEAD + bundle() + DATA + data + TAIL
}

private fun bundle(): String =
    StrykerReportWriter::class.java.getResourceAsStream(BUNDLE)?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("Missing resource $BUNDLE")
