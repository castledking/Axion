package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.protocol.EntitySelectionMask
import axion.client.tool.directionGetFacing
import axion.client.compat.add
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
        entitySelection: EntitySelectionMask = clipboardBuffer.toEntitySelectionMask(),
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
            entitySelection = entitySelection,
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
            entitySelection = preview.entitySelection,
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

        return nudgePreview(client, initial, scrollAmount)
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
            entitySelection = preview.entitySelection,
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
            entitySelection = preview.entitySelection,
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
            entitySelection = preview.entitySelection,
        )
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return directionGetFacing(look)
    }

    private fun dominantMirrorAxis(client: MinecraftClient): PlacementMirrorAxis {
        val look = client.player?.rotationVecClient ?: return PlacementMirrorAxis.X
        val ax = kotlin.math.abs(look.x)
        val ay = kotlin.math.abs(look.y)
        val az = kotlin.math.abs(look.z)
        // Match the guide arrow logic: whichever axis has the largest component wins
        return when {
            ay >= ax && ay >= az -> PlacementMirrorAxis.Y  // Looking up/down
            ax >= ay && ax >= az -> PlacementMirrorAxis.X  // Facing E/W
            else -> PlacementMirrorAxis.Z  // Facing N/S
        }
    }
}
