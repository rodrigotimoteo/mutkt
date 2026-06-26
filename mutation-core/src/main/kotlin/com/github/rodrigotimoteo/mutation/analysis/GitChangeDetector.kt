package com.github.rodrigotimoteo.mutation.analysis

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared git-diff and source-path-to-class-name conversion logic.
 *
 * Both [com.github.rodrigotimoteo.mutation.baseline.BaselineStorage] and
 * [com.github.rodrigotimoteo.mutation.engine.IncrementalAnalyzer] need to
 * (a) run `git diff` against the project directory and (b) map changed
 * file paths like `src/main/kotlin/com/example/Foo.kt` to a dotted class
 * name like `com.example.Foo`. Keeping the implementation in one place
 * avoids drift between the two callers and ensures they share a single
 * definition of the source-directory prefixes.
 *
 * Public so both consumers in the same module can call it without an
 * `internal` qualifier gymnastics.
 */
class GitChangeDetector(
    private val projectDir: File,
) {
    private val logger = LoggerFactory.getLogger(GitChangeDetector::class.java)

    /**
     * Detect changed classes by running `git diff --name-only` and mapping
     * each changed file to its dotted class name.
     *
     * @param sourceDirs Source-directory prefixes to strip when converting
     *                   a path to a class name. Each entry is matched as a
     *                   `startsWith` check. Files outside every prefix are
     *                   dropped (no class name can be derived from them).
     * @param baseBranch Optional base branch to diff against (for PRs).
     *                   When `null`, diffs the working tree against HEAD.
     * @return Set of changed class names (dotted format). Empty if git
     *         is unavailable, the diff times out, or the diff fails.
     */
    fun detectChangedClasses(
        sourceDirs: List<String> = DEFAULT_SOURCE_DIRS,
        baseBranch: String? = null,
    ): Set<String> {
        val changedFiles = getChangedFiles(baseBranch)
        if (changedFiles.isEmpty()) return emptySet()

        return changedFiles
            .mapNotNull { path -> fileToClassName(path, sourceDirs) }
            .toSet()
    }

    /**
     * Run `git diff --name-only` and return the list of changed source
     * files. Returns an empty list if git is unavailable or the diff
     * times out — both indicate "no incremental filter" rather than a
     * hard error.
     */
    private fun getChangedFiles(baseBranch: String?): List<String> {
        return try {
            val command =
                if (baseBranch != null) {
                    // Compare against base branch (for PRs)
                    listOf("git", "diff", "--name-only", "--diff-filter=ACMR", "origin/$baseBranch...HEAD")
                } else {
                    // Compare working tree against HEAD
                    listOf("git", "diff", "--name-only", "--diff-filter=ACMR", "HEAD")
                }

            val process =
                ProcessBuilder(command)
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            // Bound the wait so a hung `git` (credential prompt, paging,
            // stale lock) cannot block the mutation task forever.
            // destroyForcibly releases the underlying OS process; we
            // then bail out with no incremental filter.
            val finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                logger.warn("git diff timed out after ${GIT_TIMEOUT_SECONDS}s, no incremental filtering applied")
                return emptyList()
            }
            val exitCode = process.exitValue()

            if (exitCode != 0) {
                logger.warn("git diff failed (exit code $exitCode), no incremental filtering applied")
                return emptyList()
            }

            output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && (it.endsWith(".kt") || it.endsWith(".java")) }
        } catch (e: Exception) {
            logger.warn("Failed to run git diff: ${e.message}")
            emptyList()
        }
    }

    /**
     * Checks if git is available and the project is a git repository.
     */
    fun isAvailable(): Boolean {
        return try {
            val process =
                ProcessBuilder(listOf("git", "rev-parse", "--git-dir"))
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

            val finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Convert a `src/.../Class.kt`-style path into a dotted class name by
     * stripping the first [sourceDirs] prefix that matches. Returns null
     * for files that do not live under any configured source root, so
     * callers can filter unknown layouts (e.g. generated resources) out
     * of the result.
     *
     * Package-private so tests can verify the mapping directly.
     */
    internal fun fileToClassName(
        path: String,
        sourceDirs: List<String>,
    ): String? {
        for (sourceDir in sourceDirs) {
            if (path.startsWith("$sourceDir/")) {
                val relative = path.removePrefix("$sourceDir/")
                val className =
                    relative
                        .removeSuffix(".kt")
                        .removeSuffix(".java")
                        .replace("/", ".")
                if (className.isNotEmpty()) return className
            }
        }
        return null
    }

    companion object {
        /**
         * Max seconds to wait for a `git` subprocess before bailing out.
         * Keeps hung git invocations (credential prompts, paging, stale
         * locks) from blocking the mutation task indefinitely.
         */
        private const val GIT_TIMEOUT_SECONDS: Long = 30

        /**
         * Default source-directory prefixes used when converting
         * `src/main` → `src/main` style paths to dotted class names. The
         * slashed form (not the dotted form) is required because git
         * reports paths with forward slashes regardless of platform.
         *
         * Includes the standard pure-JVM roots plus all common KMP
         * source set directories (jvmMain, androidMain, iosMain,
         * linuxMain, macosMain, mingwX*, jsMain, wasm*Main) so changed
         * files in KMP projects are still mapped to class names for
         * incremental analysis.
         */
        val DEFAULT_SOURCE_DIRS: List<String> =
            listOf(
                "src/main/kotlin",
                "src/main/java",
                "src/commonMain/kotlin",
                "src/commonMain/java",
                "src/jvmMain/kotlin",
                "src/jvmMain/java",
                "src/androidMain/kotlin",
                "src/androidMain/java",
                "src/iosMain/kotlin",
                "src/iosMain/java",
                "src/iosArm64Main/kotlin",
                "src/iosArm64Main/java",
                "src/iosX64Main/kotlin",
                "src/iosX64Main/java",
                "src/iosSimulatorArm64Main/kotlin",
                "src/iosSimulatorArm64Main/java",
                "src/linuxMain/kotlin",
                "src/linuxMain/java",
                "src/macosMain/kotlin",
                "src/macosMain/java",
                "src/mingwX64Main/kotlin",
                "src/mingwX64Main/java",
                "src/mingwX86Main/kotlin",
                "src/mingwX86Main/java",
                "src/jsMain/kotlin",
                "src/jsMain/java",
                "src/wasmJsMain/kotlin",
                "src/wasmJsMain/java",
                "src/wasmWasiMain/kotlin",
                "src/wasmWasiMain/java",
            )
    }
}
