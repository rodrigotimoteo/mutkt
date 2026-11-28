package com.github.rodrigotimoteo.mutkt.application

import kotlin.time.Duration

/**
 * Everything outside the main and test classes that can change a result, so any change drops the whole history.
 * [classpath] (the test runtime entries that are not class directories) and [helperTestClasses] (test classes that
 * are not run as tests) map names to content digests; [classpath] stays in classpath order, which can pick a class.
 * [threads] is the resolved number of parallel runs: their load decides whether a slow mutant ends in TIMEOUT.
 * [java] names the runtime's vendor and full version (java.vendor + java.runtime.version), not just the major.
 */
public data class RunFingerprint(
    val pluginVersion: String,
    val operators: Set<String>,
    val engines: Set<String>,
    val includeTags: Set<String>,
    val excludeTags: Set<String>,
    val timeout: Duration,
    val threads: Int,
    val jvmArgs: List<String>,
    val java: String,
    val workingDir: String,
    val classpath: Map<String, String>,
    val helperTestClasses: Map<String, String>,
) {
    /** The digest stored as the history's fingerprint. */
    public fun digest(): String =
        sha256(
            listOf(pluginVersion) + operators.sorted().counted() + engines.sorted().counted() +
                includeTags.sorted().counted() + excludeTags.sorted().counted() +
                timeout.inWholeMilliseconds.toString() + threads.toString() + jvmArgs.counted() + java + workingDir +
                classpath.parts() + helperTestClasses.toSortedMap().parts(),
        )
}
