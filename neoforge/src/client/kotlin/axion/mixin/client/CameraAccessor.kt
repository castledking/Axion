package axion.mixin.client

import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(Camera::class)
interface CameraAccessor {
    @Accessor("pos")
    fun axionGetPos(): Vec3
}
