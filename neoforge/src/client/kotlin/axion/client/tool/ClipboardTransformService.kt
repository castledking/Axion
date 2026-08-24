package axion.client.tool

import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import axion.common.model.StairMirrorPolicy
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.core.Vec3i

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
        state: net.minecraft.world.level.block.state.BlockState,
        transform: PlacementTransform,
    ): net.minecraft.world.level.block.state.BlockState {
        val mirroredState = when (transform.mirrorAxis) {
            PlacementMirrorAxis.NONE -> state
            PlacementMirrorAxis.X -> mirrored(state, Mirror.FRONT_BACK, Mirror.LEFT_RIGHT)
            PlacementMirrorAxis.Y ->
                mirrored(state, Mirror.FRONT_BACK, Mirror.LEFT_RIGHT).rotate(Rotation.CLOCKWISE_180)
            PlacementMirrorAxis.Z -> mirrored(state, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK)
        }

        return when (transform.normalizedRotationQuarterTurns) {
            0 -> mirroredState
            1 -> mirroredState.rotate(Rotation.CLOCKWISE_90)
            2 -> mirroredState.rotate(Rotation.CLOCKWISE_180)
            else -> mirroredState.rotate(Rotation.COUNTERCLOCKWISE_90)
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
        state: net.minecraft.world.level.block.state.BlockState,
        mirror: Mirror,
        perpendicular: Mirror,
    ): net.minecraft.world.level.block.state.BlockState {
        val mirroredState = state.mirror(mirror)
        if (!StairMirrorPolicy.needsHandednessFlip(
                isStairs = state.block is StairBlock,
                mirrorLeftStateUnchanged = mirroredState == state,
            )
        ) {
            return mirroredState
        }

        return state.mirror(perpendicular).rotate(Rotation.CLOCKWISE_180)
    }
}
