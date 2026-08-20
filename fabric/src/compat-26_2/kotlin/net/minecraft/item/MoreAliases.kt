package net.minecraft.item

typealias BlockItem = net.minecraft.world.item.BlockItem

open class ItemPlacementContext : net.minecraft.world.item.context.BlockPlaceContext {
    constructor(
        player: net.minecraft.world.entity.player.Player,
        hand: net.minecraft.world.InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        hitResult: net.minecraft.world.phys.BlockHitResult,
    ) : super(player, hand, stack, hitResult)

    constructor(
        world: net.minecraft.world.level.Level,
        player: net.minecraft.world.entity.player.Player,
        hand: net.minecraft.world.InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        hitResult: net.minecraft.world.phys.BlockHitResult,
    ) : super(world, player, hand, stack, hitResult)

    open fun getBlockPos(): net.minecraft.util.math.BlockPos =
        net.minecraft.util.math.BlockPos(getClickedPos())

    open fun canReplaceExisting(): Boolean = replacingClickedOnBlock()
}
