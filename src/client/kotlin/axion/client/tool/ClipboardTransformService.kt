package axion.client.tool

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import axion.common.model.StairMirrorPolicy
import net.minecraft.block.StairsBlock
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
        val mirroredOffset = when (transform.mirrorAxis) {
            PlacementMirrorAxis.NONE -> offset
            PlacementMirrorAxis.X -> Vec3i(size.x - 1 - offset.x, offset.y, offset.z)
            PlacementMirrorAxis.Y -> Vec3i(offset.x, size.y - 1 - offset.y, offset.z)
            PlacementMirrorAxis.Z -> Vec3i(offset.x, offset.y, size.z - 1 - offset.z)
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
        val mirroredState = when (transform.mirrorAxis) {
            PlacementMirrorAxis.NONE -> state
            PlacementMirrorAxis.X -> mirrored(state, BlockMirror.FRONT_BACK, BlockMirror.LEFT_RIGHT)
            PlacementMirrorAxis.Y ->
                mirrored(state, BlockMirror.FRONT_BACK, BlockMirror.LEFT_RIGHT).rotate(BlockRotation.CLOCKWISE_180)
            PlacementMirrorAxis.Z -> mirrored(state, BlockMirror.LEFT_RIGHT, BlockMirror.FRONT_BACK)
        }

        return when (transform.normalizedRotationQuarterTurns) {
            0 -> mirroredState
            1 -> mirroredState.rotate(BlockRotation.CLOCKWISE_90)
            2 -> mirroredState.rotate(BlockRotation.CLOCKWISE_180)
            else -> mirroredState.rotate(BlockRotation.COUNTERCLOCKWISE_90)
        }
    }

    /**
     * Mirrors a state, finishing the corner-stair case vanilla declines to handle.
     *
     * See [StairMirrorPolicy]. When vanilla hands the state straight back, the
     * stair faces across the mirrored axis: its facing is already right, but the
     * corner handedness still has to flip. Mirroring along the *perpendicular*
     * axis is the case vanilla does handle, so it flips the handedness for us —
     * at the cost of a 180 degree facing rotation, which the second rotate undoes.
     *
     * Straight stairs pass through this unchanged, so it needs no shape check.
     */
    private fun mirrored(
        state: net.minecraft.block.BlockState,
        mirror: BlockMirror,
        perpendicular: BlockMirror,
    ): net.minecraft.block.BlockState {
        val mirroredState = state.mirror(mirror)
        if (!StairMirrorPolicy.needsHandednessFlip(
                isStairs = state.block is StairsBlock,
                mirrorLeftStateUnchanged = mirroredState == state,
            )
        ) {
            return mirroredState
        }

        return state.mirror(perpendicular).rotate(BlockRotation.CLOCKWISE_180)
    }
}
