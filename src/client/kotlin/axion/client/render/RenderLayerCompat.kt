package axion.client.render

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.render.RenderLayer
import org.slf4j.LoggerFactory
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object RenderLayerCompat {
    private val logger = LoggerFactory.getLogger(RenderLayerCompat::class.java)
    private val loggedWarnings = ConcurrentHashMap.newKeySet<String>()
    private val layerCache = ConcurrentHashMap<String, RenderLayer>()

    private val renderSetupField: java.lang.reflect.Field? by lazy {
        RenderLayer::class.java.declaredFields.firstOrNull {
            it.type.name == "net.minecraft.client.render.RenderSetup"
        }?.also { it.isAccessible = true }
    }

    /**
     * Access the RenderSetup for a RenderLayer via reflection.
     * Needed to resolve texture bindings for custom RenderPass drawing.
     * Returns Any? to remain compatible across MC versions where RenderSetup may not exist.
     */
    fun getRenderSetup(layer: RenderLayer): Any? {
        return renderSetupField?.get(layer)
    }

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
        "cutout" to "method_75995",
    )

    fun cutout(): RenderLayer = resolve(
        key = "cutout",
        namedMethodNames = listOf("cutout"),
        fieldNames = emptyList(),
    )

    fun lines(): RenderLayer = resolve(
        key = "lines",
        namedMethodNames = listOf("lines", "getLines"),
        fieldNames = listOf("LINES"),
    )

    fun lightning(): RenderLayer = resolve(
        key = "lightning",
        namedMethodNames = listOf("lightning", "getLightning"),
        fieldNames = emptyList(),
    )

    fun xrayQuads(): RenderLayer {
        layerCache["xrayQuads"]?.let { return it }
        return runCatching {
            createXrayQuadsLayer()
        }.onFailure { throwable ->
            if (loggedWarnings.add("xrayQuads")) {
                logger.warn("[RenderLayerCompat] Failed to create xrayQuads layer; falling back to lightning", throwable)
            }
        }.getOrElse { lightning() }.also { layerCache["xrayQuads"] = it }
    }

    fun debugQuads(): RenderLayer = resolve(
        key = "debugQuads",
        namedMethodNames = listOf("debugQuads", "getDebugQuads"),
        fieldNames = emptyList(),
    )

    fun debugFilledBox(): RenderLayer = resolve(
        key = "debugFilledBox",
        namedMethodNames = listOf("debugFilledBox", "getDebugFilledBox"),
        fieldNames = emptyList(),
    )

    fun translucentMovingBlock(): RenderLayer = resolve(
        key = "translucentMovingBlock",
        namedMethodNames = listOf("translucentMovingBlock", "getTranslucentMovingBlock"),
        fieldNames = emptyList(),
    )

    fun blockTranslucentCull(): RenderLayer = resolve(
        key = "blockTranslucentCull",
        namedMethodNames = listOf("blockTranslucentCull", "getBlockTranslucentCull", "translucentMovingBlock", "getTranslucentMovingBlock"),
        fieldNames = emptyList(),
    )

    private val renderLayerFactoryClasses: List<Class<*>> by lazy {
        buildList {
            add(RenderLayer::class.java)
            addAll(
                candidateClasses(
                    "net.minecraft.client.render.RenderLayers",
                    "net.minecraft.client.renderer.rendertype.RenderTypes",
                    "net.minecraft.class_12249",
                ),
            )
        }.distinct()
    }

    private fun resolve(
        key: String,
        namedMethodNames: List<String>,
        fieldNames: List<String>,
    ): RenderLayer {
        layerCache[key]?.let { return it }

        val names = candidateMethodNames(namedMethodNames)
        renderLayerFactoryClasses.forEach { owner ->
            findStaticLayerMethod(owner, names)?.let { method ->
                val layer = method.invoke(null) as RenderLayer
                layerCache[key] = layer
                return layer
            }
            findStaticLayerField(owner, fieldNames)?.let { field ->
                val layer = field.get(null) as RenderLayer
                layerCache[key] = layer
                return layer
            }
        }

        if (key == "blockTranslucentCull") {
            return translucentMovingBlock()
        }

        logResolveFailure(key, names, fieldNames)
        error("Missing RenderLayer.$key")
    }

    private fun candidateClasses(vararg namedClassNames: String): List<Class<*>> {
        val resolver = FabricLoader.getInstance().mappingResolver
        val classNames = linkedSetOf<String>()
        namedClassNames.forEach { named ->
            classNames += named
            runCatching { resolver.mapClassName("named", named) }.getOrNull()?.let(classNames::add)
        }
        return classNames.mapNotNull { className ->
            runCatching { Class.forName(className) }.getOrNull()
        }
    }

    private fun candidateMethodNames(namedMethodNames: List<String>): Set<String> {
        val names = linkedSetOf<String>()
        namedMethodNames.forEach { named ->
            names += named
            legacyIntermediaryNames[named]?.let(names::add)
            modernIntermediaryNames[named]?.let(names::add)
        }
        return names
    }

    private fun findStaticLayerMethod(owner: Class<*>, names: Set<String>): java.lang.reflect.Method? {
        return (owner.methods.asSequence() + owner.declaredMethods.asSequence())
            .firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.name in names &&
                    RenderLayer::class.java.isAssignableFrom(method.returnType)
            }
            ?.also { it.isAccessible = true }
    }

    private fun findStaticLayerField(owner: Class<*>, fieldNames: List<String>): java.lang.reflect.Field? {
        if (fieldNames.isEmpty()) return null
        return (owner.fields.asSequence() + owner.declaredFields.asSequence())
            .firstOrNull { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.name in fieldNames &&
                    RenderLayer::class.java.isAssignableFrom(field.type)
            }
            ?.also { it.isAccessible = true }
    }

    private fun createXrayQuadsLayer(): RenderLayer {
        val pipeline = createXrayQuadsPipeline()
        return createModernLayer(pipeline) ?: createLegacyLayer(pipeline)
            ?: error("No compatible RenderLayer factory for xrayQuads")
    }

    private fun createXrayQuadsPipeline(): Any {
        val pipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline")
        val snippetClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline\$Snippet")
        val snippets = Array.newInstance(snippetClass, 0)
        var builder = pipelineClass.getMethod("builder", snippets.javaClass).invoke(null, snippets)
        builder = invokeBuilder(builder, "withLocation", "pipeline/axion_xray_quads")
        builder = invokeBuilder(builder, "withVertexShader", "core/rendertype_lightning")
        builder = invokeBuilder(builder, "withFragmentShader", "core/rendertype_lightning")
        builder = configureBlend(builder)
        builder = invokeIfPresent(builder, "withCull", false)
        builder = configureNoDepth(builder)
        builder = invokeBuilder(builder, "withVertexFormat", positionColorVertexFormat(), quadsDrawMode())
        return builder.javaClass.getMethod("build").invoke(builder).let(::registerPipelineIfPossible)
    }

    private fun configureBlend(builder: Any): Any {
        val blendFunction = staticField("com.mojang.blaze3d.pipeline.BlendFunction", "TRANSLUCENT")
        invokeIfPresent(builder, "withBlend", blendFunction)?.let { return it }

        val colorTargetClass = Class.forName("com.mojang.blaze3d.pipeline.ColorTargetState")
        val colorTarget = colorTargetClass.getConstructor(blendFunction.javaClass).newInstance(blendFunction)
        return invokeBuilder(builder, "withColorTargetState", colorTarget)
    }

    private fun configureNoDepth(builder: Any): Any {
        val depthFunction = staticFieldOrNull("com.mojang.blaze3d.platform.DepthTestFunction", "NO_DEPTH_TEST")
        if (depthFunction != null) {
            var configured = invokeBuilder(builder, "withDepthTestFunction", depthFunction)
            configured = invokeBuilder(configured, "withDepthWrite", false)
            return configured
        }

        val compareOp = staticField("com.mojang.blaze3d.platform.CompareOp", "ALWAYS_PASS")
        val depthStencilClass = Class.forName("com.mojang.blaze3d.pipeline.DepthStencilState")
        val depthStencil = depthStencilClass
            .getConstructor(compareOp.javaClass, Boolean::class.javaPrimitiveType)
            .newInstance(compareOp, false)
        return invokeBuilder(builder, "withDepthStencilState", depthStencil)
    }

    private fun createModernLayer(pipeline: Any): RenderLayer? {
        val renderSetupClass = candidateClasses(
            "net.minecraft.client.render.RenderSetup",
            "net.minecraft.client.renderer.rendertype.RenderSetup",
        ).firstOrNull() ?: return null
        val builder = renderSetupClass.getMethod("builder", pipeline.javaClass).invoke(null, pipeline)
        invokeIfPresent(builder, "translucent")
        invokeIfPresent(builder, "expectedBufferSize", 1536)
        val renderSetup = builder.javaClass.getMethod("build").invoke(builder)
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name in setOf("of", "create") &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isInstance(renderSetup)
        } ?: return null
        factory.isAccessible = true
        return factory.invoke(null, "axion_xray_quads", renderSetup) as RenderLayer
    }

    private fun createLegacyLayer(pipeline: Any): RenderLayer? {
        val paramsClass = Class.forName("net.minecraft.client.render.RenderLayer\$MultiPhaseParameters")
        val builder = paramsClass.getMethod("builder").invoke(null)
        val build = builder.javaClass.declaredMethods.firstOrNull { method ->
            method.name == "build" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType
        } ?: return null
        build.isAccessible = true
        val params = build.invoke(builder, false)
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == "of" &&
                method.parameterCount == 4 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2].isInstance(pipeline) &&
                method.parameterTypes[3].isInstance(params)
        } ?: return null
        factory.isAccessible = true
        return factory.invoke(null, "axion_xray_quads", 1536, pipeline, params) as RenderLayer
    }

    private fun registerPipelineIfPossible(pipeline: Any): Any {
        val renderPipelines = candidateClasses(
            "net.minecraft.client.gl.RenderPipelines",
            "net.minecraft.client.renderer.RenderPipelines",
        ).firstOrNull() ?: return pipeline
        val register = renderPipelines.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == "register" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isInstance(pipeline)
        } ?: return pipeline
        register.isAccessible = true
        return register.invoke(null, pipeline) ?: pipeline
    }

    private fun positionColorVertexFormat(): Any {
        return staticFieldOrNull("net.minecraft.client.render.VertexFormats", "POSITION_COLOR")
            ?: staticField("com.mojang.blaze3d.vertex.DefaultVertexFormat", "POSITION_COLOR")
    }

    private fun quadsDrawMode(): Any {
        return staticFieldOrNull("com.mojang.blaze3d.vertex.VertexFormat\$DrawMode", "QUADS")
            ?: staticField("com.mojang.blaze3d.vertex.VertexFormat\$Mode", "QUADS")
    }

    private fun invokeBuilder(builder: Any, methodName: String, vararg args: Any): Any {
        return invokeIfPresent(builder, methodName, *args)
            ?: error("Missing RenderPipeline.Builder.$methodName(${args.joinToString { it.javaClass.name }})")
    }

    private fun invokeIfPresent(target: Any, methodName: String, vararg args: Any): Any? {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterCount == args.size &&
                method.parameterTypes.zip(args).all { (type, arg) ->
                    if (type.isPrimitive) primitiveMatches(type, arg) else type.isInstance(arg)
                }
        } ?: return null
        return method.invoke(target, *args) ?: target
    }

    private fun primitiveMatches(type: Class<*>, arg: Any): Boolean {
        return (type == Boolean::class.javaPrimitiveType && arg is Boolean) ||
            (type == Int::class.javaPrimitiveType && arg is Int) ||
            (type == Float::class.javaPrimitiveType && arg is Float) ||
            (type == Double::class.javaPrimitiveType && arg is Double)
    }

    private fun staticField(className: String, fieldName: String): Any {
        return staticFieldOrNull(className, fieldName) ?: error("Missing $className.$fieldName")
    }

    private fun staticFieldOrNull(className: String, fieldName: String): Any? {
        val owner = runCatching { Class.forName(className) }.getOrNull() ?: return null
        return runCatching {
            owner.getField(fieldName).get(null)
        }.getOrNull()
    }

    private fun logResolveFailure(key: String, methodNames: Set<String>, fieldNames: List<String>) {
        if (!loggedWarnings.add("resolve-$key")) return
        logger.warn(
            "[RenderLayerCompat] Failed to resolve {}. methodCandidates={} fieldCandidates={} layerClass={} factories={}",
            key,
            methodNames.joinToString(),
            fieldNames.joinToString(),
            RenderLayer::class.java.name,
            renderLayerFactoryClasses.joinToString { it.name },
        )
        renderLayerFactoryClasses.forEach { owner ->
            val methods = owner.declaredMethods
                .asSequence()
                .filter { Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
                .take(30)
                .map { "${it.name}:${it.returnType.name}" }
                .joinToString()
            val fields = owner.declaredFields
                .asSequence()
                .filter { Modifier.isStatic(it.modifiers) }
                .take(30)
                .map { "${it.name}:${it.type.name}" }
                .joinToString()
            logger.warn("[RenderLayerCompat] {} static no-arg methods: {}", owner.name, methods)
            logger.warn("[RenderLayerCompat] {} static fields: {}", owner.name, fields)
        }
    }
}
