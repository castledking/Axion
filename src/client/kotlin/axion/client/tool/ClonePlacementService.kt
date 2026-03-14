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
    ): ClonePreviewState {
        val normalized = sourceRegion.normalized()
        val anchor = normalized.minCorner()
        return ClonePreviewState(
            mode = mode,
            firstCorner = firstCorner,
            sourceRegion = normalized,
            clipboardBuffer = clipboardBuffer,
            anchor = anchor,
            offset = offset,
            destinationRegion = normalized.offset(offset).normalized(),
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
            clipboardBuffer = preview.clipboardBuffer,
            offset = preview.offset.add(delta),
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
            clipboardBuffer = preview.clipboardBuffer,
            offset = offset,
        )
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return Direction.getFacing(look)
    }
}
