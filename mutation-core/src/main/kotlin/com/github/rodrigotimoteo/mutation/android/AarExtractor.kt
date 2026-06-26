package com.github.rodrigotimoteo.mutation.android

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Extracts classes.jar (and any bundled jar entries under `libs/`)
 * from Android Archive (.aar) files.
 *
 * AAR files are zip archives that can contain:
 * - classes.jar: the actual Java/Kotlin bytecode for the library
 * - AndroidManifest.xml: metadata
 * - res/, jni/, assets/: resources, native libs, raw assets
 * - libs/<name>.jar: bundled Java/Kotlin dependencies (not all AARs)
 *
 * For mutation testing every jar entry matters: classes.jar holds
 * the library's own bytecode and any jar entries under `libs/` hold
 * transitively bundled dependencies that the URL classloader must
 * see for correct scanning and runtime.
 */
class AarExtractor(private val tempDir: File) {
    /**
     * Wipe the entire AAR extraction directory. Called at the start of
     * a mutation run so stale extractions from AARs that are no longer
     * on the classpath (renamed, removed, version-downgraded) do not
     * accumulate forever. The directory is recreated by [mkdirs] on
     * the next [extractAll] / [extractClassesJar] call.
     *
     * The directory is declared `@LocalState` on the Gradle task and
     * survives up-to-date checks by design — this method is the
     * explicit cleanup hook for in-run freshness, complementing
     * Gradle's `clean` task.
     */
    fun clearAars() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
    }

    fun extractClassesJar(aar: File): File {
        require(aar.extension.equals("aar", ignoreCase = true)) {
            "Expected .aar file, got: ${aar.extension}"
        }
        val outDir = stableAarDir(aar)
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

    /**
     * Compute a stable extraction directory for [aar] based on a SHA-256
     * of the AAR bytes. Using the content hash (instead of `name +
     * lastModified`) ensures:
     *  - same AAR content always reuses the same dir → no accumulation
     *    of stale extracted directories in `build/mutkt-aars/`
     *  - AARs that change (rebuild) get a new dir; the old one can be
     *    cleaned by Gradle's `clean` task like any other build output
     *  - the dir name is independent of filesystem metadata, so it
     *    survives checkouts across machines
     */
    private fun stableAarDir(aar: File): File {
        val digest =
            MessageDigest.getInstance("SHA-256").digest(aar.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
        return File(tempDir, "aar-$hex").apply {
            // Overwrite any prior extraction for the same content. Stale
            // dirs from previous AAR versions are wiped before re-extract
            // to avoid mixing old/new jar entries if the AAR shape changes.
            if (exists()) deleteRecursively()
            mkdirs()
        }
    }

    /**
     * Extract every jar entry from the AAR: `classes.jar` plus any
     * jar bundles under `libs/`. Returns the list of extracted files
     * in deterministic order (classes.jar first, then libs entries in
     * zip order). Throws when no jar entry is present at all — a bare
     * AAR with only resources has no bytecode to mutate.
     */
    fun extractAll(aar: File): List<File> {
        require(aar.extension.equals("aar", ignoreCase = true)) {
            "Expected .aar file, got: ${aar.extension}"
        }
        val outDir = stableAarDir(aar)
        val results = mutableListOf<File>()
        ZipInputStream(aar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "classes.jar" -> {
                        val outFile = File(outDir, "classes.jar")
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                        results.add(outFile)
                    }
                    name.startsWith("libs/") && name.endsWith(".jar") -> {
                        val outFile = File(outDir, name.substringAfterLast('/'))
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                        results.add(outFile)
                    }
                }
                entry = zip.nextEntry
            }
        }
        if (results.isEmpty()) {
            throw IllegalStateException(
                "No classes.jar or libs jars found in AAR: ${aar.absolutePath}",
            )
        }
        return results
    }
}
