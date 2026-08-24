package axion.client.compat

import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Field
import java.lang.reflect.Method

object CameraAccess {
    private val posField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            Camera::class.java.declaredFields.firstOrNull { it.type == Vec3::class.java }
                ?.apply { isAccessible = true }
        }
    }

    private val positionMethod: Method? by lazy {
        Camera::class.java.methods.firstOrNull { it.name == "position" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull { it.name == "getPos" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull {
                it.parameterCount == 0 && it.returnType == Vec3::class.java
            }
    }

    fun getPos(camera: Camera): Vec3 {
        positionMethod?.let { method ->
            try {
                return method.invoke(camera) as Vec3
            } catch (_: Exception) {}
        }

        val field = posField
        if (field != null) {
            try {
                return field.get(camera) as Vec3
            } catch (_: Exception) {}
        }

        return VersionCompatImpl.getCameraPos(camera)
    }
}
