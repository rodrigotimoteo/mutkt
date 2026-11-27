package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.Mutant

/** Adds hit probes to classes, so running a test shows which mutation sites it reached. */
public interface CoverageInstrumenter {
    /**
     * Probes for the sites of [mutants], which a [MutantGenerator] discovered in [classes]. A mutant whose site
     * cannot be probed, or whose hits would not name the tests that depend on it, is absent from [Instrumented.probes].
     */
    public fun instrument(
        classes: List<ClassFile>,
        mutants: List<Mutant>,
    ): Instrumented
}
