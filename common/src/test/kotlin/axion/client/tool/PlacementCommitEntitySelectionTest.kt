package axion.client.tool

import axion.client.AxionClientState
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.operation.CompositeOperation
import axion.common.operation.MoveEntitiesOperation
import axion.protocol.EntitySelectionMask
import axion.protocol.IntVector3
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlacementCommitEntitySelectionTest {
    @Test
    fun `move carries the sparse selected blob mask`() {
        val selectedCells = EntitySelectionMask.sparseOffsets(
            listOf(IntVector3(0, 0, 0), IntVector3(8, 0, 0)),
        )
        val origin = BlockPos(10, 20, 30)
        val region = BlockRegion(origin, origin.add(8, 0, 0))
        val clipboard = ClipboardBuffer(Vec3i(9, 1, 1), emptyList())
        val preview = ClonePreviewState(
            mode = PlacementToolMode.MOVE,
            firstCorner = origin,
            sourceRegion = region,
            sourceClipboardBuffer = clipboard,
            destinationClipboardBuffer = clipboard,
            anchor = origin,
            offset = Vec3i(0, 1, 0),
            destinationRegion = BlockRegion(origin.up(), origin.add(8, 1, 0)),
            entitySelection = selectedCells,
        )

        AxionClientState.updateCopyEntities(true)
        try {
            val operation = PlacementCommitService.toOperation(preview)
            val entityMove = when (operation) {
                is MoveEntitiesOperation -> operation
                is CompositeOperation -> operation.operations.filterIsInstance<MoveEntitiesOperation>().singleOrNull()
                else -> null
            }

            assertNotNull(entityMove)
            assertEquals(selectedCells, entityMove.entitySelection)
        } finally {
            AxionClientState.updateCopyEntities(false)
        }
    }
}
