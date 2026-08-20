package net.minecraft.world

typealias World = net.minecraft.world.level.Level
typealias BlockView = net.minecraft.world.level.BlockGetter

class RaycastContext(
    start: net.minecraft.world.phys.Vec3,
    end: net.minecraft.world.phys.Vec3,
    shapeType: ShapeType,
    fluidHandling: FluidHandling,
    entity: net.minecraft.world.entity.Entity,
) : net.minecraft.world.level.ClipContext(start, end, shapeType.delegate, fluidHandling.delegate, entity) {
    enum class ShapeType(val delegate: net.minecraft.world.level.ClipContext.Block) {
        COLLIDER(net.minecraft.world.level.ClipContext.Block.COLLIDER),
        OUTLINE(net.minecraft.world.level.ClipContext.Block.OUTLINE),
        VISUAL(net.minecraft.world.level.ClipContext.Block.VISUAL),
        FALLDAMAGE_RESETTING(net.minecraft.world.level.ClipContext.Block.FALLDAMAGE_RESETTING),
    }

    enum class FluidHandling(val delegate: net.minecraft.world.level.ClipContext.Fluid) {
        NONE(net.minecraft.world.level.ClipContext.Fluid.NONE),
        SOURCE_ONLY(net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY),
        ANY(net.minecraft.world.level.ClipContext.Fluid.ANY),
        WATER(net.minecraft.world.level.ClipContext.Fluid.WATER),
    }
}
