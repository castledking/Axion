package axion.client.tool

import axion.client.AxionClientState
import axion.common.operation.CloneEntitiesOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient

object StackPlacementService {
    fun toOperation(preview: StackPreviewState): EditOperation {
        val blockOperation = RegionRepeatPlacementService.toOperation(preview, repeatMode())
        if (!AxionClientState.copyEntitiesEnabled) {
            return blockOperation
        }

        val entityCloneOperations = buildList {
            preview.committedSegments.forEach { segment ->
                addAll(entityCloneOperationsFor(segment.sourceRegion, segment.step, segment.repeatCount))
            }
            addAll(entityCloneOperationsFor(preview.sourceRegion, preview.step, preview.repeatCount))
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
            mode = repeatMode(),
        )
    }

    fun nudgePreview(client: MinecraftClient, preview: StackPreviewState, scrollAmount: Double): StackPreviewState? {
        return RegionRepeatPlacementService.nudgePreview(
            client = client,
            preview = preview,
            scrollAmount = scrollAmount,
            mode = repeatMode(),
        )
    }

    fun repeatMode(): RegionRepeatPlacementService.Mode {
        return if (AxionClientState.keepExistingEnabled) {
            RegionRepeatPlacementService.Mode.SMEAR
        } else {
            RegionRepeatPlacementService.Mode.STACK
        }
    }

    private fun entityCloneOperationsFor(
        sourceRegion: BlockRegion,
        step: net.minecraft.util.math.Vec3i,
        repeatCount: Int,
    ): List<CloneEntitiesOperation> {
        val sourceOrigin = sourceRegion.minCorner()
        return (1..repeatCount).map { index ->
            CloneEntitiesOperation(
                sourceRegion = sourceRegion,
                destinationOrigin = sourceOrigin.add(step.multiply(index)).toImmutable(),
            )
        }
    }
}
