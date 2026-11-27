package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.domain.Mutant
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * True for a mutant in the body of a `lazy { }` that one of [classes] creates while it initialises (object, companion
 * and top-level properties). That body runs once per JVM, under whichever test class first reads the property, so its
 * hits do not name the tests that depend on it. A lazy an instance creates runs once per instance and stays mapped.
 */
internal fun inLazyInitialiser(classes: Collection<ByteArray>): (Mutant) -> Boolean {
    val bodies = classes.flatMapTo(HashSet(), ::lazyBodies)
    return { Body(it.className, null) in bodies || Body(it.className, it.methodName + it.methodDescriptor) in bodies }
}

/** A lambda body: method [method] (name and descriptor) of [className], or with a null [method] a lambda class. */
private data class Body(
    val className: String,
    val method: String?,
)

private fun lazyBodies(bytes: ByteArray): List<Body> {
    val node = ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES) }
    val init = node.methods.firstOrNull { it.name == "<clinit>" } ?: return emptyList()
    return init.instructions.filter(::isLazyCall).mapNotNull { body(it.previousCode()) }
}

private fun isLazyCall(insn: AbstractInsnNode) =
    insn is MethodInsnNode && insn.opcode == INVOKESTATIC && insn.owner == "kotlin/LazyKt" && insn.name == "lazy"

// The function is lazy's last argument, so the instruction before the call (and its cast) creates it. kotlinc 2
// compiles the lambda to a method of the class, created by invokedynamic; kotlinc 1.x to a class of its own with a
// static INSTANCE.
private fun body(insn: AbstractInsnNode?): Body? =
    when {
        insn is InvokeDynamicInsnNode ->
            (insn.bsmArgs.getOrNull(1) as? Handle)?.let { Body(it.owner.replace('/', '.'), it.name + it.desc) }
        insn is FieldInsnNode && insn.opcode == GETSTATIC && insn.name == "INSTANCE" ->
            Body(insn.owner.replace('/', '.'), null)
        else -> null
    }

private fun AbstractInsnNode.previousCode(): AbstractInsnNode? =
    generateSequence(previous) { it.previous }.firstOrNull { it.opcode >= 0 && it.opcode != CHECKCAST }
