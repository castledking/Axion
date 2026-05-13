package axion.client.compat

import net.minecraft.client.render.Camera
import net.minecraft.util.math.Vec3d
import java.lang.reflect.Field
import java.lang.reflect.Method

object CameraAccess {
    private val posField: Field? by lazy {
        try {
            Camera::class.java.getDeclaredField("pos").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            Camera::class.java.declaredFields.firstOrNull { it.type == Vec3d::class.java }
                ?.apply { isAccessible = true }
        }
    }

    private val positionMethod: Method? by lazy {
        Camera::class.java.methods.firstOrNull { it.name == "position" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull { it.name == "getPos" && it.parameterCount == 0 }
            ?: Camera::class.java.methods.firstOrNull {
                it.parameterCount == 0 && it.returnType == Vec3d::class.java
            }
    }

    fun getPos(camera: Camera): Vec3d {
        positionMethod?.let { method ->
            try {
                return method.invoke(camera) as Vec3d
            } catch (_: Exception) {}
        }

        val field = posField
        if (field != null) {
            try {
                return field.get(camera) as Vec3d
            } catch (_: Exception) {}
        }

        return VersionCompatImpl.getCameraPos(camera)
    }
}
