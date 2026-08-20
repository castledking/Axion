package axion.client.render

import kotlin.test.Test
import kotlin.test.assertContains

class RuntimeMappingCandidatesTest {
    @Test
    fun `intermediary field remains resolvable when production runtime has no named namespace`() {
        val named = RuntimeFieldMapping(
            namespace = "named",
            owner = "net.minecraft.client.render.RenderPhase",
            name = "COLOR_PROGRAM",
            descriptor = "Lnet/minecraft/client/render/RenderPhase\$ShaderProgram;",
        )
        val intermediary = RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_29442",
            descriptor = "Lnet/minecraft/class_4668\$class_5942;",
        )

        val candidates = runtimeFieldNameCandidates(
            rawNames = listOf(named.name),
            mappings = listOf(named, intermediary),
        ) { mapping ->
            if (mapping.namespace == "named") {
                throw IllegalArgumentException("Unknown namespace: named")
            }
            mapping.name
        }

        assertContains(candidates, "field_29442")
    }

    @Test
    fun `intermediary class maps into the active development namespace`() {
        val candidates = runtimeClassNameCandidates(
            rawNames = listOf(
                "net.minecraft.client.render.RenderSetup",
                "net.minecraft.class_12247",
            ),
        ) { namespace, className ->
            when {
                namespace == "named" ->
                    throw IllegalArgumentException("Unknown namespace: named")
                namespace == "intermediary" && className == "net.minecraft.class_12247" ->
                    "net.minecraft.client.render.RenderSetup"
                else -> className
            }
        }

        assertContains(candidates, "net.minecraft.class_12247")
        assertContains(candidates, "net.minecraft.client.render.RenderSetup")
    }
}
