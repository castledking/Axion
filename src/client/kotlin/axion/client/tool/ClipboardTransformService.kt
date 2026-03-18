package axion.client.tool

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.Vec3i

object ClipboardTransformService {
    fun transform(buffer: ClipboardBuffer, transform: PlacementTransform): ClipboardBuffer {
        if (transform.isIdentity()) {
            return buffer
        }

        return ClipboardBuffer(
            size = transformedSize(buffer.size, transform),
            cells = buffer.cells.map { cell ->
                ClipboardCell(
                    offset = transformedOffset(buffer.size, cell.offset, transform),
                    state = transformState(cell.state, transform),
                    blockEntityData = cell.blockEntityData?.copy(),
                )
            },
        )
    }

    fun transformedSize(size: Vec3i, transform: PlacementTransform): Vec3i {
        return when (transform.normalizedRotationQuarterTurns) {
            0, 2 -> Vec3i(size.x, size.y, size.z)
            else -> Vec3i(size.z, size.y, size.x)
        }
    }

    fun transformedOffset(size: Vec3i, offset: Vec3i, transform: PlacementTransform): Vec3i {
        val mirroredOffset = if (transform.mirrored) {
            Vec3i(size.x - 1 - offset.x, offset.y, offset.z)
        } else {
            offset
        }

        return when (transform.normalizedRotationQuarterTurns) {
            0 -> mirroredOffset
            1 -> Vec3i(size.z - 1 - mirroredOffset.z, mirroredOffset.y, mirroredOffset.x)
            2 -> Vec3i(size.x - 1 - mirroredOffset.x, mirroredOffset.y, size.z - 1 - mirroredOffset.z)
            else -> Vec3i(mirroredOffset.z, mirroredOffset.y, size.x - 1 - mirroredOffset.x)
        }
    }

    private fun transformState(
        state: net.minecraft.block.BlockState,
        transform: PlacementTransform,
    ): net.minecraft.block.BlockState {
        val mirroredState = if (transform.mirrored) {
            state.mirror(BlockMirror.FRONT_BACK)
        } else {
            state
        }

        return when (transform.normalizedRotationQuarterTurns) {
            0 -> mirroredState
            1 -> mirroredState.rotate(BlockRotation.CLOCKWISE_90)
            2 -> mirroredState.rotate(BlockRotation.CLOCKWISE_180)
            else -> mirroredState.rotate(BlockRotation.COUNTERCLOCKWISE_90)
        }
    }
}
