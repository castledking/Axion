package axion.mixin.client

import net.minecraft.client.render.Camera
import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(Camera::class)
interface CameraAccessor {
    @Accessor("pos")
    fun axionGetPos(): Vec3d
}
