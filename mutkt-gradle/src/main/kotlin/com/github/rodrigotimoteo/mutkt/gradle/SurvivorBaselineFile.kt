package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.Survivor
import com.github.rodrigotimoteo.mutkt.application.SurvivorBaseline
import com.github.rodrigotimoteo.mutkt.application.survivorBaselineFromJson
import com.github.rodrigotimoteo.mutkt.application.survivors
import com.github.rodrigotimoteo.mutkt.application.toJson
import com.github.rodrigotimoteo.mutkt.asm.methodDigests
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File

/**
 * The survivors of [report], each keyed by its method's code in the original class [bytes] returns by binary name
 * (null when there is none); a survivor whose method is not found fails the run.
 */
internal fun survivorsOf(
    report: MutationReport,
    bytes: (String) -> ByteArray?,
): List<Survivor> {
    val byClass = mutableMapOf<String, Map<String, String>>()
    return report.survivors { mutant ->
        val digests = byClass.getOrPut(mutant.className) { bytes(mutant.className)?.let(::methodDigests).orEmpty() }
        val method = "${mutant.methodName}${mutant.methodDescriptor}"
        digests[method] ?: throw GradleException(
            "MutKt cannot key survivor ${mutant.id} for the survivor baseline: ${mutant.className}#$method " +
                "is not in the compiled classes",
        )
    }
}

/** The baseline in [file]; a missing file accepts nothing, one that cannot be read fails the run. */
internal fun readSurvivorBaseline(file: File): SurvivorBaseline {
    if (!file.exists()) return SurvivorBaseline(emptySet())
    return try {
        val tree = JsonSlurper().parseText(file.readText())
        require(tree is Map<*, *>) { "not a JSON object" }
        survivorBaselineFromJson(tree)
    } catch (e: JsonException) {
        throw corrupt(file, e)
    } catch (e: IllegalArgumentException) {
        // Also JsonSlurper's answer to an empty file.
        throw corrupt(file, e)
    }
}

/** Replaces [file] with a baseline that accepts [survivors]. */
internal fun writeSurvivorBaseline(
    file: File,
    survivors: List<Survivor>,
) {
    file.absoluteFile.parentFile.mkdirs()
    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(SurvivorBaseline(survivors.toSet()).toJson())) + "\n")
}

/** Why the run fails: the [found] survivors [baseline] does not accept, null when it accepts them all. */
internal fun newSurvivorsFailure(
    baseline: SurvivorBaseline,
    found: List<Survivor>,
    path: String,
): String? {
    val new = baseline.unaccepted(found).ifEmpty { return null }
    val lines =
        new.take(MAX_LISTED).map { survivor ->
            val changed = if (baseline.accepted(survivor.id)) " (accepted before its method changed: review it)" else ""
            "  ${survivor.id}$changed"
        }
    val more = listOfNotNull("  ... and ${new.size - MAX_LISTED} more".takeIf { new.size > MAX_LISTED })
    val count = if (new.size == 1) "1 survivor is" else "${new.size} survivors are"
    return (listOf("$count not in the MutKt survivor baseline $path:") + lines + more + RECOVERY).joinToString("\n")
}

private fun corrupt(
    file: File,
    cause: Exception,
) = GradleException(
    "The MutKt survivor baseline ${file.path} is corrupt (${cause.message?.lines()?.first()}): " +
        "fix it or run ./gradlew mutktUpdateBaseline",
    cause,
)

private const val MAX_LISTED = 10
private const val RECOVERY = "Kill them, or accept them with ./gradlew mutktUpdateBaseline"
