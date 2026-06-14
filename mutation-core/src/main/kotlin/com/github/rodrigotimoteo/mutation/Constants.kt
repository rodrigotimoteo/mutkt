package com.github.rodrigotimoteo.mutation

/**
 * Shared constants used across MutKt modules.
 *
 * Centralised here so the values stay in sync between the engine,
 * annotations, Gradle plugin, and reporter without re-declaring them
 * in each module:
 * - [DEFAULT_TIMEOUT_MS] default timeout (ms) applied to a single mutant test execution
 * - [MUTKT_DIR] root directory (relative to the project) that holds MutKt-generated files
 * - [LOG_PREFIX] common log prefix printed on stderr and in console reports
 * - [REPORT_WIDTH] width (in characters) of the separator line used in console reports
 */

const val DEFAULT_TIMEOUT_MS: Long = 30_000L

const val MUTKT_DIR: String = ".mutkt"

const val LOG_PREFIX: String = "[MutKt]"

const val REPORT_WIDTH: Int = 60
