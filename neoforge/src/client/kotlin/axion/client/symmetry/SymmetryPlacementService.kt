package axion.client.symmetry

import axion.client.compat.extents
import axion.common.model.SymmetryConfig
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import axion.client.compat.add

object SymmetryPlacementService {
    fun createPlacementResult(
        client: Minecraft,
        config: SymmetryConfig,
        hand: InteractionHand = InteractionHand.MAIN_HAND,
    ): SymmetryPlacementResult? {
        val player = client.player ?: return null
        val world = client.level ?: return null
        val hitResult = client.hitResult as? BlockHitResult ?: return null
        val stack = player.getItemInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        val rawContext = BlockPlaceContext(player, hand, stack, hitResult)
        val placementContext = rawContext
        if (!placementContext.canPlace()) {
            return null
        }

        val placementPos = hitResult.blockPos.immutable()
        if (!world.isInWorldBounds(placementPos)) {
            return null
        }

        val placementState = blockItem.block.defaultBlockState()
        val derivedPlacements = SymmetryTransformService.activeTransforms(config)
            .asSequence()
            .filterNot { transform ->
                transform.rotationQuarterTurns == 0 && transform.mirrorAxis == null
            }
            .map { transform ->
                val derivedPos = SymmetryTransformService.transformBlock(placementPos, config.anchor.position, transform)
                derivedPos to SymmetryTransformService.transformDirection(hitResult.direction, transform)
            }
            .filter { (derivedPos, _) -> derivedPos != placementPos && world.isInWorldBounds(derivedPos) }
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
        player: net.minecraft.world.entity.player.Player,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        originalContext: BlockPlaceContext,
        derivedPos: BlockPos,
        derivedSide: net.minecraft.core.Direction,
    ): SymmetryPlacementResult.Placement? {
        val supportPos = if (originalContext.canReplaceExisting()) {
            derivedPos
        } else {
            derivedPos.add(derivedSide.opposite.extents)
        }
        val hitPos = Vec3(supportPos.x + 0.5, supportPos.y + 0.5, supportPos.z + 0.5).add(
            derivedSide.stepX * 0.5,
            derivedSide.stepY * 0.5,
            derivedSide.stepZ * 0.5,
        )
        val derivedHit = BlockHitResult(hitPos, derivedSide, supportPos, false)
        val rawContext = BlockPlaceContext(player, hand, stack, derivedHit)
        val placementContext = rawContext
        if (derivedHit.blockPos != derivedPos || !placementContext.canPlace()) {
            return null
        }

        return SymmetryPlacementResult.Placement(
            derivedPos,
            blockItem.block.defaultBlockState(),
        )
    }
}
