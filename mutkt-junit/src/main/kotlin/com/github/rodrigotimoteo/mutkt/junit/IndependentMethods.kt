package com.github.rodrigotimoteo.mutkt.junit

import java.util.Properties

internal const val LIFECYCLE_DEFAULT = "junit.jupiter.testinstance.lifecycle.default"
private const val METHOD_ORDER_DEFAULT = "junit.jupiter.testmethod.order.default"
private const val TEST_INSTANCE = "org.junit.jupiter.api.TestInstance"
private const val TEST_METHOD_ORDER = "org.junit.jupiter.api.TestMethodOrder"
private const val PLATFORM_PROPERTIES = "junit-platform.properties"

/**
 * Whether the Jupiter tests of root class [type] may run one method at a time: not when it or a class nested in it
 * shares one instance across its methods or orders them (a method may then need what an earlier one left), nor when
 * [loader]'s configuration (a system property or junit-platform.properties) makes either the default.
 */
internal fun methodsIndependent(
    type: Class<*>,
    loader: ClassLoader,
): Boolean = !sharedOrOrderedByDefault(loader) && type.withNested().none { it.annotations.any(::sharesOrOrders) }

// Annotations are matched by name: the project's Jupiter is loaded apart from this module's.
private fun sharesOrOrders(annotation: Annotation): Boolean =
    when (annotation.annotationClass.java.name) {
        TEST_METHOD_ORDER -> true
        TEST_INSTANCE ->
            annotation.annotationClass.java
                .getMethod("value")
                .invoke(annotation)
                .toString() == "PER_CLASS"
        else -> false
    }

private fun Class<*>.withNested(): Sequence<Class<*>> =
    sequenceOf(this) + declaredClasses.asSequence().flatMap { it.withNested() }

private fun sharedOrOrderedByDefault(loader: ClassLoader): Boolean {
    val file by lazy {
        Properties().apply { loader.getResource(PLATFORM_PROPERTIES)?.openStream()?.use(::load) }
    }
    val setting = { key: String -> System.getProperty(key) ?: file.getProperty(key) }
    return setting(LIFECYCLE_DEFAULT)?.trim().equals("per_class", ignoreCase = true) ||
        !setting(METHOD_ORDER_DEFAULT).isNullOrBlank()
}
