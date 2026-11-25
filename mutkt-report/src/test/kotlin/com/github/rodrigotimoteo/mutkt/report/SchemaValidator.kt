package com.github.rodrigotimoteo.mutkt.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Checks a JSON document against the subset of draft-07 the Stryker report schema uses; returns the violations. */
class SchemaValidator(
    schemaJson: String,
) {
    private val root = mapper.readTree(schemaJson)

    fun validate(json: String): List<String> =
        mutableListOf<String>().also { check(mapper.readTree(json), root, "$", it) }

    private fun check(
        value: JsonNode,
        schema: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        schema.get("\$ref")?.let { ref -> return check(value, root.at(ref.asText().removePrefix("#")), path, errors) }
        schema.get("type")?.let {
            if (!matches(value, it.asText())) {
                return run {
                    errors +=
                        path + " is not " + it.asText()
                }
            }
        }
        checkValue(value, schema, path, errors)
        if (value.isObject) checkObject(value, schema, path, errors)
        if (value.isArray) checkArray(value, schema, path, errors)
    }

    private fun checkValue(
        value: JsonNode,
        schema: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        schema.get("enum")?.let { allowed ->
            if (allowed.none { it == value }) {
                errors +=
                    path + " is not one of " + allowed
            }
        }
        schema.get("pattern")?.let {
            if (!Regex(it.asText()).containsMatchIn(value.asText())) errors += path + " does not match " + it.asText()
        }
        schema.get("minimum")?.let { if (value.asDouble() < it.asDouble()) errors += path + " is below " + it }
        schema.get("maximum")?.let { if (value.asDouble() > it.asDouble()) errors += path + " is above " + it }
    }

    private fun checkObject(
        value: JsonNode,
        schema: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        schema.get("required")?.forEach { if (!value.has(it.asText())) errors += path + " lacks " + it.asText() }
        val additional = schema.get("additionalProperties")?.takeIf(JsonNode::isObject)
        for ((name, child) in value.properties()) {
            val childSchema = schema.get("properties")?.get(name) ?: additional
            if (childSchema != null) check(child, childSchema, "$path.$name", errors)
        }
    }

    private fun checkArray(
        value: JsonNode,
        schema: JsonNode,
        path: String,
        errors: MutableList<String>,
    ) {
        schema.get("items")?.let { items ->
            value.forEachIndexed { i, item -> check(item, items, "$path[$i]", errors) }
        }
        if (schema.get("uniqueItems")?.asBoolean() == true &&
            value.toSet().size != value.size()
        ) {
            errors += path + " has duplicates"
        }
    }

    private fun matches(
        value: JsonNode,
        type: String,
    ): Boolean =
        when (type) {
            "object" -> value.isObject
            "array" -> value.isArray
            "string" -> value.isTextual
            "boolean" -> value.isBoolean
            "number" -> value.isNumber
            "integer" -> value.isIntegralNumber
            else -> error("Unsupported type " + type)
        }

    companion object {
        private val mapper = ObjectMapper()
    }
}
