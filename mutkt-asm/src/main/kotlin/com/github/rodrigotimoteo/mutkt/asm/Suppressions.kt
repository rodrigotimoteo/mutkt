package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.annotations.SuppressMutations
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

// Matched by descriptor: the analysed classes are only read, never loaded against this annotation class.
private val ANNOTATION = Type.getDescriptor(SuppressMutations::class.java)

// Kotlin names the body of a lambda in function f "f$lambda$N" (nested lambdas append another "$lambda$N").
private val LAMBDA = Regex("""(.+)[$]lambda[$]\d+""")

/** Operators switched off for a piece of code: every operator, or the listed ids. */
private data class Suppression(
    val all: Boolean = false,
    val operators: Set<String> = emptySet(),
) {
    operator fun plus(other: Suppression) = Suppression(all || other.all, operators + other.operators)
}

/**
 * The `@SuppressMutations` rules read from every class in [classes], so a target keeps the rules of enclosing
 * code that is not itself a target. Only the [targets] (internal names) and the code enclosing them are validated
 * against [valid]: an unknown operator name in an excluded class is ignored.
 * A method is covered by its class, its own annotation, the annotation of the function whose lambda body it is,
 * and, for classes nested in or declared inside another class (or one of its methods), by that enclosing code.
 * A method's own annotation matches by name and descriptor; a lambda only knows its owner's name, so it
 * inherits from every overload with that name.
 */
internal class Suppressions(
    classes: List<ByteArray>,
    targets: Set<String>,
    private val valid: List<OperatorId>,
) {
    private val nodes: Map<String, ClassNode> =
        classes
            .map { bytes -> ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE) } }
            .associateBy { it.name }

    init {
        nodes.values.filter { it.name in targets }.forEach { node ->
            forClass(node.name)
            node.methods.forEach { forMethod(node.name, it.name, it.desc) }
        }
    }

    /** True when [operator] must not mutate the method [method][descriptor] of class [className] (internal name). */
    fun covers(
        className: String,
        method: String,
        descriptor: String,
        operator: OperatorId,
    ): Boolean {
        val rule = forClass(className) + forMethod(className, method, descriptor)
        return rule.all || operator.value in rule.operators
    }

    private fun forClass(name: String): Suppression {
        val node = nodes[name] ?: return Suppression()
        return read(node.visibleAnnotations, name) + enclosing(node)
    }

    private fun enclosing(node: ClassNode): Suppression {
        val outer = node.innerClasses.firstOrNull { it.name == node.name }?.outerName ?: node.outerClass
        return if (outer == null) {
            Suppression()
        } else {
            forClass(outer) + (node.outerMethod?.let { forMethod(outer, it, node.outerMethodDesc) } ?: Suppression())
        }
    }

    private fun forMethod(
        className: String,
        name: String,
        descriptor: String?,
    ): Suppression {
        val node = nodes[className] ?: return Suppression()
        val own =
            node.methods
                .filter { it.name == name && (descriptor == null || it.desc == descriptor) }
                .fold(Suppression()) { acc, m -> acc + read(m.visibleAnnotations, "$className.$name") }
        val owner = LAMBDA.matchEntire(name)?.groupValues?.get(1)
        return if (owner == null) own else own + forMethod(className, owner, null)
    }

    private fun read(
        annotations: List<AnnotationNode>?,
        where: String,
    ): Suppression {
        val annotation = annotations?.firstOrNull { it.desc == ANNOTATION } ?: return Suppression()
        val names =
            annotation.values
                .orEmpty()
                .chunked(2)
                .firstOrNull { it[0] == "operators" }
                ?.get(1)
        val ids = (names as? List<*>).orEmpty().map { it as String }
        val unknown = ids.filter { name -> valid.none { it.value == name } }
        require(unknown.isEmpty()) {
            "Unknown operator ${unknown.joinToString { "'$it'" }} in @SuppressMutations on $where; " +
                "valid operators: ${valid.joinToString { it.value }}"
        }
        return Suppression(all = ids.isEmpty(), operators = ids.toSet())
    }
}
