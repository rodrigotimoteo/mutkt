package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestClassShapesTest {
    private val shapes =
        testClassShapes(compileKotlin(SOURCE, inheritClassPath = true).map { (name, bytes) -> ClassFile(name, bytes) })

    @Test
    fun `names each test class's direct superclass and interfaces`() {
        assertEquals(setOf("demo.BaseTest", "demo.Tagged"), shapes.supertypes["demo.CalcTest"])
        assertEquals(setOf("java.lang.Object"), shapes.supertypes["demo.BaseTest"])
    }

    @Test
    fun `classes that neither declare nor inherit a test method are helpers`() =
        assertEquals(
            setOf("demo.Fixtures", "demo.Tagged", "demo.Setup", "demo.CalcTest\$Companion"),
            shapes.helpers,
        )

    private companion object {
        val SOURCE =
            """
            package demo

            import org.junit.jupiter.api.BeforeEach
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.params.ParameterizedTest
            import org.junit.jupiter.params.provider.ValueSource

            object Fixtures { val two = 2 }

            interface Tagged

            abstract class BaseTest {
                @Test
                fun base() = Unit
            }

            class CalcTest : BaseTest(), Tagged {
                companion object { val one = 1 }
            }

            class OtherTest {
                @ParameterizedTest
                @ValueSource(ints = [1])
                fun each(n: Int) = Unit
            }

            open class Setup {
                @BeforeEach
                fun reset() = Unit
            }

            class InheritsTest : CalcTest2()

            open class CalcTest2 {
                @org.junit.Test
                fun legacy() = Unit
            }
            """.trimIndent()
    }
}
