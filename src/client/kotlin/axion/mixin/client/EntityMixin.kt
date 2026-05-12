package axion.mixin.client

import axion.client.mode.ClientModeController
import net.minecraft.entity.Entity
import net.minecraft.entity.MovementType
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.lang.reflect.Method

@Mixin(Entity::class)
abstract class EntityMixin {
    @Shadow
    public abstract fun getX(): Double

    @Shadow
    public abstract fun getY(): Double

    @Shadow
    public abstract fun getZ(): Double

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun self(): Entity = this as Entity

    private fun setPositionCompat(x: Double, y: Double, z: Double) {
        val method = SET_POS_METHOD ?: return
        method.invoke(self(), x, y, z)
    }

    @Inject(method = ["move"], at = [At("HEAD")], cancellable = true)
    private fun axionApplyNoClipMovement(type: MovementType, movement: Vec3d, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        setPositionCompat(getX() + movement.x, getY() + movement.y, getZ() + movement.z)
        self().clearCollisionFlags()
        ci.cancel()
    }

    @Inject(method = ["pushOutOfBlocks"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun axionSuppressPushOutOfBlocks(x: Double, y: Double, z: Double, ci: CallbackInfo) {
        if (!ClientModeController.isNoClipActiveFor(self())) {
            return
        }

        ci.cancel()
    }

}

private val SET_POS_METHOD: Method? by lazy {
    val entityClass = Entity::class.java
    val parameters = arrayOf(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
    sequenceOf("setPos", "setPosition")
        .mapNotNull { name ->
            try {
                entityClass.getMethod(name, *parameters)
            } catch (_: NoSuchMethodException) {
                null
            }
        }
        .firstOrNull()
}

private val COLLISION_FIELD_NAMES = listOf(
    "horizontalCollision",
    "verticalCollision",
    "groundCollision",
    "collidedSoftly",
    "collidesHorizontally",
    "collidesVertically",
    "collides",
)

private fun Entity.clearCollisionFlags() {
    for (name in COLLISION_FIELD_NAMES) {
        try {
            val field = javaClass.getDeclaredField(name)
            if (!field.trySetAccessible()) {
                continue
            }
            when (field.type) {
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> field.setBoolean(this, false)
            }
        } catch (_: NoSuchFieldException) {
            // Ignore missing fields – names changed between versions.
        } catch (_: IllegalAccessException) {
            // Ignore inaccessible fields.
        }
    }
}
