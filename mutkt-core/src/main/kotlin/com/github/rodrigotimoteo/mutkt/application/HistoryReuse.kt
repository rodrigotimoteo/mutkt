package com.github.rodrigotimoteo.mutkt.application

import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import com.github.rodrigotimoteo.mutkt.application.port.ResultHistory

/**
 * Lets a run reuse the results in [history] while [fingerprint] matches; [testClasses] supplies every compiled test
 * class (nested ones included), whose bytes key the mutants they cover, and [testSupertypes] each test class's direct
 * superclass and interfaces, so a base class in the test sources keys the mutants its subclasses cover.
 */
public class HistoryReuse(
    public val history: ResultHistory,
    public val fingerprint: RunFingerprint,
    public val testClasses: ClassSource,
    public val testSupertypes: Map<String, Set<String>>,
)
