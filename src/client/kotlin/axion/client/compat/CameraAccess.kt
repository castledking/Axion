package axion.client.compat

import net.minecraft.client.render.Camera
import net.minecraft.util.math.Vec3d

/**
 * Helper object to access Camera position in a version-compatible way.
 * In Minecraft 1.21.11+, camera.pos is private and needs to be accessed differently.
 */
object CameraAccess {
    fun getPos(camera: Camera): Vec3d {
        // Use the version-specific implementation
        return VersionCompatImpl.getCameraPos(camera)
    }
}
