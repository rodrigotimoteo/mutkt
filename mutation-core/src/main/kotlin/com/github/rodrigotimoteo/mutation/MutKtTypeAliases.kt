package com.github.rodrigotimoteo.mutation

/**
 * Type aliases for MutKt public API.
 *
 * Centralises the magic strings used across the engine, runner, and
 * reports so call sites can use a named type instead of repeating
 * `String` and hoping a `ClassName` and a `MutationId` never get
 * swapped at a constructor boundary. The aliases are erased at
 * runtime — they exist purely for documentation and IDE support.
 *
 * - [ClassName]: dotted form (e.g. `com.example.Foo`) is the
 *   canonical representation used by
 *   [com.github.rodrigotimoteo.mutation.model.Mutation], the engine,
 *   and the reports. Slashed form (`com/example/Foo`) is the
 *   JVM-internal form used by ASM and the classloader.
 * - [ClassNamePattern]: regex pattern matched against a class name
 *   to decide whether the class should be included in or excluded
 *   from mutation. See
 *   [com.github.rodrigotimoteo.mutation.engine.MutationEngineConfig.includePatterns]
 *   for the rules on syntax and matching.
 * - [MutationId]: unique identifier for a single mutation point.
 *   Format is
 *   `${operator}::${className}::${methodName}::${lineNumber}::${occurrenceIndex}`
 *   (the engine builds this string in
 *   [com.github.rodrigotimoteo.mutation.engine.MutationEngine]). The
 *   format is also embedded in cache keys, baseline entries, and the
 *   [com.github.rodrigotimoteo.mutation.model.Mutation.id] field.
 */
typealias ClassName = String
typealias ClassNamePattern = String
typealias MutationId = String
