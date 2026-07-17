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
        "getTranslucent" to "method_23596",
        "getDebugQuads" to "method_49042",
        "getDebugFilledBox" to "method_49047",
    )
    private val modernIntermediaryNames = mapOf(
        "lightning" to "method_76003",
        "lines" to "method_76015",
        "blockTranslucentCull" to "method_76545",
        "translucentMovingBlock" to "method_75977",
        "translucent" to "method_23596",
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

    fun entityTranslucent(): RenderLayer = resolve(
        key = "entityTranslucent",
        namedMethodNames = listOf("translucent", "getTranslucent", "entityTranslucent"),
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
        if (key == "entityTranslucent") {
            return translucentMovingBlock()
        }

        logResolveFailure(key, names, fieldNames)
        error("Missing RenderLayer.$key")

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
        val pipelineLayer = runCatching {
            val pipeline = createXrayQuadsPipeline()
            createModernLayer(pipeline) ?: createLegacyLayer(pipeline)
        }.getOrNull()
        return pipelineLayer ?: createClassicXrayQuadsLayer()
            ?: error("No compatible RenderLayer factory for xrayQuads")
    }

    /**
     * Minecraft 1.21-1.21.3 predates RenderPipeline. Build the equivalent
     * color-only layer reflectively because Yarn exposes MultiPhaseParameters
     * with different source visibility between 1.21 and 1.21.1.
     */
    private fun createClassicXrayQuadsLayer(): RenderLayer? {
        val paramsClass = candidateClasses(
            "net.minecraft.client.render.RenderLayer\$MultiPhaseParameters",
        ).firstOrNull() ?: return null
        val builderFactory = paramsClass.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType.enclosingClass == paramsClass
        } ?: return null
        builderFactory.isAccessible = true
        val builder = builderFactory.invoke(null)

        listOf(
            classicPhase("COLOR_PROGRAM", "ShaderProgram"),
            classicPhase("TRANSLUCENT_TRANSPARENCY", "Transparency"),
            classicPhase("ALWAYS_DEPTH_TEST", "DepthTest"),
            classicPhase("COLOR_MASK", "WriteMaskState"),
            classicPhase("DISABLE_CULLING", "Cull"),
        ).forEach { phase ->
            val setter = builder.javaClass.declaredMethods.firstOrNull { method ->
                method.parameterCount == 1 &&
                    method.parameterTypes[0].isInstance(phase) &&
                    method.returnType.isAssignableFrom(builder.javaClass)
            } ?: return null
            setter.isAccessible = true
            setter.invoke(builder, phase)
        }

        val build = builder.javaClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 1 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                method.returnType == paramsClass
        } ?: return null
        build.isAccessible = true
        val params = build.invoke(builder, false)
        val vertexFormat = positionColorVertexFormat()
        val drawMode = quadsDrawMode()
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                RenderLayer::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 7 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isInstance(vertexFormat) &&
                method.parameterTypes[2].isInstance(drawMode) &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                method.parameterTypes[4] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[5] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[6] == paramsClass
        } ?: return null
        factory.isAccessible = true
        return factory.invoke(
            null,
            "axion_xray_quads",
            vertexFormat,
            drawMode,
            1536,
            false,
            true,
            params,
        ) as RenderLayer
    }

    private fun classicPhase(namedField: String, namedPhaseType: String): Any {
        val resolver = FabricLoader.getInstance().mappingResolver
        val owner = "net.minecraft.client.render.RenderPhase"
        val descriptor = "Lnet/minecraft/client/render/RenderPhase\$$namedPhaseType;"
        // Mojang renamed the position/color shader phase between 1.21.1 and
        // 1.21.2. Try both named fields so the classic color-only layer can be
        // created throughout the complete 1.21-1.21.3 compatibility range.
        val namedCandidates = if (namedField == "COLOR_PROGRAM") {
            listOf("COLOR_PROGRAM", "POSITION_COLOR_PROGRAM")
        } else {
            listOf(namedField)
        }
        val fieldCandidates = namedCandidates.flatMap { candidate ->
            listOf(
                candidate,
                runCatching {
                    resolver.mapFieldName("named", owner, candidate, descriptor)
                }.getOrDefault(candidate),
            )
        }.toSet()
        val phaseClass = RenderLayer::class.java.superclass
        val field = phaseClass.declaredFields.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.name in fieldCandidates
        } ?: error("Missing RenderPhase.$namedField")
        field.isAccessible = true
        return field.get(null)
    }

    private fun createXrayQuadsPipeline(): Any {
        val pipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline")
        val snippetClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline\$Snippet")
        val inheritedSnippet = findXrayPipelineSnippet(snippetClass)
        val snippets = Array.newInstance(snippetClass, if (inheritedSnippet == null) 0 else 1)
        if (inheritedSnippet != null) {
            Array.set(snippets, 0, inheritedSnippet)
        }
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

    /**
     * In pipeline-based renderers the lightning shader's transform, projection,
     * and fog uniforms are declared by a version-specific vanilla snippet. A
     * pipeline built without that snippet can rasterize but logs unsupported
     * DynamicTransforms uniforms on 26.1 and has undefined positioning.
     */
    private fun findXrayPipelineSnippet(snippetClass: Class<*>): Any? {
        val namedFieldNames = setOf(
            // 1.21.7-1.21.11 Yarn
            "TRANSFORMS_PROJECTION_FOG_SNIPPET",
            // 26.1 official namespace
            "MATRICES_FOG_SNIPPET",
            // 1.21.5 Yarn
            "MATRICES_COLOR_FOG_SNIPPET",
        )
        val namedOwners = listOf(
            "net.minecraft.client.gl.RenderPipelines",
            "net.minecraft.client.renderer.RenderPipelines",
        )
        val owners = candidateClasses(*namedOwners.toTypedArray())
        val resolver = FabricLoader.getInstance().mappingResolver
        val descriptor = "Lcom/mojang/blaze3d/pipeline/RenderPipeline\$Snippet;"
        val fieldNames = buildSet {
            addAll(namedFieldNames)
            namedOwners.forEach { owner ->
                namedFieldNames.forEach { field ->
                    runCatching {
                        resolver.mapFieldName("named", owner, field, descriptor)
                    }.getOrNull()?.let(::add)
                }
            }
        }
        owners.forEach { owner ->
            val field = owner.declaredFields.firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    it.type == snippetClass &&
                    it.name in fieldNames
            }
            if (field != null) {
                field.isAccessible = true
                return field.get(null)
            }
        }
        return null
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
        // RenderSetup's builder API changed again in 26.1: translucent uploads
        // are marked with sortOnUpload(), the buffer setter is bufferSize(), and
        // createRenderSetup() replaced build(). Keep the older names for every
        // 1.21.x range while accepting the new factory without falling back to
        // a depth-writing vanilla layer.
        if (invokeIfPresent(builder, "translucent") == null) {
            invokeIfPresent(builder, "sortOnUpload")
        }
        if (invokeIfPresent(builder, "expectedBufferSize", 1536) == null) {
            invokeIfPresent(builder, "bufferSize", 1536)
        }
        val renderSetup = invokeIfPresent(builder, "build")
            ?: invokeIfPresent(builder, "createRenderSetup")
            ?: return null
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
        return mappedStaticFieldOrNull(
            "net.minecraft.client.render.VertexFormats",
            "POSITION_COLOR",
            "Lnet/minecraft/client/render/VertexFormat;",
        )
            ?: staticField("com.mojang.blaze3d.vertex.DefaultVertexFormat", "POSITION_COLOR")
    }

    private fun quadsDrawMode(): Any {
        return mappedStaticFieldOrNull(
            "net.minecraft.client.render.VertexFormat\$DrawMode",
            "QUADS",
            "Lnet/minecraft/client/render/VertexFormat\$DrawMode;",
        )
            ?: staticFieldOrNull("com.mojang.blaze3d.vertex.VertexFormat\$DrawMode", "QUADS")
            ?: staticField("com.mojang.blaze3d.vertex.VertexFormat\$Mode", "QUADS")
    }

    private fun mappedStaticFieldOrNull(owner: String, fieldName: String, descriptor: String): Any? {
        staticFieldOrNull(owner, fieldName)?.let { return it }
        val resolver = FabricLoader.getInstance().mappingResolver
        val runtimeOwner = runCatching { resolver.mapClassName("named", owner) }.getOrNull() ?: return null
        val runtimeField = runCatching {
            resolver.mapFieldName("named", owner, fieldName, descriptor)
        }.getOrNull() ?: return null
        val ownerClass = runCatching { Class.forName(runtimeOwner) }.getOrNull() ?: return null
        val field = runCatching { ownerClass.getDeclaredField(runtimeField) }.getOrNull() ?: return null
        field.isAccessible = true
        return runCatching { field.get(null) }.getOrNull()
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
