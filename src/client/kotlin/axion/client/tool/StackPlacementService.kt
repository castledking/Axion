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
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

object StackPlacementService {
    fun toOperation(preview: StackPreviewState, client: MinecraftClient? = null): EditOperation {
        val blockOperation = RegionRepeatPlacementService.toOperation(preview, repeatMode())
        if (!AxionClientState.copyEntitiesEnabled) {
            return blockOperation
        }
        val sourceEntityUuids = client?.world?.let { entityUuidsInRegion(it, preview.sourceRegion) }.orEmpty()
        if (sourceEntityUuids.isEmpty()) {
            return blockOperation
        }

        val entityCloneOperations = buildList {
            preview.committedSegments.forEach { segment ->
                addAll(entityCloneOperationsFor(segment.sourceRegion, segment.step, segment.repeatCount, sourceEntityUuids))
            }
            addAll(entityCloneOperationsFor(preview.sourceRegion, preview.step, preview.repeatCount, sourceEntityUuids))
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
        entityUuids: List<UUID>,
    ): List<CloneEntitiesOperation> {
        val sourceOrigin = sourceRegion.minCorner()
        return (1..repeatCount).map { index ->
            CloneEntitiesOperation(
                entityUuids = entityUuids,
                sourceRegion = sourceRegion,
                destinationOrigin = sourceOrigin.add(step.multiply(index)).toImmutable(),
            )
        }
    }

    private fun entityUuidsInRegion(world: World, sourceRegion: BlockRegion): List<UUID> {
        val source = sourceRegion.normalized()
        val sourceMin = source.minCorner()
        val sourceMax = source.maxCorner()
        val queryBox = Box(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        return world.getEntitiesByClass(Entity::class.java, queryBox) { true }
            .mapNotNull { it.uuid }
            .toList()
    }
}
