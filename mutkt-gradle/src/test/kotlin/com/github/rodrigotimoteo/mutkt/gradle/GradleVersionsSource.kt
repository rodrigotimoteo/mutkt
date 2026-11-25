package com.github.rodrigotimoteo.mutkt.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream

/** Runs the test once per Gradle version in `-Dmutkt.gradleVersions` (comma list); unset means the wrapper's Gradle. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest(name = "{displayName} on gradle {0}")
@ArgumentsSource(GradleVersions::class)
internal annotation class OnGradleVersions

internal class GradleVersions : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
        System
            .getProperty("mutkt.gradleVersions")
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf(TestKitProject.CURRENT_GRADLE) }
            .map { Arguments.of(it) }
            .stream()
}
