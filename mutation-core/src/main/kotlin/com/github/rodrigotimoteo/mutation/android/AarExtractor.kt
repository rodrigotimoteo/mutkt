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
 *
 * **Stable extraction directory**: the output directory for each AAR is
 * named `aar-<sha256-prefix>` where the prefix is a 16-char hex of the
 * AAR's content hash. This ensures that:
 *  - the same AAR content always maps to the same extraction dir (no
 *    stale duplicates from re-extracts)
 *  - changed AARs get a fresh dir; the old one is cleaned by Gradle's
 *    `clean` task
 *  - dir names are content-derived and stable across checkouts
 *
 * **clearAars trade-off**: [clearAars] wipes the entire extraction
 * directory at the start of a run to discard stale AARs that are no
 * longer on the classpath. This is the explicit in-run cleanup hook —
 * complementing (not replacing) Gradle's `clean` task. The cost is one
 * re-extract per AAR per run; the benefit is that AARs removed from
 * the classpath (renamed, version-downgraded, deleted) do not leave
 * forever-orphaned extracted jars in `build/`.
 */
class AarExtractor(private val tempDir: File) {
    /**
     * Memoize SHA-256 hex digests keyed by `(path, length, lastModified)`.
     * The three-tuple is a cheap identity check — if the AAR was rebuilt
     * in place (same path) the size or mtime changes and the cache misses
     * → forces a fresh hash. The hex string itself never changes for a
     * given content, so multiple extract calls per AAR pay the digest
     * cost only once.
     */
    private val shaCache: MutableMap<String, String> = HashMap()

    /**
     * Prune stale extraction directories whose AAR is no longer on the
     * classpath. Replaces the old "wipe everything every run" behaviour
     * that forced re-extraction of every AAR even when unchanged.
     *
     * [currentAars] is the list of AARs that will be (or have just been)
     * processed by this run; any `aar-*` subdir of [tempDir] whose
     * content hash does not match a current AAR is deleted. Pass an
     * empty list to skip cleanup entirely.
     */
    fun clearStaleAars(currentAars: List<File>) {
        if (!tempDir.exists()) return
        val keep = currentAars.mapTo(HashSet()) { "aar-${sha256(it)}" }
        tempDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name.startsWith("aar-") && dir.name !in keep) {
                dir.deleteRecursively()
            }
        }
    }

    fun extractClassesJar(aar: File): File {
        require(aar.extension.equals("aar", ignoreCase = true)) {
            "Expected .aar file, got: ${aar.extension}"
        }
        val outDir = stableAarDir(aar)
        val outFile = File(outDir, "classes.jar")
        if (outFile.exists()) return outFile
        ZipInputStream(aar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "classes.jar") {
                    try {
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    } catch (t: Throwable) {
                        // Delete partial jar so the next run doesn't reuse a truncated file.
                        if (outFile.exists()) outFile.delete()
                        throw t
                    }
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
        val hex = sha256(aar)
        return File(tempDir, "aar-$hex").apply {
            // No delete: SHA collision means identical content, so the
            // existing extraction is valid. The caller skips re-write
            // when the expected output file already exists.
            mkdirs()
        }
    }

    private fun sha256(aar: File): String {
        val key = "${aar.absolutePath}:${aar.length()}:${aar.lastModified()}"
        shaCache[key]?.let { return it }
        val digest = MessageDigest.getInstance("SHA-256").digest(aar.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
        shaCache[key] = hex
        return hex
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
        // Fast path: if the expected output files are already present
        // (prior run with same AAR content), reuse them. Avoids re-
        // reading + re-writing potentially-megabyte AAR bytes.
        val existing = mutableListOf<File>()
        val classesJar = File(outDir, "classes.jar")
        if (classesJar.exists()) existing.add(classesJar)
        outDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".jar") && f.name != "classes.jar") {
                existing.add(f)
            }
        }
        if (existing.any { it.name == "classes.jar" }) {
            return existing
        }
        val results = mutableListOf<File>()
        ZipInputStream(aar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "classes.jar" -> {
                        val outFile = File(outDir, "classes.jar")
                        try {
                            outFile.outputStream().use { output -> zip.copyTo(output) }
                        } catch (t: Throwable) {
                            if (outFile.exists()) outFile.delete()
                            throw t
                        }
                        results.add(outFile)
                    }
                    name.startsWith("libs/") && name.endsWith(".jar") -> {
                        val outFile = File(outDir, name.substringAfterLast('/'))
                        try {
                            outFile.outputStream().use { output -> zip.copyTo(output) }
                        } catch (t: Throwable) {
                            if (outFile.exists()) outFile.delete()
                            throw t
                        }
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
