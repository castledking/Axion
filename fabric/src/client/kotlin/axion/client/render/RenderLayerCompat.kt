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
        val renderSetupClasses = candidateClasses(
            "net.minecraft.client.render.RenderSetup",
            "net.minecraft.client.renderer.rendertype.RenderSetup",
            "net.minecraft.class_12247",
        )
        RenderLayer::class.java.declaredFields.firstOrNull { field ->
            renderSetupClasses.any { setupClass -> field.type == setupClass }
        }?.also { field -> field.isAccessible = true }
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
    private val classicPhaseIntermediaryMappings = mapOf(
        "COLOR_PROGRAM" to RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_29442",
            descriptor = "Lnet/minecraft/class_4668\$class_5942;",
        ),
        "TRANSLUCENT_TRANSPARENCY" to RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_21370",
            descriptor = "Lnet/minecraft/class_4668\$class_4685;",
        ),
        "ALWAYS_DEPTH_TEST" to RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_21346",
            descriptor = "Lnet/minecraft/class_4668\$class_4672;",
        ),
        "COLOR_MASK" to RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_21350",
            descriptor = "Lnet/minecraft/class_4668\$class_4686;",
        ),
        "DISABLE_CULLING" to RuntimeFieldMapping(
            namespace = "intermediary",
            owner = "net.minecraft.class_4668",
            name = "field_21345",
            descriptor = "Lnet/minecraft/class_4668\$class_4671;",
        ),
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

    /**
     * Vanilla's lightning pipeline culls back faces. Raw cuboid fills need a
     * reverse winding on that layer to remain visible from either side, while
     * the no-cull debug/x-ray layers must keep a single winding so alpha is not
     * blended twice on the same plane.
     */
    fun requiresReverseWinding(layer: RenderLayer): Boolean = layer === lightning()

    fun xrayQuads(): RenderLayer {
        layerCache["xrayQuads"]?.let { return it }
        return runCatching {
            createXrayQuadsLayer()
        }.onFailure { throwable ->
            if (loggedWarnings.add("xrayQuads")) {
                // The fallback is not cosmetic: lightning() writes depth, so the
                // selection fill, the outline and the symmetry anchor all start
                // occluding the GPU preview instead of showing through it. This
                // is the first thing to check when x-ray surfaces look wrong on
                // a new Minecraft version.
                logger.error(
                    "[RenderLayerCompat] Failed to build the no-depth xrayQuads pipeline; " +
                        "falling back to lightning, which WRITES DEPTH. Selection fills, outlines " +
                        "and the symmetry anchor will hide the block preview instead of showing " +
                        "through blocks.",
                    throwable,
                )
            }
        }.getOrElse { lightning() }.also { layerCache["xrayQuads"] = it }
    }

    fun debugQuads(): RenderLayer = resolve(
        key = "debugQuads",
        namedMethodNames = listOf("debugQuads", "getDebugQuads"),
        fieldNames = emptyList(),
    )

    /**
     * Translucent quad layer for selection fills and ghost overlays.
     *
     * Iris has no shader program for the vanilla `debug_quads` pipeline and, when
     * a pack is loaded, throws `Missing program minecraft:pipeline/debug_quads`
     * the moment that layer is drawn -- so the selection silently vanishes under
     * shaders. `lightning()` uses the `rendertype_lightning` program, which Iris
     * does override, so it renders under a pack (its depth test is on, so it no
     * longer shows through terrain, but it is visible -- the priority here).
     *
     * Without a pack, `debug_quads` is depth-write-off and shows through terrain,
     * which is what the x-ray look wants, so keep it.
     */
    fun shaderSafeQuads(): RenderLayer {
        return if (ShaderPackCompat.isShaderPackActive()) lightning() else debugQuads()
    }

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

    /**
     * Creates a pipeline-backed layer without relying on the source visibility
     * of RenderLayer.create(String, RenderSetup). Mojang keeps that factory
     * package-private in the shipped 26.x classes even though Loom widens it in
     * the development compile classpath.
     */
    fun createPipelineLayer(name: String, renderSetup: Any): RenderLayer {
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                RenderLayer::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isInstance(renderSetup)
        } ?: error("Missing pipeline-backed RenderLayer factory")
        factory.isAccessible = true
        return factory.invoke(null, name, renderSetup) as RenderLayer
    }

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
    }

    private fun candidateClasses(vararg namedClassNames: String): List<Class<*>> {
        val resolver = FabricLoader.getInstance().mappingResolver
        val classNames = runtimeClassNameCandidates(namedClassNames.asIterable()) { namespace, className ->
            resolver.mapClassName(namespace, className)
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
        if (pipelineLayer != null) {
            logger.info("[RenderLayerCompat] Created pipeline-backed xrayQuads layer")
            return pipelineLayer
        }

        val classicLayer = createClassicXrayQuadsLayer()
        if (classicLayer != null) {
            logger.info("[RenderLayerCompat] Created classic xrayQuads layer")
            return classicLayer
        }
        error("No compatible RenderLayer factory for xrayQuads")
    }

    /**
     * Minecraft 1.21-1.21.3 predates RenderPipeline. Build the equivalent
     * color-only layer reflectively because Yarn exposes MultiPhaseParameters
     * with different source visibility between 1.21 and 1.21.1.
     */
    private fun createClassicXrayQuadsLayer(): RenderLayer? {
        // Derive the private MultiPhaseParameters class from RenderLayer's
        // factory signature. Production Fabric runtimes do not expose the
        // "named" namespace, so resolving this class by its Yarn name works in
        // Loom development runs but fails in normal launchers.
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                RenderLayer::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 7 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                method.parameterTypes[4] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[5] == Boolean::class.javaPrimitiveType
        } ?: return null
        val paramsClass = factory.parameterTypes[6]
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
        // The factory accepts a VertexFormat instance, but POSITION_COLOR is
        // declared on the separate VertexFormats holder class. Looking the
        // field up on parameterTypes[1] works at compile time yet always fails
        // at runtime, forcing the depth-writing lightning fallback.
        val vertexFormat = positionColorVertexFormat()
        val drawMode = quadsDrawMode()
        if (!factory.parameterTypes[1].isInstance(vertexFormat) ||
            !factory.parameterTypes[2].isInstance(drawMode)
        ) {
            return null
        }
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
        val mappings = buildList {
            namedCandidates.forEach { candidate ->
                add(
                    RuntimeFieldMapping(
                        namespace = "named",
                        owner = owner,
                        name = candidate,
                        descriptor = descriptor,
                    ),
                )
            }
            add(
                classicPhaseIntermediaryMappings[namedField]
                    ?: error("Missing intermediary mapping for RenderPhase.$namedField"),
            )
        }
        val phaseClass = RenderLayer::class.java.superclass
        return mappedStaticField(
            ownerClass = phaseClass,
            rawNames = namedCandidates,
            mappings = mappings,
        )
    }

    private fun mappedStaticField(
        ownerClass: Class<*>,
        rawNames: Iterable<String>,
        mappings: Iterable<RuntimeFieldMapping>,
    ): Any {
        val resolver = FabricLoader.getInstance().mappingResolver
        val fieldNames = runtimeFieldNameCandidates(rawNames, mappings) { mapping ->
            resolver.mapFieldName(
                mapping.namespace,
                mapping.owner,
                mapping.name,
                mapping.descriptor,
            )
        }
        val field = (ownerClass.fields.asSequence() + ownerClass.declaredFields.asSequence())
            .firstOrNull { candidate ->
                Modifier.isStatic(candidate.modifiers) && candidate.name in fieldNames
            }
            ?: error("Missing mapped field on ${ownerClass.name}; candidates=${fieldNames.joinToString()}")
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
        builder = configureVertexFormat(builder)
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
            "net.minecraft.class_10799",
        )
        val owners = candidateClasses(*namedOwners.toTypedArray())
        val resolver = FabricLoader.getInstance().mappingResolver
        val descriptor = "Lcom/mojang/blaze3d/pipeline/RenderPipeline\$Snippet;"
        val mappings = buildList {
            namedOwners
                .filterNot { it == "net.minecraft.class_10799" }
                .forEach { owner ->
                    namedFieldNames.forEach { field ->
                        add(RuntimeFieldMapping("named", owner, field, descriptor))
                    }
                }
            // The transform/fog snippet changed name in 1.21.7 while retaining
            // the same owner. Keep both intermediary fields so production
            // launchers do not depend on Yarn's development-only namespace.
            add(
                RuntimeFieldMapping(
                    "intermediary",
                    "net.minecraft.class_10799",
                    "field_56850",
                    descriptor,
                ),
            )
            add(
                RuntimeFieldMapping(
                    "intermediary",
                    "net.minecraft.class_10799",
                    "field_60127",
                    descriptor,
                ),
            )
        }
        val fieldNames = runtimeFieldNameCandidates(namedFieldNames, mappings) { mapping ->
            resolver.mapFieldName(
                mapping.namespace,
                mapping.owner,
                mapping.name,
                mapping.descriptor,
            )
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

    /**
     * 26.2 split `withVertexFormat(format, mode)` into `withVertexBinding(index,
     * format)` plus `withPrimitiveTopology(topology)`.
     *
     * This matters more than a rename: when it fails the whole pipeline throws,
     * `xrayQuads()` silently falls back to `lightning()` — which *writes depth*
     * — and every x-ray surface (selection fill, outline, symmetry anchor)
     * starts occluding the GPU preview instead of showing through it.
     */
    private fun configureVertexFormat(builder: Any): Any {
        val format = positionColorVertexFormat()
        val topology = quadsDrawMode()
        invokeIfPresent(builder, "withVertexFormat", format, topology)?.let { return it }

        val bound = invokeIfPresent(builder, "withVertexBinding", 0, format)
            ?: error("Missing RenderPipeline.Builder.withVertexFormat/withVertexBinding")
        return invokeBuilder(bound, "withPrimitiveTopology", topology)
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
        // Derive RenderSetup and its builder from RenderLayer's factory
        // signature. Their source names are not present in a normal
        // intermediary Fabric runtime.
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                RenderLayer::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == String::class.java
        } ?: return null
        val renderSetupClass = factory.parameterTypes[1]
        val builderFactory = renderSetupClass.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isInstance(pipeline) &&
                method.returnType.enclosingClass == renderSetupClass
        } ?: return null
        builderFactory.isAccessible = true
        val builder = builderFactory.invoke(null, pipeline)
        // RenderSetup's builder API changed again in 26.1: translucent uploads
        // are marked with sortOnUpload(), the buffer setter is bufferSize(), and
        // createRenderSetup() replaced build(). Keep the older names for every
        // 1.21.x range while accepting the new factory without falling back to
        // a depth-writing vanilla layer.
        if (invokeAnyIfPresent(builder, setOf("translucent", "method_75937")) == null) {
            invokeIfPresent(builder, "sortOnUpload")
        }
        val bufferSetter = builder.javaClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 1 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.returnType.isAssignableFrom(builder.javaClass)
        }
        if (bufferSetter != null) {
            bufferSetter.isAccessible = true
            bufferSetter.invoke(builder, 1536)
        } else if (invokeIfPresent(builder, "expectedBufferSize", 1536) == null) {
            invokeIfPresent(builder, "bufferSize", 1536)
        }
        val build = builder.javaClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 0 &&
                method.returnType == renderSetupClass
        }
        build?.isAccessible = true
        val renderSetup = build?.invoke(builder)
            ?: invokeIfPresent(builder, "build")
            ?: invokeIfPresent(builder, "createRenderSetup")
            ?: return null
        factory.isAccessible = true
        return factory.invoke(null, "axion_xray_quads", renderSetup) as RenderLayer
    }

    private fun createLegacyLayer(pipeline: Any): RenderLayer? {
        val factory = RenderLayer::class.java.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                RenderLayer::class.java.isAssignableFrom(method.returnType) &&
                method.parameterCount == 4 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2].isInstance(pipeline)
        } ?: return null
        val paramsClass = factory.parameterTypes[3]
        val builderFactory = paramsClass.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType.enclosingClass == paramsClass
        } ?: return null
        builderFactory.isAccessible = true
        val builder = builderFactory.invoke(null)
        val build = builder.javaClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 1 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                method.returnType == paramsClass
        } ?: return null
        build.isAccessible = true
        val params = build.invoke(builder, false)
        factory.isAccessible = true
        return factory.invoke(null, "axion_xray_quads", 1536, pipeline, params) as RenderLayer
    }

    private fun registerPipelineIfPossible(pipeline: Any): Any {
        val renderPipelines = candidateClasses(
            "net.minecraft.client.gl.RenderPipelines",
            "net.minecraft.client.renderer.RenderPipelines",
            "net.minecraft.class_10799",
        ).firstOrNull() ?: return pipeline
        val register = renderPipelines.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isInstance(pipeline) &&
                method.returnType.isInstance(pipeline)
        } ?: return pipeline
        register.isAccessible = true
        return register.invoke(null, pipeline) ?: pipeline
    }

    private fun positionColorVertexFormat(): Any {
        val mappings = listOf(
            RuntimeFieldMapping(
                "named",
                "net.minecraft.client.render.VertexFormats",
                "POSITION_COLOR",
                "Lnet/minecraft/client/render/VertexFormat;",
            ),
            RuntimeFieldMapping(
                "intermediary",
                "net.minecraft.class_290",
                "field_1576",
                "Lcom/mojang/blaze3d/vertex/VertexFormat;",
            ),
        )
        candidateClasses(
            "net.minecraft.client.render.VertexFormats",
            "net.minecraft.class_290",
        ).forEach { owner ->
            runCatching {
                return mappedStaticField(
                    ownerClass = owner,
                    rawNames = listOf("POSITION_COLOR", "field_1576"),
                    mappings = mappings,
                )
            }
        }
        return staticField("com.mojang.blaze3d.vertex.DefaultVertexFormat", "POSITION_COLOR")
    }

    private fun quadsDrawMode(): Any {
        val mappings = listOf(
            RuntimeFieldMapping(
                "named",
                "net.minecraft.client.render.VertexFormat\$DrawMode",
                "QUADS",
                "Lnet/minecraft/client/render/VertexFormat\$DrawMode;",
            ),
            RuntimeFieldMapping(
                "intermediary",
                "net.minecraft.class_293\$class_5596",
                "field_27382",
                "Lnet/minecraft/class_293\$class_5596;",
            ),
            RuntimeFieldMapping(
                "intermediary",
                "com.mojang.blaze3d.vertex.VertexFormat\$class_5596",
                "field_27382",
                "Lcom/mojang/blaze3d/vertex/VertexFormat\$class_5596;",
            ),
        )
        candidateClasses(
            "net.minecraft.client.render.VertexFormat\$DrawMode",
            "net.minecraft.class_293\$class_5596",
            "com.mojang.blaze3d.vertex.VertexFormat\$DrawMode",
            "com.mojang.blaze3d.vertex.VertexFormat\$class_5596",
            // 26.2 promoted the draw mode out of VertexFormat entirely.
            "com.mojang.blaze3d.PrimitiveTopology",
        ).forEach { owner ->
            runCatching {
                return mappedStaticField(
                    ownerClass = owner,
                    rawNames = listOf("QUADS", "field_27382"),
                    mappings = mappings,
                )
            }
        }
        return staticFieldOrNull("com.mojang.blaze3d.vertex.VertexFormat\$DrawMode", "QUADS")
            ?: staticFieldOrNull("com.mojang.blaze3d.vertex.VertexFormat\$Mode", "QUADS")
            ?: staticField("com.mojang.blaze3d.PrimitiveTopology", "QUADS")
    }

    private fun invokeBuilder(builder: Any, methodName: String, vararg args: Any): Any {
        return invokeIfPresent(builder, methodName, *args)
            ?: error("Missing RenderPipeline.Builder.$methodName(${args.joinToString { it.javaClass.name }})")
    }

    private fun invokeIfPresent(target: Any, methodName: String, vararg args: Any): Any? {
        return invokeAnyIfPresent(target, setOf(methodName), *args)
    }

    private fun invokeAnyIfPresent(target: Any, methodNames: Set<String>, vararg args: Any): Any? {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name in methodNames &&
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
