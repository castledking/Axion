package axion.client.symmetry

import axion.common.model.SymmetryConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult

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
        val derivedPlacements = SymmetryTransformService.transformedBlocks(config, placementPos)
            .asSequence()
            .filterNot { it == placementPos }
            .filter { world.isInBuildLimit(it) }
            .distinct()
            .map { SymmetryPlacementResult.Placement(it, placementState) }
            .toList()

        return SymmetryPlacementResult(
            hitResult = hitResult,
            primaryPlacement = SymmetryPlacementResult.Placement(placementPos, placementState),
            derivedPlacements = derivedPlacements,
        )
    }
}
