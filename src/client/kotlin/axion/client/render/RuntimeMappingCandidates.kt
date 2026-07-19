package axion.client.render

data class RuntimeFieldMapping(
    val namespace: String,
    val owner: String,
    val name: String,
    val descriptor: String,
)

fun runtimeFieldNameCandidates(
    rawNames: Iterable<String>,
    mappings: Iterable<RuntimeFieldMapping>,
    mapper: (RuntimeFieldMapping) -> String?,
): Set<String> {
    return buildSet {
        addAll(rawNames)
        mappings.forEach { mapping ->
            add(mapping.name)
            runCatching { mapper(mapping) }.getOrNull()?.let(::add)
        }
    }
}

fun runtimeClassNameCandidates(
    rawNames: Iterable<String>,
    mapper: (namespace: String, className: String) -> String?,
): Set<String> {
    return buildSet {
        rawNames.forEach { className ->
            add(className)
            listOf("named", "intermediary").forEach { namespace ->
                runCatching { mapper(namespace, className) }.getOrNull()?.let(::add)
            }
        }
    }
}
