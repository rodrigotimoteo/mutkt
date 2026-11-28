package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException

// MutKt's own classes, which the child JVMs load, are Java 17 bytecode: an older JVM cannot even start them.
internal fun MutKtTask.checkTestJava() {
    val version = testJavaVersion.get()
    if (version < MIN_TEST_JAVA) {
        throw GradleException(
            "MutKt runs the tests on the test task's Java launcher, which is Java $version " +
                "(${testJavaRuntime.get()}), but needs Java $MIN_TEST_JAVA or newer: " +
                "set the test task's javaLauncher to a Java $MIN_TEST_JAVA or newer toolchain",
        )
    }
}

private const val MIN_TEST_JAVA = 17
