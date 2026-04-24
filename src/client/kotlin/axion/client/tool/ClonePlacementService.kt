package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i

object ClonePlacementService {
    fun createPreview(
        mode: PlacementToolMode,
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        offset: Vec3i,
        transform: PlacementTransform = PlacementTransform(),
        entityUuids: List<java.util.UUID> = emptyList(),
    ): ClonePreviewState {
        val normalized = sourceRegion.normalized()
        val anchor = normalized.minCorner()
        val destinationClipboardBuffer = ClipboardTransformService.transform(clipboardBuffer, transform)
        return ClonePreviewState(
            mode = mode,
            firstCorner = firstCorner,
            sourceRegion = normalized,
            sourceClipboardBuffer = clipboardBuffer,
            destinationClipboardBuffer = destinationClipboardBuffer,
            anchor = anchor,
            offset = offset,
            destinationRegion = BlockRegion(
                anchor.add(offset),
                anchor.add(offset).add(destinationClipboardBuffer.size).add(-1, -1, -1),
            ).normalized(),
            transform = transform,
            entityUuids = entityUuids,
        )
    }

    fun nudgePreview(
        client: MinecraftClient,
        preview: ClonePreviewState,
        scrollAmount: Double,
    ): ClonePreviewState {
        val direction = dominantLookDirection(client)
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return preview
        }

        val delta = direction.vector.multiply(scrollDirection)
        return createPreview(
            mode = preview.mode,
            firstCorner = preview.firstCorner,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.sourceClipboardBuffer,
            offset = preview.offset.add(delta),
            transform = preview.transform,
            entityUuids = preview.entityUuids,
        )
    }

    fun initialPreview(
        client: MinecraftClient,
        mode: PlacementToolMode,
        firstCorner: BlockPos,
        sourceRegion: BlockRegion,
        clipboardBuffer: ClipboardBuffer,
        scrollAmount: Double,
    ): ClonePreviewState {
        val initial = createPreview(
            mode = mode,
            firstCorner = firstCorner,
            sourceRegion = sourceRegion,
            clipboardBuffer = clipboardBuffer,
            offset = Vec3i.ZERO,
        )

        val nudged = nudgePreview(client, initial, scrollAmount)
        // Capture entity UUIDs from the source region on first scroll
        val world = client.world ?: return nudged
        val sourceMin = sourceRegion.minCorner()
        val sourceMax = sourceRegion.maxCorner()
        val queryBox = net.minecraft.util.math.Box(
            sourceMin.x.toDouble(),
            sourceMin.y.toDouble(),
            sourceMin.z.toDouble(),
            sourceMax.x + 1.0,
            sourceMax.y + 1.25,
            sourceMax.z + 1.0,
        )
        val entityUuids = world.getEntitiesByClass(net.minecraft.entity.Entity::class.java, queryBox) { true }
            .mapNotNull { it.uuid }
            .toList()
        return nudged.copy(entityUuids = entityUuids)
    }

    fun reanchorPreview(preview: ClonePreviewState, destinationOrigin: BlockPos): ClonePreviewState {
        val sourceMin = preview.sourceRegion.minCorner()
        val offset = Vec3i(
            destinationOrigin.x - sourceMin.x,
            destinationOrigin.y - sourceMin.y,
            destinationOrigin.z - sourceMin.z,
        )

        return createPreview(
            mode = preview.mode,
            firstCorner = preview.firstCorner,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.sourceClipboardBuffer,
            offset = offset,
            transform = preview.transform,
            entityUuids = preview.entityUuids,
        )
    }

    fun rotatePreview(preview: ClonePreviewState): ClonePreviewState {
        return createPreview(
            mode = preview.mode,
            firstCorner = preview.firstCorner,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.sourceClipboardBuffer,
            offset = preview.offset,
            transform = preview.transform.rotateClockwise(),
            entityUuids = preview.entityUuids,
        )
    }

    fun mirrorPreview(preview: ClonePreviewState, client: MinecraftClient): ClonePreviewState {
        val axis = dominantMirrorAxis(client)
        return createPreview(
            mode = preview.mode,
            firstCorner = preview.firstCorner,
            sourceRegion = preview.sourceRegion,
            clipboardBuffer = preview.sourceClipboardBuffer,
            offset = preview.offset,
            transform = preview.transform.toggleMirror(axis),
            entityUuids = preview.entityUuids,
        )
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return Direction.getFacing(look)
    }

    private fun dominantMirrorAxis(client: MinecraftClient): PlacementMirrorAxis {
        val look = client.player?.rotationVecClient ?: return PlacementMirrorAxis.X
        val ax = kotlin.math.abs(look.x)
        val ay = kotlin.math.abs(look.y)
        val az = kotlin.math.abs(look.z)
        return when {
            ay >= ax && ay >= az -> PlacementMirrorAxis.Y
            ax >= az -> PlacementMirrorAxis.X
            else -> PlacementMirrorAxis.Z
        }
    }
}
