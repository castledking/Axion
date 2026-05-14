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

    private val collisionFieldNames = listOf(
        "horizontalCollision",
        "verticalCollision",
        "groundCollision",
        "collidedSoftly",
        "collidesHorizontally",
        "collidesVertically",
        "collides",
    )

    fun setPosition(entity: Entity, x: Double, y: Double, z: Double) {
        val method = setPosMethod ?: return
        method.invoke(entity, x, y, z)
    }

    fun clearCollisionFlags(entity: Entity) {
        for (name in collisionFieldNames) {
            try {
                val field = entity.javaClass.getDeclaredField(name)
                if (!field.trySetAccessible()) {
                    continue
                }
                when (field.type) {
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType ->
                        field.setBoolean(entity, false)
                }
            } catch (_: NoSuchFieldException) {
                // Ignore missing fields – names changed between versions.
            } catch (_: IllegalAccessException) {
                // Ignore inaccessible fields.
            }
        }
    }
}
