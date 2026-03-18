package axion.client.mode

import axion.common.model.SymmetryConfig
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.client.MinecraftClient
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object BuildPlacementService {
    fun createPlacementOperation(
        client: MinecraftClient,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig? = null,
        replaceMode: Boolean = false,
        hand: Hand = Hand.MAIN_HAND,
    ): SymmetryPlacementOperation? {
        val player = client.player ?: return null
        val world = client.world ?: return null
        val stack = player.getStackInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        val primary = createPrimaryPlacement(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            hitResult = target.hitResult,
            replaceMode = replaceMode,
        ) ?: return null

        val placements = buildList {
            add(primary.placement)
            addAll(
                createDerivedPlacements(
                    world = world,
                    player = player,
                    hand = hand,
                    stack = stack,
                    blockItem = blockItem,
                    target = target,
                    symmetryConfig = symmetryConfig,
                    replaceMode = replaceMode,
                    primaryPos = primary.placement.pos,
                ),
            )
        }.distinctBy { it.pos }

        if (placements.isEmpty()) {
            return null
        }

        return SymmetryPlacementOperation(placements)
    }

    fun createDerivedPlacementOperation(
        client: MinecraftClient,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig,
        replaceMode: Boolean = false,
        hand: Hand = Hand.MAIN_HAND,
    ): SymmetryPlacementOperation? {
        val player = client.player ?: return null
        val world = client.world ?: return null
        val stack = player.getStackInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        val primary = createPrimaryPlacement(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            hitResult = target.hitResult,
            replaceMode = replaceMode,
        ) ?: return null

        val placements = createDerivedPlacements(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            target = target,
            symmetryConfig = symmetryConfig,
            replaceMode = replaceMode,
            primaryPos = primary.placement.pos,
        )
            .distinctBy { it.pos }

        if (placements.isEmpty()) {
            return null
        }

        return SymmetryPlacementOperation(placements)
    }

    private fun createPrimaryPlacement(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
        replaceMode: Boolean,
    ): PlacementResult? {
        return if (replaceMode) {
            createReplacePlacement(
                world = world,
                player = player,
                hand = hand,
                stack = stack,
                blockItem = blockItem,
                hitResult = hitResult,
            )
        } else {
            createAdjacentPlacement(
                world = world,
                player = player,
                hand = hand,
                stack = stack,
                blockItem = blockItem,
                hitResult = hitResult,
            )
        }
    }

    private fun createDerivedPlacements(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig?,
        replaceMode: Boolean,
        primaryPos: BlockPos,
    ): List<SymmetryBlockPlacement> {
        val config = symmetryConfig ?: return emptyList()
        return axion.client.symmetry.SymmetryTransformService.activeTransforms(config)
            .asSequence()
            .filterNot { transform ->
                transform.rotationQuarterTurns == 0 && !transform.mirrorY
            }
            .mapNotNull { transform ->
                val derivedPos = axion.client.symmetry.SymmetryTransformService.transformBlock(
                    sourceBlock = primaryPos,
                    anchor = config.anchor.position,
                    transform = transform,
                )
                if (derivedPos == primaryPos) {
                    return@mapNotNull null
                }
                val derivedSide = axion.client.symmetry.SymmetryTransformService.transformDirection(
                    target.hitResult.side,
                    transform,
                )
                if (replaceMode) {
                    createReplacePlacementAt(
                        world = world,
                        player = player,
                        hand = hand,
                        stack = stack,
                        blockItem = blockItem,
                        pos = derivedPos,
                        side = derivedSide,
                    )
                } else {
                    createAdjacentPlacementAt(
                        player = player,
                        hand = hand,
                        stack = stack,
                        blockItem = blockItem,
                        pos = derivedPos,
                        side = derivedSide,
                    )
                }
            }
            .distinctBy { it.pos }
            .toList()
    }

    private fun createAdjacentPlacement(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
    ): PlacementResult? {
        val rawContext = ItemPlacementContext(player, hand, stack, hitResult)
        val placementContext = blockItem.getPlacementContext(rawContext) ?: return null
        if (!placementContext.canPlace()) {
            return null
        }
        val placementPos = placementContext.blockPos.toImmutable()
        val placementState = blockItem.block.getPlacementState(placementContext) ?: return null
        if (!placementState.canPlaceAt(world, placementPos)) {
            return null
        }
        return PlacementResult(
            placement = SymmetryBlockPlacement(placementPos, placementState),
            canReplaceExisting = rawContext.canReplaceExisting(),
        )
    }

    private fun createAdjacentPlacementAt(
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        side: Direction,
    ): SymmetryBlockPlacement? {
        val supportPos = pos.offset(side.opposite)
        val hitPos = Vec3d.ofCenter(supportPos).add(
            side.offsetX * 0.5,
            side.offsetY * 0.5,
            side.offsetZ * 0.5,
        )
        val rawContext = ItemPlacementContext(
            player,
            hand,
            stack,
            BlockHitResult(hitPos, side, supportPos, false),
        )
        val placementContext = blockItem.getPlacementContext(rawContext) ?: return null
        if (placementContext.blockPos != pos || !placementContext.canPlace()) {
            return null
        }
        val placementState = blockItem.block.getPlacementState(placementContext) ?: return null
        if (!placementState.canPlaceAt(player.entityWorld, pos)) {
            return null
        }
        return SymmetryBlockPlacement(pos.toImmutable(), placementState)
    }

    private fun createReplacePlacement(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
    ): PlacementResult? {
        val placement = createReplacePlacementAt(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            pos = hitResult.blockPos.toImmutable(),
            hitResult = hitResult,
        ) ?: return null
        return PlacementResult(
            placement = placement,
            canReplaceExisting = true,
        )
    }

    private fun createReplacePlacementAt(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        hitResult: BlockHitResult,
    ): SymmetryBlockPlacement? {
        if (world.getBlockState(pos).isAir) {
            return null
        }

        val placementContext = object : ItemPlacementContext(world, player, hand, stack, hitResult) {
            override fun getBlockPos(): BlockPos = pos
            override fun canPlace(): Boolean = true
            override fun canReplaceExisting(): Boolean = true
        }
        val adjustedContext = blockItem.getPlacementContext(placementContext) ?: placementContext
        val placementState = blockItem.block.getPlacementState(adjustedContext) ?: return null
        if (!placementState.canPlaceAt(world, pos)) {
            return null
        }

        return SymmetryBlockPlacement(
            pos = pos.toImmutable(),
            state = placementState,
        )
    }

    private fun createReplacePlacementAt(
        world: net.minecraft.client.world.ClientWorld,
        player: net.minecraft.client.network.ClientPlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        side: Direction,
    ): SymmetryBlockPlacement? {
        val supportPos = pos.offset(side.opposite)
        val hitPos = Vec3d.ofCenter(supportPos).add(
            side.offsetX * 0.5,
            side.offsetY * 0.5,
            side.offsetZ * 0.5,
        )
        return createReplacePlacementAt(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            pos = pos,
            hitResult = BlockHitResult(hitPos, side, supportPos, false),
        )
    }

    private data class PlacementResult(
        val placement: SymmetryBlockPlacement,
        val canReplaceExisting: Boolean,
    )
}
