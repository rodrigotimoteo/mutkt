package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

// kotlinc marks the reference return of every non-private member with @NotNull or @Nullable (class retention, so
// an invisible annotation). Private members, generic T, platform-typed and Java methods carry neither: unknown.
// Only a Kotlin class's annotation is trusted: in Java code it is a convention, not a type the compiler enforces.
private const val NOT_NULL = "Lorg/jetbrains/annotations/NotNull;"

/** True when the method's return is declared non-null with `@org.jetbrains.annotations.NotNull`. */
internal fun MethodNode.returnsNonNull(): Boolean = invisibleAnnotations.orEmpty().any { it.desc == NOT_NULL }

/** True when the class carries `kotlin.Metadata` (runtime retention; javac output never has it). */
internal fun ClassNode.isKotlin(): Boolean = visibleAnnotations.orEmpty().any { it.desc == "Lkotlin/Metadata;" }

/** The method [name] [desc] and the class declaring it: [owner] or the nearest supertype [header] knows; else null. */
internal fun declarationOf(
    header: (String) -> ClassNode?,
    owner: String,
    name: String,
    desc: String,
): Pair<ClassNode, MethodNode>? {
    val pending = ArrayDeque(listOf(owner))
    val seen = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst().takeIf(seen::add)?.let(header) ?: continue
        node.methods.firstOrNull { it.name == name && it.desc == desc }?.let { return node to it }
        pending += listOfNotNull(node.superName) + node.interfaces
    }
    return null
}

/**
 * The methods a static `foo$default(receiver?, args..., int..., Object)` bridge may stand for, as (name, descriptor):
 * kotlinc's unannotated bridge for a call with default arguments, which returns what `foo` returns.
 */
internal fun defaultBridgeTargets(
    owner: String,
    name: String,
    desc: String,
): List<Pair<String, String>> {
    val args = Type.getArgumentTypes(desc).toList()
    if (!name.endsWith("\$default") || args.lastOrNull() != Type.getType(Any::class.java)) return emptyList()
    val returned = Type.getReturnType(desc)
    val masks =
        args
            .dropLast(1)
            .takeLastWhile { it == Type.INT_TYPE }
            .indices
            .map { it + 1 }
    return masks
        .flatMap { count ->
            val own = args.dropLast(1 + count)
            listOfNotNull(own, own.drop(1).takeIf { own.firstOrNull() == Type.getObjectType(owner) })
        }.map { params ->
            name.removeSuffix("\$default") to params.joinToString("", "(", ")${returned.descriptor}") { it.descriptor }
        }
}

/** The class without code: enough for its supertypes, methods and annotations. */
internal fun header(bytes: ByteArray) =
    ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG) }
