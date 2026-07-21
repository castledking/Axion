package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.mode.ModeTargeting
import axion.client.tool.AxionToolSelectionController
import axion.common.model.BlockRegion
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CompositeOperation
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import axion.protocol.AxionInteractionOrigin

object SymmetryBreakController {
    private val dispatcher = SymmetryAwareOperationDispatcher()
    private val infiniteReachDispatcher = SymmetryAwareOperationDispatcher(
        interactionOrigin = AxionInteractionOrigin.INFINITE_REACH,
    )

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!AxionToolSelectionController.isCreativeModeAllowed()) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        return dispatchDerivedBreaks(
            client,
            target.hitResult.blockPos.toImmutable(),
            interactionOrigin = SymmetryBreakOriginPolicy.forPrimaryBreak(
                AxionClientState.globalModeState.infiniteReachEnabled,
            ),
        )
    }

    fun dispatchDerivedBreaks(
        client: MinecraftClient,
        primaryPos: BlockPos,
        interactionOrigin: AxionInteractionOrigin = AxionInteractionOrigin.NONE,
    ): Boolean {
        val config = ActiveSymmetryConfig.current() ?: return false
        if (!ActiveSymmetryConfig.hasDerivedTransforms(config)) {
            return false
        }

        val world = client.world ?: return false
        val derivedPositions = SymmetryTransformService.transformedBlocks(config, primaryPos)
            .asSequence()
            .filterNot { it == primaryPos }
            .distinct()
            .filter { pos -> !world.getBlockState(pos).isAir }
            .toList()
        if (derivedPositions.isEmpty()) {
            return false
        }

        val operation = if (derivedPositions.size == 1) {
            ClearRegionOperation(BlockRegion(derivedPositions.first(), derivedPositions.first()))
        } else {
            CompositeOperation(
                derivedPositions.map { pos ->
                    ClearRegionOperation(BlockRegion(pos, pos))
                },
            )
        }
        when (interactionOrigin) {
            AxionInteractionOrigin.NONE -> dispatcher
            AxionInteractionOrigin.INFINITE_REACH -> infiniteReachDispatcher
        }.dispatch(operation)
        return true
    }
}
