package axion.client.render

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.render.RenderLayer
import org.slf4j.LoggerFactory
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
