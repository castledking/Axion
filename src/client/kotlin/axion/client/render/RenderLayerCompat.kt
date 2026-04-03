package axion.client.render

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderLayers
import java.lang.reflect.Modifier

object RenderLayerCompat {
    private const val NO_ARG_RENDER_LAYER_DESC = "()Lnet/minecraft/client/render/RenderLayer;"
    private val legacyIntermediaryNames = mapOf(
        "getLightning" to "method_23593",
        "getLines" to "method_23594",
        "getBlockTranslucentCull" to "method_76545",
        "getTranslucentMovingBlock" to "method_29380",
        "getDebugQuads" to "method_49042",
        "getDebugFilledBox" to "method_49047",
    )
    private val modernIntermediaryNames = mapOf(
        "lightning" to "method_76003",
        "lines" to "method_76015",
        "blockTranslucentCull" to "method_76545",
        "translucentMovingBlock" to "method_75977",
        "debugQuads" to "method_76023",
        "debugFilledBox" to "method_76019",
    )

    fun lines(): RenderLayer = resolve("lines", "getLines")

    fun lightning(): RenderLayer = resolve("lightning", "getLightning")

    fun debugQuads(): RenderLayer = resolve("debugQuads", "getDebugQuads")

    fun debugFilledBox(): RenderLayer = resolve("debugFilledBox", "getDebugFilledBox")

    fun translucentMovingBlock(): RenderLayer = resolve("translucentMovingBlock", "getTranslucentMovingBlock")

    fun blockTranslucentCull(): RenderLayer {
        findMappedMethod(
            RenderLayers::class.java,
            "net.minecraft.client.render.RenderLayers",
            "blockTranslucentCull",
            modernIntermediaryNames["blockTranslucentCull"],
        )?.let { return it.invoke(null) as RenderLayer }

        findMappedMethod(
            RenderLayer::class.java,
            "net.minecraft.client.render.RenderLayer",
            "getBlockTranslucentCull",
            legacyIntermediaryNames["getBlockTranslucentCull"],
        )?.let { return it.invoke(null) as RenderLayer }

        return translucentMovingBlock()
    }

    private fun resolve(renderLayersMethod: String, renderLayerMethod: String): RenderLayer {
        findMappedMethod(
            RenderLayers::class.java,
            "net.minecraft.client.render.RenderLayers",
            renderLayersMethod,
            modernIntermediaryNames[renderLayersMethod],
        )
            ?.let { return it.invoke(null) as RenderLayer }

        val method = findMappedMethod(
            RenderLayer::class.java,
            "net.minecraft.client.render.RenderLayer",
            renderLayerMethod,
            legacyIntermediaryNames[renderLayerMethod],
        ) ?: error("Missing RenderLayer.$renderLayerMethod()")
        return method.invoke(null) as RenderLayer
    }

    private fun findMappedMethod(
        ownerClass: Class<*>,
        namedOwner: String,
        namedMethod: String,
        knownIntermediaryName: String?,
    ) =
        ownerClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType == RenderLayer::class.java &&
                method.name in mappedMethodNames(namedOwner, namedMethod, knownIntermediaryName)
        }

    private fun mappedMethodNames(
        namedOwner: String,
        namedMethod: String,
        knownIntermediaryName: String?,
    ): Set<String> {
        val names = linkedSetOf(namedMethod)
        knownIntermediaryName?.let(names::add)
        runCatching {
            FabricLoader.getInstance().mappingResolver.mapMethodName(
                "named",
                namedOwner,
                namedMethod,
                NO_ARG_RENDER_LAYER_DESC,
            )
        }.getOrNull()?.let(names::add)
        return names
    }
}
