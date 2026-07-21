package axion.client.tool

import axion.client.AxionClientState
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import axion.client.compat.add
import axion.client.compat.toImmutable
import axion.protocol.EntitySelectionMask

object StackPlacementService {
    fun toOperation(preview: StackPreviewState): EditOperation {
        val blockOperation = RegionRepeatPlacementService.toOperation(
            preview = preview,
            mode = RegionRepeatPlacementService.Mode.STACK,
            keepExisting = AxionClientState.keepExistingEnabled,
        )
        if (!AxionClientState.copyEntitiesEnabled) {
            return blockOperation
        }
        val entityCloneOperations = buildList {
            preview.committedSegments.forEach { segment ->
                addAll(
                    entityCloneOperationsFor(
                        sourceRegion = segment.sourceRegion,
                        step = segment.step,
                        repeatCount = segment.repeatCount,
                        entitySelection = segment.entitySelection,
                    ),
                )
            }
            addAll(
                entityCloneOperationsFor(
                    sourceRegion = preview.sourceRegion,
                    step = preview.step,
                    repeatCount = preview.repeatCount,
                    entitySelection = preview.entitySelection,
                ),
            )
        }

        return when {
            entityCloneOperations.isEmpty() -> blockOperation
            else -> CompositeOperation(listOf(blockOperation) + entityCloneOperations)
        }
    }

    fun createInitialPreview(
        client: MinecraftClient,
        firstCorner: net.minecraft.util.math.BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
    ): StackPreviewState? {
        return RegionRepeatPlacementService.createInitialPreview(
            client = client,
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            scrollAmount = scrollAmount,
            mode = RegionRepeatPlacementService.Mode.STACK,
        )
    }

    fun nudgePreview(client: MinecraftClient, preview: StackPreviewState, scrollAmount: Double): StackPreviewState? {
        return RegionRepeatPlacementService.nudgePreview(
            client = client,
            preview = preview,
            scrollAmount = scrollAmount,
            mode = RegionRepeatPlacementService.Mode.STACK,
        )
    }

    private fun entityCloneOperationsFor(
        sourceRegion: BlockRegion,
        step: net.minecraft.util.math.Vec3i,
        repeatCount: Int,
        entitySelection: EntitySelectionMask,
    ): List<CloneEntitiesOperation> {
        if (entitySelection.isEmpty) {
            return emptyList()
        }
        val sourceOrigin = sourceRegion.minCorner()
        return (1..repeatCount).map { index ->
            CloneEntitiesOperation(
                entitySelection = entitySelection,
                sourceRegion = sourceRegion,
                destinationOrigin = sourceOrigin.add(step.multiply(index)).toImmutable(),
            )
        }
    }
}
