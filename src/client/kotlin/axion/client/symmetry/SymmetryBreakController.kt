package axion.client.symmetry

import axion.client.mode.ModeTargeting
import axion.client.tool.AxionToolSelectionController
import axion.common.model.BlockRegion
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CompositeOperation
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos

object SymmetryBreakController {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    fun handlePrimaryAction(client: MinecraftClient): Boolean {
        if (!AxionToolSelectionController.isCreativeModeAllowed()) {
            return false
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }

        val target = ModeTargeting.currentBlockTarget(client) ?: return false
        return dispatchDerivedBreaks(client, target.hitResult.blockPos.toImmutable())
    }

    fun dispatchDerivedBreaks(client: MinecraftClient, primaryPos: BlockPos): Boolean {
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
        dispatcher.dispatch(operation)
        return true
    }
}
