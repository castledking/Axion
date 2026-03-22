package axion.client.symmetry

import axion.common.model.SymmetryConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object SymmetryPlacementService {
    fun createPlacementResult(
        client: MinecraftClient,
        config: SymmetryConfig,
        hand: Hand = Hand.MAIN_HAND,
    ): SymmetryPlacementResult? {
        val player = client.player ?: return null
        val world = client.world ?: return null
        val hitResult = client.crosshairTarget as? BlockHitResult ?: return null
        val stack = player.getStackInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        val rawContext = ItemPlacementContext(player, hand, stack, hitResult)
        val placementContext = blockItem.getPlacementContext(rawContext) ?: return null
        if (!placementContext.canPlace()) {
            return null
        }

        val placementPos = placementContext.blockPos.toImmutable()
        if (!world.isInBuildLimit(placementPos)) {
            return null
        }

        val placementState = blockItem.block.getPlacementState(placementContext) ?: return null
        val derivedPlacements = SymmetryTransformService.activeTransforms(config)
            .asSequence()
            .filterNot { transform ->
                transform.rotationQuarterTurns == 0 && transform.mirrorAxis == null
            }
            .map { transform ->
                val derivedPos = SymmetryTransformService.transformBlock(placementPos, config.anchor.position, transform)
                derivedPos to SymmetryTransformService.transformDirection(placementContext.side, transform)
            }
            .filter { (derivedPos, _) -> derivedPos != placementPos && world.isInBuildLimit(derivedPos) }
            .distinctBy { (derivedPos, _) -> derivedPos }
            .mapNotNull { (derivedPos, derivedSide) ->
                derivedPlacement(
                    player = player,
                    hand = hand,
                    stack = stack,
                    blockItem = blockItem,
                    originalContext = placementContext,
                    derivedPos = derivedPos,
                    derivedSide = derivedSide,
                )
            }
            .toList()

        return SymmetryPlacementResult(
            hitResult = hitResult,
            primaryPlacement = SymmetryPlacementResult.Placement(placementPos, placementState),
            derivedPlacements = derivedPlacements,
        )
    }

    private fun derivedPlacement(
        player: net.minecraft.entity.player.PlayerEntity,
        hand: Hand,
        stack: net.minecraft.item.ItemStack,
        blockItem: BlockItem,
        originalContext: ItemPlacementContext,
        derivedPos: BlockPos,
        derivedSide: net.minecraft.util.math.Direction,
    ): SymmetryPlacementResult.Placement? {
        val supportPos = if (originalContext.canReplaceExisting()) {
            derivedPos
        } else {
            derivedPos.offset(derivedSide.opposite)
        }
        val hitPos = Vec3d.ofCenter(supportPos).add(
            derivedSide.offsetX * 0.5,
            derivedSide.offsetY * 0.5,
            derivedSide.offsetZ * 0.5,
        )
        val derivedHit = BlockHitResult(hitPos, derivedSide, supportPos, false)
        val rawContext = ItemPlacementContext(player, hand, stack, derivedHit)
        val placementContext = blockItem.getPlacementContext(rawContext) ?: return null
        if (placementContext.blockPos != derivedPos || !placementContext.canPlace()) {
            return null
        }

        val placementState = blockItem.block.getPlacementState(placementContext) ?: return null
        return SymmetryPlacementResult.Placement(derivedPos.toImmutable(), placementState)
    }
}
