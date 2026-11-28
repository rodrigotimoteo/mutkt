package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested

internal val DEFAULT_ENGINES: Set<String> = setOf("junit-jupiter")

/** The `mutkt { }` DSL. Defaults are documented in docs/dsl.md. */
public abstract class MutKtExtension {
    /** Class globs to mutate; empty means all. */
    public abstract val targetClasses: ListProperty<String>

    /**
     * Class globs never mutated. It starts with `<generated>` (classes compiled from `build/generated` sources) and,
     * on Android, the generated classes' globs (R, Hilt, bindings...).
     */
    public abstract val excludedClasses: ListProperty<String>

    /** Method names or `pkg.Class#method` entries to skip. */
    public abstract val excludedMethods: ListProperty<String>

    /** Operator ids to enable; empty means the default operators (see docs/operators.md). */
    public abstract val operators: SetProperty<String>

    /** Parallel test runs; unset means the available processors, resolved at run time. */
    public abstract val threads: Property<Int>

    /** Per-mutant deadline in milliseconds. */
    public abstract val timeoutMs: Property<Long>

    /** Cap on mutants per class; unset means unlimited. */
    public abstract val maxMutantsPerClass: Property<Int>

    /** Minimum mutation score in percent (0..100); the build fails below it. Unset means no gate. */
    public abstract val failBelowScore: Property<Int>

    /** JUnit engine ids to run (`junit-jupiter`, `junit-vintage`); MutKt supplies the vintage engine when missing. */
    public abstract val engines: SetProperty<String>

    /** Only run tests matching one of these JUnit tag expressions; empty means all. */
    public abstract val includeTags: SetProperty<String>

    /** Skip tests matching one of these JUnit tag expressions. */
    public abstract val excludeTags: SetProperty<String>

    /** Where the results are kept between runs, so mutants whose code and tests did not change are not run again. */
    public abstract val historyFile: RegularFileProperty

    /** Android only: the variant whose unit tests run against the mutants. */
    public abstract val variant: Property<String>

    /** The `reports { }` block. */
    @get:Nested
    public abstract val reports: ReportsSpec

    /** Configures [reports]. */
    public fun reports(action: Action<ReportsSpec>) {
        action.execute(reports)
    }
}
