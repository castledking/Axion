package net.minecraft.util.math

typealias Box = net.minecraft.world.phys.AABB
typealias Direction = net.minecraft.core.Direction
typealias MathHelper = net.minecraft.util.Mth
typealias Vec3d = net.minecraft.world.phys.Vec3
typealias Axis = net.minecraft.core.Direction.Axis
typealias Vec3i = net.minecraft.core.Vec3i
typealias BlockPos = net.minecraft.core.BlockPos
typealias MutableBlockPos = net.minecraft.core.BlockPos.MutableBlockPos

// 26.2.x: Direction.unitVec3i provides the direction vector
val Direction.vector: Vec3i get() = this.unitVec3i
