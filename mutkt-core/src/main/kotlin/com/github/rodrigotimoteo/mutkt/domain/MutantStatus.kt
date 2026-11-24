package com.github.rodrigotimoteo.mutkt.domain

/** Outcome of running the tests against one mutant. */
public enum class MutantStatus { KILLED, SURVIVED, TIMEOUT, NO_COVERAGE, ERROR }
