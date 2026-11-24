package com.github.rodrigotimoteo.mutkt.annotations

/** Excludes the annotated class or function from mutation: the listed [operators], or every operator when empty. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class SuppressMutations(
    val operators: Array<String> = [],
    val reason: String = "",
)
