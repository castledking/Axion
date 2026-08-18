package axion.client.mode

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.entity.Entity
import java.lang.reflect.Method

/**
 * Reflection helpers used by [axion.mixin.client.EntityMixin] to apply
 * no-clip movement without coupling to a specific Minecraft mapping.
 *
 * IMPORTANT: this file MUST live outside the `axion.mixin.client.*` package.
 * Mixin's class-load policy refuses to resolve any class inside a defined
 * mixin package when it is referenced from a mixin handler, which would
 * otherwise crash with `IllegalClassLoadError` the first time a mixin
 * inject runs (see crash report for v0.2.7 on 1.21.11). Keeping these
 * top-level declarations in a regular client package avoids that.
 */
object EntityNoClipSupport {
    private val setPosMethod: Method? by lazy {
        val entityClass = Entity::class.java
        val parameters = arrayOf(
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        )

        // Build candidate list:
        //  * Compile-time names (work in IDE dev environments where the
        //    runtime namespace matches the dev namespace).
        //  * Intermediary name (production 1.21.x runtime is intermediary).
        //  * MappingResolver: translate the compile-time named symbol to
        //    whatever the current runtime namespace actually uses.
        // The first reflection lookup that succeeds wins.
        val candidates = linkedSetOf(
            "setPos",         // Mojang official (26.1 dev + production)
            "setPosition",    // Yarn (1.21.x dev)
            "method_5814",    // Intermediary (1.21.x production)
        )

        runCatching {
            val resolver = FabricLoader.getInstance().mappingResolver
            listOf(
                "net.minecraft.world.entity.Entity" to "setPos",
                "net.minecraft.entity.Entity" to "setPosition",
            ).forEach { (className, methodName) ->
                resolver.mapMethodName("named", className, methodName, "(DDD)V")
                    ?.let(candidates::add)
            }
        }

        candidates.firstNotNullOfOrNull { name ->
            try {
                entityClass.getMethod(name, *parameters)
            } catch (_: NoSuchMethodException) {
                null
            }
        }
    }

    /**
     * Compile-time names of the collision flags on `Entity`, paired with the
     * class each one is declared in so the runtime name can be resolved.
     *
     * `verticalCollisionBelow` and `minorHorizontalCollision` matter as much as
     * the two obvious ones: leaving them set is what still nudges a no-clipping
     * player while they pass through a block vertically.
     */
    private val collisionFieldNames = listOf(
        "horizontalCollision",
        "verticalCollision",
        "verticalCollisionBelow",
        "minorHorizontalCollision",
        "groundCollision",
        "collidedSoftly",
        "collidesHorizontally",
        "collidesVertically",
        "collides",
    )

    /**
     * Every name the collision flags might carry at runtime.
     *
     * 26.x runs in the official namespace, so the compile-time names match. On
     * 1.21.x production the runtime is intermediary, so the mapping resolver has
     * to translate them.
     */
    private val collisionFieldCandidates: List<String> by lazy {
        val candidates = linkedSetOf<String>()
        candidates += collisionFieldNames

        runCatching {
            val resolver = FabricLoader.getInstance().mappingResolver
            listOf(
                "net.minecraft.world.entity.Entity",
                "net.minecraft.entity.Entity",
            ).forEach { className ->
                collisionFieldNames.forEach { fieldName ->
                    resolver.mapFieldName("named", className, fieldName, "Z")
                        ?.let(candidates::add)
                }
            }
        }

        candidates.toList()
    }

    fun setPosition(entity: Entity, x: Double, y: Double, z: Double) {
        val method = setPosMethod ?: return
        method.invoke(entity, x, y, z)
    }

    fun clearCollisionFlags(entity: Entity) {
        for (name in collisionFieldCandidates) {
            // The flags are declared on Entity, but the instance is a concrete
            // subclass such as LocalPlayer. getDeclaredField only ever looks at
            // the exact class it is called on, so the hierarchy has to be walked
            // by hand — otherwise every lookup misses and no flag is ever cleared.
            val field = declaredFieldInHierarchy(entity.javaClass, name) ?: continue
            if (field.type != Boolean::class.javaPrimitiveType && field.type != Boolean::class.javaObjectType) {
                continue
            }
            try {
                if (!field.trySetAccessible()) {
                    continue
                }
                field.setBoolean(entity, false)
            } catch (_: IllegalAccessException) {
                // Ignore inaccessible fields.
            }
        }
    }

    private fun declaredFieldInHierarchy(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
