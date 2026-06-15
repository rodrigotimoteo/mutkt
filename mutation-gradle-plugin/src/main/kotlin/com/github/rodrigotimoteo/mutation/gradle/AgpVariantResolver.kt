package com.github.rodrigotimoteo.mutation.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import java.io.File

/**
 * Resolves an [AndroidMutationContext] for a given AGP variant.
 *
 * The resolver inspects the active [AndroidComponentsExtension] to find the
 * variant matching [MutationPluginExtension.androidVariant], then captures
 * its runtime classpath, classes dirs, test classes dirs, and the matching
 * `android.jar` for the variant's compileSdk.
 *
 * Pure-JVM projects (no AGP on the classpath) are supported: the resolver
 * returns `null` and the plugin falls back to standard JVM detection.
 */
class AgpVariantResolver(private val objectFactory: ObjectFactory) {
    /**
     * Resolve the Android variant context. Returns `null` when AGP is not
     * applied or the requested variant does not exist.
     */
    fun resolve(
        project: Project,
        extension: MutationPluginExtension,
    ): AndroidMutationContext? {
        if (!extension.isAndroid.getOrElse(false)) {
            return null
        }
        return try {
            val requestedVariant = extension.androidVariant.getOrElse("debug")
            val androidComponents =
                project.extensions.findByType(AndroidComponentsExtension::class.java)
                    ?: return null

            val contextHolder = arrayOfNulls<AndroidMutationContext>(1)
            androidComponents.onVariants { variant ->
                if (variant.name.equals(requestedVariant, ignoreCase = true)) {
                    val cap = variant.name.replaceFirstChar { it.uppercaseChar() }
                    val compileTask = "compile${cap}Kotlin"
                    val testCompileTask = "compile${cap}UnitTestKotlin"

                    val runtimeClasspath =
                        objectFactory.fileCollection().from(variant.runtimeConfiguration)

                    val classesDirs = objectFactory.fileCollection()
                    val mainCompile = project.tasks.findByName(compileTask)
                    if (mainCompile != null) {
                        classesDirs.from(mainCompile.outputs.files)
                    }

                    val testClassesDirs = objectFactory.fileCollection()
                    val testCompile = project.tasks.findByName(testCompileTask)
                    if (testCompile != null) {
                        testClassesDirs.from(testCompile.outputs.files)
                    }

                    val androidJar = project.findAndroidJar()

                    contextHolder[0] =
                        AndroidMutationContext(
                            variantName = variant.name,
                            runtimeClasspath = runtimeClasspath,
                            classesDirs = classesDirs,
                            testClassesDirs = testClassesDirs,
                            androidJar = androidJar,
                            compileTask = compileTask,
                            testCompileTask = testCompileTask,
                        )
                }
            }
            contextHolder[0]
        } catch (_: NoClassDefFoundError) {
            null
        } catch (e: Exception) {
            project.logger.warn("Could not resolve Android variant: ${e.message}")
            null
        }
    }

    private fun Project.findAndroidJar(): File {
        val android = extensions.findByType(BaseExtension::class.java)
        if (android == null) {
            val sdkPath =
                System.getenv("ANDROID_HOME")
                    ?: System.getenv("ANDROID_SDK_ROOT")
            val sdkDir =
                if (sdkPath != null) {
                    File(sdkPath)
                } else {
                    File(System.getProperty("user.home"), "Library/Android/sdk")
                }
            return File(sdkDir, "platforms/android-34/android.jar")
        }
        val sdkDir: File = android.sdkDirectory
        val compileSdk: String? = android.compileSdkVersion
        return if (compileSdk != null) {
            File(sdkDir, "platforms/android-$compileSdk/android.jar")
        } else {
            File(sdkDir, "platforms/android-34/android.jar")
        }
    }
}
