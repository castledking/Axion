package axion.client.tool

import axion.client.AxionClientState
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.protocol.EntitySelectionMask
import axion.protocol.IntVector3
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionRepeatPlacementServiceTest {
    @Test
    fun `smear offsets follow rounded 3d line to node`() {
        val offsets = RegionRepeatPlacementService.smearOffsets(Vec3i(3, 3, 0))

        assertEquals(
            listOf(
                Vec3i(1, 1, 0),
                Vec3i(2, 2, 0),
                Vec3i(3, 3, 0),
            ),
            offsets,
        )
    }

    @Test
    fun `smear offsets preserve shallow diagonals`() {
        val offsets = RegionRepeatPlacementService.smearOffsets(Vec3i(4, 2, 0))

        assertEquals(
            listOf(
                Vec3i(1, 1, 0),
                Vec3i(2, 1, 0),
                Vec3i(3, 2, 0),
                Vec3i(4, 2, 0),
            ),
            offsets,
        )
    }

    @Test
    fun `stack entity operations retain each redirected segment mask`() {
        val clipboard = ClipboardBuffer(
            size = Vec3i(3, 1, 1),
            cells = emptyList(),
        )
        val committedMask = EntitySelectionMask.sparseOffsets(listOf(IntVector3(0, 0, 0)))
        val currentMask = EntitySelectionMask.sparseOffsets(
            listOf(IntVector3(0, 0, 0), IntVector3(2, 0, 0)),
        )
        val region = BlockRegion(BlockPos(0, 0, 0), BlockPos(2, 0, 0))
        val preview = RepeatRegionPreview(
            firstCorner = BlockPos.ORIGIN,
            sourceRegion = region,
            clipboardBuffer = clipboard,
            entitySelection = currentMask,
            lookDirection = Direction.SOUTH,
            step = Vec3i(0, 0, 1),
            scrollSign = 1,
            repeatCount = 1,
            committedSegments = listOf(
                RepeatPreviewSegment(
                    sourceRegion = region,
                    clipboardBuffer = clipboard,
                    entitySelection = committedMask,
                    lookDirection = Direction.EAST,
                    step = Vec3i(3, 0, 0),
                    scrollSign = 1,
                    repeatCount = 1,
                ),
            ),
        )

        AxionClientState.updateCopyEntities(true)
        try {
            val cloneMasks = flatten(StackPlacementService.toOperation(preview))
                .filterIsInstance<CloneEntitiesOperation>()
                .map { it.entitySelection }

            assertEquals(listOf(committedMask, currentMask), cloneMasks)
        } finally {
            AxionClientState.updateCopyEntities(false)
        }
    }

    private fun flatten(operation: EditOperation): List<EditOperation> = when (operation) {
        is CompositeOperation -> operation.operations.flatMap(::flatten)
        else -> listOf(operation)
    }
}
