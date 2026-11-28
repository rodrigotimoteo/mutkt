package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Koin keeps the started application in a JVM-wide GlobalContext. A test that starts it and never calls stopKoin only
 * passes again in a reused test JVM when Koin itself is loaded per run, so the next run neither throws "already
 * started" nor hands out an instance of an earlier run's class.
 */
@Tag("e2e")
@Tag("matrix")
@Timeout(180)
class MutKtKoinTest {
    @TempDir
    lateinit var dir: File

    @OnGradleVersions
    fun `a global Koin context started by the tests does not outlive a run`(version: String) {
        val project = TestKitProject(dir, version)
        // One thread: a single test JVM runs the baseline and every mutant, one after another.
        project.setUp(
            "dependencies { testImplementation files(${project.jars(KOIN_JARS)}) }\nmutkt { threads = 1 }",
            sources = mapOf("demo/Calc.java" to CALC),
            tests = mapOf("demo/CalcTest.java" to TEST),
        )

        val plain = project.build("test")
        assertEquals(TaskOutcome.SUCCESS, plain.task(":test")?.outcome, plain.output)

        val result = project.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mutkt")?.outcome, result.output)
        // A context left over from an earlier run would fail startKoin and turn the survivor into a kill.
        TestKitProject.assertSummaryLines(project.report.readText(), "Mutants: 4", "KILLED       3", "SURVIVED     1")
    }

    private companion object {
        val KOIN_JARS = Regex("^(koin-core|stately-)")

        val CALC =
            """
            package demo;
            public class Calc {
                public int add(int a, int b) { return a + b; }
                public boolean isPositive(int n) { return n > 0; }
            }
            """.trimIndent()

        // Koin's module DSL is inline and reified, so a Java test declares the single through the types it expands to.
        val TEST =
            """
            package demo;
            import java.util.List;
            import kotlin.Unit;
            import kotlin.jvm.JvmClassMappingKt;
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.Test;
            import org.koin.core.context.DefaultContextExtKt;
            import org.koin.core.definition.BeanDefinition;
            import org.koin.core.definition.Kind;
            import org.koin.core.instance.SingleInstanceFactory;
            import org.koin.core.module.Module;
            import org.koin.core.registry.ScopeRegistry;
            import org.koin.java.KoinJavaComponent;
            public class CalcTest {
                @BeforeAll static void startKoin() {
                    Module module = new Module();
                    module.indexPrimaryType(new SingleInstanceFactory<Calc>(new BeanDefinition<Calc>(
                        ScopeRegistry.Companion.getRootScopeQualifier(), JvmClassMappingKt.getKotlinClass(Calc.class),
                        null, (scope, params) -> new Calc(), Kind.Singleton, List.of())));
                    DefaultContextExtKt.startKoin(app -> { app.modules(module); return Unit.INSTANCE; });
                }
                @Test void adds() { Assertions.assertEquals(5, KoinJavaComponent.<Calc>get(Calc.class).add(2, 3)); }
                @Test void positive() { Assertions.assertTrue(KoinJavaComponent.<Calc>get(Calc.class).isPositive(5)); }
            }
            """.trimIndent()
    }
}
