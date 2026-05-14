package axion.client.tool

import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

fun directionGetFacing(vec: Vec3d): Direction = Direction.getFacing(vec.x, vec.y, vec.z)

fun floorMod(x: Int, y: Int): Int = Math.floorMod(x, y)
