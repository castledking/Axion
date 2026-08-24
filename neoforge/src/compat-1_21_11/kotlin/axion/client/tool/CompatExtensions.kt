package axion.client.tool

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

fun directionGetFacing(vec: Vec3): Direction = Direction.getApproximateNearest(vec)

fun floorMod(x: Int, y: Int): Int = Math.floorMod(x, y)
