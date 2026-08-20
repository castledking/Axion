package axion.client.symmetry

import net.minecraft.util.math.Direction
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Vec3d

fun directionGetFacing(vec: Vec3d): Direction = Direction.getFacing(vec.x, vec.y, vec.z)

val BlockHitResult.direction: Direction
    get() = side
