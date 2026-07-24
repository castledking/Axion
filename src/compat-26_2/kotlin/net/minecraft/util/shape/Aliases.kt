package net.minecraft.util.shape

typealias VoxelShape = net.minecraft.world.phys.shapes.VoxelShape

object VoxelShapes {
    fun empty(): VoxelShape = net.minecraft.world.phys.shapes.Shapes.empty()

    fun cuboid(box: net.minecraft.world.phys.AABB): VoxelShape =
        net.minecraft.world.phys.shapes.Shapes.create(box)

    fun union(first: VoxelShape, second: VoxelShape): VoxelShape =
        net.minecraft.world.phys.shapes.Shapes.or(first, second)

    fun matchesAnywhere(
        first: VoxelShape,
        second: VoxelShape,
        predicate: net.minecraft.world.phys.shapes.BooleanOp,
    ): Boolean = net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(first, second, predicate)
}
