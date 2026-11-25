package com.github.rodrigotimoteo.mutkt.application.port

import com.github.rodrigotimoteo.mutkt.domain.Mutant

/** Finds mutation sites and produces the mutated bytecode for one of them. */
public interface MutantGenerator {
    /**
     * Mutants of [targets]. [classes] is every analysable class: suppressions and type lookups read the whole set,
     * so narrowing the targets never changes what a target may be mutated by.
     */
    public fun discover(
        classes: List<ClassFile>,
        targets: List<ClassFile>,
    ): List<Mutant>

    /**
     * Bytes of the mutant's class with the mutation applied.
     * Valid only for mutants this instance returned from [discover] (one generator per run).
     */
    public fun materialize(mutant: Mutant): ByteArray
}
