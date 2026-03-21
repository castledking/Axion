package axion.client.tool

import axion.client.network.BlockEntitySnapshotService
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockState
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World

object MagicSelectionService {
    private const val DEFAULT_RADIUS: Int = 4
    private const val DEFAULT_RADIUS_SQUARED: Int = DEFAULT_RADIUS * DEFAULT_RADIUS

    data class Result(
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer,
    )

    fun select(world: World, center: BlockPos): Result? {
        val seedState = world.getBlockState(center)
        if (seedState.isAir) {
            return null
        }

        val selectedPositions = buildList {
            for (x in center.x - DEFAULT_RADIUS..center.x + DEFAULT_RADIUS) {
                for (y in center.y - DEFAULT_RADIUS..center.y + DEFAULT_RADIUS) {
                    for (z in center.z - DEFAULT_RADIUS..center.z + DEFAULT_RADIUS) {
                        val dx = x - center.x
                        val dy = y - center.y
                        val dz = z - center.z
                        if ((dx * dx) + (dy * dy) + (dz * dz) > DEFAULT_RADIUS_SQUARED) {
                            continue
                        }

                        val pos = BlockPos(x, y, z)
                        val state = world.getBlockState(pos)
                        if (matchesFamily(seedState, state)) {
                            add(pos.toImmutable())
                        }
                    }
                }
            }
        }

        if (selectedPositions.isEmpty()) {
            return null
        }

        val minX = selectedPositions.minOf { it.x }
        val minY = selectedPositions.minOf { it.y }
        val minZ = selectedPositions.minOf { it.z }
        val maxX = selectedPositions.maxOf { it.x }
        val maxY = selectedPositions.maxOf { it.y }
        val maxZ = selectedPositions.maxOf { it.z }
        val min = BlockPos(minX, minY, minZ)
        val max = BlockPos(maxX, maxY, maxZ)
        val cells = selectedPositions.map { pos ->
            ClipboardCell(
                offset = Vec3i(pos.x - minX, pos.y - minY, pos.z - minZ),
                state = world.getBlockState(pos),
                blockEntityData = BlockEntitySnapshotService.capture(world, pos),
            )
        }

        return Result(
            region = BlockRegion(min, max).normalized(),
            clipboardBuffer = ClipboardBuffer(
                size = Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1),
                cells = cells,
            ),
        )
    }

    fun merge(
        existingRegion: BlockRegion,
        existingClipboard: ClipboardBuffer,
        addition: Result,
    ): Result {
        val merged = linkedMapOf<BlockPos, ClipboardCell>()
        addAbsoluteCells(merged, existingRegion.minCorner(), existingClipboard)
        addAbsoluteCells(merged, addition.region.minCorner(), addition.clipboardBuffer)

        val positions = merged.keys.toList()
        val minX = positions.minOf { it.x }
        val minY = positions.minOf { it.y }
        val minZ = positions.minOf { it.z }
        val maxX = positions.maxOf { it.x }
        val maxY = positions.maxOf { it.y }
        val maxZ = positions.maxOf { it.z }
        val min = BlockPos(minX, minY, minZ)
        val max = BlockPos(maxX, maxY, maxZ)

        return Result(
            region = BlockRegion(min, max).normalized(),
            clipboardBuffer = ClipboardBuffer(
                size = Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1),
                cells = merged.entries.map { (pos, cell) ->
                    cell.copy(offset = Vec3i(pos.x - minX, pos.y - minY, pos.z - minZ))
                },
            ),
        )
    }

    private fun addAbsoluteCells(
        destination: MutableMap<BlockPos, ClipboardCell>,
        origin: BlockPos,
        clipboard: ClipboardBuffer,
    ) {
        clipboard.cells.forEach { cell ->
            destination[origin.add(cell.offset).toImmutable()] = cell.copy()
        }
    }

    private fun matchesFamily(seedState: BlockState, candidateState: BlockState): Boolean {
        if (candidateState.isAir) {
            return false
        }
        if (candidateState.isOf(seedState.block)) {
            return true
        }
        if (seedState.isIn(BlockTags.DIRT) && candidateState.isIn(BlockTags.DIRT)) {
            return true
        }
        if (seedState.isIn(BlockTags.BASE_STONE_OVERWORLD) && candidateState.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            return true
        }
        return false
    }
}
