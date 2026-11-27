package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.Mutant

/** Adds hit probes to classes, so running a test shows which mutation sites and classes it reached. */
public interface CoverageInstrumenter {
    /**
     * Probes for the sites of [mutants], which a [MutantGenerator] discovered in [classes], and one on entry to every
     * method of [classes]: the last slot of each class's probe array, which no [Probe] names. A mutant whose site
     * cannot be probed, or whose hits would not name the tests that depend on it, is absent from [Instrumented.probes];
     * a class that cannot be probed is in [Instrumented.unprobed].
     */
    public fun instrument(
        classes: List<ClassFile>,
        mutants: List<Mutant>,
    ): Instrumented
}
