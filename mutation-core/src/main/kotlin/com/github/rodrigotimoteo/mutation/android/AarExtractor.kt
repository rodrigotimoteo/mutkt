package com.github.rodrigotimoteo.mutation.android

import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extracts classes.jar from Android Archive (.aar) files.
 *
 * AAR files are zip archives containing:
 * - classes.jar: the actual Java/Kotlin bytecode
 * - AndroidManifest.xml: metadata
 * - res/, libs/: resources and native libs
 *
 * For mutation testing, only classes.jar is relevant.
 */
class AarExtractor(private val tempDir: File) {
    fun extractClassesJar(aar: File): File {
        require(aar.extension == "aar") { "Expected .aar file, got: ${aar.extension}" }
        val outDir =
            File(tempDir, "aar-${aar.nameWithoutExtension}-${aar.lastModified()}").apply {
                mkdirs()
            }
        ZipInputStream(aar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "classes.jar") {
                    val outFile = File(outDir, "classes.jar")
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                    return outFile
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalStateException("No classes.jar found in AAR: ${aar.absolutePath}")
    }
}
