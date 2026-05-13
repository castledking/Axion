package axion.client.compat

import net.minecraft.client.render.Camera
import net.minecraft.util.math.Vec3d
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Helper object to access Camera position in a version-compatible way.
 * Uses reflection to handle different Minecraft versions.
 */
object CameraAccess {
    private val posField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    private val positionMethod: Method? by lazy {
        Camera::class.java.methods.firstOrNull { it.name == "position" && it.parameterCount == 0 }
    }

    fun getPos(camera: Camera): Vec3d {
        positionMethod?.let { method ->
            try {
                return method.invoke(camera) as Vec3d
            } catch (_: Exception) {
                // Fall back to field access.
            }
        }

        // Fall back to reflection on pos field
        val field = posField
        if (field != null) {
            try {
                return field.get(camera) as Vec3d
            } catch (_: Exception) {
                // Fall through
            }
        }

        // Last resort: use VersionCompatImpl
        return VersionCompatImpl.getCameraPos(camera)
    }
}
