package axion.client.compat

import net.minecraft.client.render.Camera
import net.minecraft.util.math.Vec3d
import java.lang.reflect.Field

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

    fun getPos(camera: Camera): Vec3d {
        // Try the public position() method first (26.1.2+)
        try {
            return camera.position()
        } catch (_: NoSuchMethodException) {
            // Fall back to field access (1.21.11)
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
