package axion.client.itemStack

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

fun directionGetFacing(vec: Vec3): Direction = Direction.getFacing(vec.x, vec.y, vec.z)

fun floorMod(x: Int, y: Int): Int = Math.floorMod(x, y)
