package axion.client.render

import net.minecraft.core.Direction

object AxisProbe2 {
    fun probe(d: Direction): net.minecraft.world.phys.Vec3 = d.unitVec3
    fun probeI(d: Direction): net.minecraft.core.Vec3i = d.unitVec3i
    fun nearest(v: net.minecraft.world.phys.Vec3): Direction = Direction.getApproximateNearest(v)
}
