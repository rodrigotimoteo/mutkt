package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.Mutant

/** Finds mutation sites and produces the mutated bytecode for one of them. */
public interface MutantGenerator {
    public fun discover(classes: List<ClassFile>): List<Mutant>

    /**
     * Bytes of the mutant's class with the mutation applied.
     * Valid only for mutants this instance returned from [discover] (one generator per run).
     */
    public fun materialize(mutant: Mutant): ByteArray
}
