package axion.client.tool

import axion.client.AxionClientState
import axion.client.compat.add
import axion.client.symmetry.SymmetryAwareOperationDispatcher
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.DeleteEntitiesOperation
import axion.common.operation.EditOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.block.Blocks

/**
 * Shared "clear this region" dispatch.
 *
 * The delete key erases from every tool that has a second corner set -- not
 * just the erase tool -- so the operation build-up lives here instead of in
 * [EraseToolController].
 */
object RegionEraseService {
    private val dispatcher = SymmetryAwareOperationDispatcher()

    /** A region a tool is holding, plus the sparse cells to limit the erase to. */
    data class Target(
        val region: BlockRegion,
        val clipboard: ClipboardBuffer?,
    )

    /**
     * @param clipboard when present, only the cells it captured are cleared, so
     * a sparse (magic) selection does not flatten the air gaps around it.
     */
    fun erase(region: BlockRegion, clipboard: ClipboardBuffer? = null) {
        dispatcher.dispatch(withEntities(region, blockOperation(region, clipboard)))
    }

    private fun blockOperation(region: BlockRegion, clipboard: ClipboardBuffer?): EditOperation {
        if (clipboard == null) {
            return ClearRegionOperation(region)
        }
        return SymmetryPlacementOperation(
            clipboard.cells.map { cell ->
                SymmetryBlockPlacement(
                    pos = region.minCorner().add(cell.offset),
                    state = Blocks.AIR.defaultState,
                    blockEntityData = null,
                )
            },
        )
    }

    private fun withEntities(region: BlockRegion, blockOperation: EditOperation): EditOperation {
        if (!AxionClientState.copyEntitiesEnabled) {
            return blockOperation
        }

        return CompositeOperation(
            listOf(
                blockOperation,
                DeleteEntitiesOperation(region),
            ),
        )
    }
}
