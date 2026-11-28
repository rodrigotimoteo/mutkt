package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

/**
 * What the result history needs from the compiled test classes: each one's direct superclass and interfaces
 * ([supertypes], binary names), and the [helpers], classes that neither declare a JUnit test method nor inherit one
 * from a test-source supertype. No coverage can name a helper, so a change to one must drop the whole history.
 */
internal data class TestClassShapes(
    val supertypes: Map<String, Set<String>>,
    val helpers: Set<String>,
)

internal fun testClassShapes(classes: List<ClassFile>): TestClassShapes {
    val nodes = classes.associate { it.name to ClassNode().also { node -> ClassReader(it.bytes).accept(node, SKIP) } }
    val supertypes =
        nodes.mapValues { (_, node) ->
            (listOfNotNull(node.superName) + node.interfaces.orEmpty()).map { it.replace('/', '.') }.toSet()
        }
    val declaring =
        nodes
            .filterValues { node ->
                node.methods.any { method -> method.visibleAnnotations.orEmpty().any { it.desc in TEST_ANNOTATIONS } }
            }.keys

    // A class hierarchy has no cycles, so the walk ends at classes outside the test sources.
    fun runsTests(name: String): Boolean =
        name in declaring || supertypes[name].orEmpty().any { it in nodes && runsTests(it) }
    return TestClassShapes(supertypes, nodes.keys.filterNot(::runsTests).toSet())
}

private const val SKIP = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

// Jupiter's test method annotations (a custom meta-annotation is missed, which only makes that class a helper) and
// JUnit 4's.
private val TEST_ANNOTATIONS =
    setOf(
        "Lorg/junit/jupiter/api/Test;",
        "Lorg/junit/jupiter/api/RepeatedTest;",
        "Lorg/junit/jupiter/api/TestFactory;",
        "Lorg/junit/jupiter/api/TestTemplate;",
        "Lorg/junit/jupiter/params/ParameterizedTest;",
        "Lorg/junit/Test;",
    )
