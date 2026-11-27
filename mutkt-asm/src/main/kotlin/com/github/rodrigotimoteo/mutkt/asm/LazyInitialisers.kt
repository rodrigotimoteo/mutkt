package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

private const val LAZY_OWNER = "kotlin/LazyKt"

/**
 * True for a mutant in the body of a `lazy { }` that one of [classes] (bytes by binary name) creates while it
 * initialises (object, companion and top-level properties). That body runs once per JVM, under whichever test class
 * first reads the property, so its hits do not name the tests that depend on it. A lazy an instance creates runs once
 * per instance, and a function reference or object passed to lazy can run elsewhere, so both stay mapped.
 */
internal fun inLazyInitialiser(classes: Map<String, ByteArray>): (Mutant) -> Boolean {
    val isLambdaClass = { owner: String -> classes[owner.binary()]?.let(::ClassReader)?.superName == LAMBDA_CLASS }
    val bodies = HashSet<Body>()
    // Most classes never call lazy: skip parsing them.
    classes.values.filter { String(it, Charsets.ISO_8859_1).contains(LAZY_OWNER) }.forEach {
        bodies += lazyBodies(it, isLambdaClass)
    }
    return { Body(it.className, null) in bodies || Body(it.className, it.methodName + it.methodDescriptor) in bodies }
}

/** A lambda body: method [method] (name and descriptor) of [className], or with a null [method] a lambda class. */
private data class Body(
    val className: String,
    val method: String?,
)

private const val LAMBDA_CLASS = "kotlin/jvm/internal/Lambda"

private fun lazyBodies(
    bytes: ByteArray,
    isLambdaClass: (String) -> Boolean,
): List<Body> {
    val node = ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES) }
    val init = node.methods.firstOrNull { it.name == "<clinit>" } ?: return emptyList()
    return init.instructions.filter(::isLazyCall).mapNotNull { body(it.previousCode(), isLambdaClass) }
}

private fun isLazyCall(insn: AbstractInsnNode) =
    insn is MethodInsnNode && insn.opcode == INVOKESTATIC && insn.owner == LAZY_OWNER && insn.name == "lazy"

// The function is lazy's last argument, so the instruction before the call (and its cast) creates it. kotlinc 2
// compiles the lambda to a `$lambda` method of the class, created by invokedynamic; kotlinc 1.x to a Lambda subclass,
// read from its static INSTANCE or, when it captures, constructed.
private fun body(
    insn: AbstractInsnNode?,
    isLambdaClass: (String) -> Boolean,
): Body? =
    when {
        insn is InvokeDynamicInsnNode && insn.bsm.owner == "java/lang/invoke/LambdaMetafactory" ->
            (insn.bsmArgs.getOrNull(1) as? Handle)
                ?.takeIf { "\$lambda" in it.name }
                ?.let { Body(it.owner.binary(), it.name + it.desc) }
        insn is FieldInsnNode && insn.opcode == GETSTATIC && insn.name == "INSTANCE" && isLambdaClass(insn.owner) ->
            Body(insn.owner.binary(), null)
        insn is MethodInsnNode && insn.opcode == INVOKESPECIAL && insn.name == "<init>" && isLambdaClass(insn.owner) ->
            Body(insn.owner.binary(), null)
        else -> null
    }

private fun AbstractInsnNode.previousCode(): AbstractInsnNode? =
    generateSequence(previous) { it.previous }.firstOrNull { it.opcode >= 0 && it.opcode != CHECKCAST }

private fun String.binary() = replace('/', '.')
