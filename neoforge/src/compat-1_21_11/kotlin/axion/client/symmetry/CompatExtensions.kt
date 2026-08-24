package axion.client.symmetry

import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

fun directionGetFacing(vec: Vec3): Direction = Direction.getFacing(vec.x, vec.y, vec.z)

val BlockHitResult.direction: Direction
    get() = side
