package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestExecutor
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request
import org.junit.platform.launcher.core.LauncherFactory
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.time.Duration

/** Runs the project's Jupiter tests in isolated class loaders, one fresh set per mutant. */
public class JUnitTestExecutor(
    projectClasses: List<ClassFile>,
    testClasses: List<ClassFile>,
    classpath: List<Path>,
) : TestExecutor,
    AutoCloseable {
    private val classes = (projectClasses + testClasses).associate { it.name to it.bytes }
    private val testNames = testClasses.map { it.name }.sorted()

    // Dependency jars are shared by concurrent mutants through this loader (library statics included);
    // mutated and test classes are defined per run.
    private val base = projectLoader(classpath, JUnitTestExecutor::class.java.classLoader)

    @Volatile private var closed = false

    // The deadline is enforced by the timeout wrapper, not here.
    override fun run(
        mutant: Mutant,
        bytes: ByteArray,
        deadline: Duration,
    ): Outcome {
        check(!closed) { "JUnitTestExecutor is closed, cannot run ${mutant.id}" }
        return MutantClassLoader(base, classes + (mutant.className to bytes)).use { loader ->
            // Launcher and engines discover and load through the context loader.
            val previous = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = loader
            try {
                execute(mutant, loader)
            } finally {
                Thread.currentThread().contextClassLoader = previous
            }
        }
    }

    override fun close() {
        closed = true
        base.close()
    }

    private fun execute(
        mutant: Mutant,
        loader: ClassLoader,
    ): Outcome {
        // A mutant or test class that cannot link is a broken input, not a kill.
        // declaredMethods links the mutant, which is what surfaces VerifyError before any test touches it.
        val types =
            try {
                loader.loadClass(mutant.className).declaredMethods
                testNames.map(loader::loadClass).filter(::isRunnableRoot)
            } catch (e: LinkageError) {
                return Outcome.LoadError(e.toString())
            }
        return runTests(types)
    }

    // @Nested classes run through their enclosing class; selecting them too would run them twice.
    private fun isRunnableRoot(type: Class<*>) =
        !type.isSynthetic &&
            !type.isAnonymousClass &&
            (type.enclosingClass == null || Modifier.isStatic(type.modifiers))

    private fun runTests(types: List<Class<*>>): Outcome {
        // A new launcher per run: nothing is cached between runs, and engines must be found via this loader.
        val launcher = LauncherFactory.create(LauncherConfig.builder().enableTestEngineAutoRegistration(true).build())
        var started = 0
        for (type in types) {
            val listener = OutcomeListener()
            launcher.execute(request(type), listener)
            started += listener.started
            listener.killedBy?.let { return Outcome.Killed(it) }
        }
        return if (started == 0) Outcome.NoTests else Outcome.Survived
    }

    private fun request(type: Class<*>) =
        request()
            .selectors(selectClass(type))
            .filters(includeEngines("junit-jupiter"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .build()
}
