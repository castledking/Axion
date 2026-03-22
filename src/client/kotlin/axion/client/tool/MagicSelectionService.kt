package axion.client.tool

import axion.client.config.AxionClientConfig
import axion.client.network.BlockEntitySnapshotService
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockState
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World

object MagicSelectionService {
    private const val DEFAULT_RADIUS: Int = 8
    private const val MIN_RADIUS: Int = 1
    private const val MAX_RADIUS: Int = 16
    private var brushRadius: Int = DEFAULT_RADIUS

    data class Result(
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer,
    )

    fun defaultBrushSize(): Int = brushRadius

    fun adjustBrushSize(scrollAmount: Double): Int? {
        val direction = scrollAmount.compareTo(0.0)
        if (direction == 0) {
            return null
        }
        val nextRadius = (brushRadius + direction).coerceIn(MIN_RADIUS, MAX_RADIUS)
        if (nextRadius == brushRadius) {
            return null
        }
        brushRadius = nextRadius
        return brushRadius
    }

    fun select(world: World, center: BlockPos): Result? {
        val radius = brushRadius
        val radiusSquared = radius * radius
        val seedState = world.getBlockState(center)
        if (seedState.isAir) {
            return null
        }

        val selectedPositions = buildList {
            for (x in center.x - radius..center.x + radius) {
                for (y in center.y - radius..center.y + radius) {
                    for (z in center.z - radius..center.z + radius) {
                        val dx = x - center.x
                        val dy = y - center.y
                        val dz = z - center.z
                        if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSquared) {
                            continue
                        }

                        val pos = BlockPos(x, y, z)
                        if (pos == center) {
                            add(pos.toImmutable())
                            continue
                        }
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
        val enabledTemplates = AxionClientConfig.enabledMagicSelectTemplates()
        if (enabledTemplates.isEmpty()) {
            return seedState.block == candidateState.block
        }
        return enabledTemplates.any { template ->
            templateMatches(template, seedState, candidateState) ||
                template.selectedCustomMaskIds
                    .mapNotNull(AxionClientConfig::customMaskById)
                    .any { mask -> customMaskMatches(mask, seedState, candidateState) }
        }
    }

    private fun templateMatches(
        template: axion.client.config.MagicSelectTemplateConfig,
        seedState: BlockState,
        candidateState: BlockState,
    ): Boolean {
        return groupMatches(
            ruleMatcher = { state -> template.rules().any { rule -> rule.includes(state) } },
            customBlockIds = template.customBlockIds,
            excludedBlockIds = emptySet(),
            seedState = seedState,
            candidateState = candidateState,
        )
    }

    private fun customMaskMatches(
        mask: axion.client.config.MagicSelectCustomMask,
        seedState: BlockState,
        candidateState: BlockState,
    ): Boolean {
        return groupMatches(
            ruleMatcher = { state -> mask.rules().any { rule -> rule.includes(state) } },
            customBlockIds = mask.customBlockIds,
            excludedBlockIds = mask.excludedBlockIds,
            seedState = seedState,
            candidateState = candidateState,
        )
    }

    private fun groupMatches(
        ruleMatcher: (BlockState) -> Boolean,
        customBlockIds: Set<String>,
        excludedBlockIds: Set<String>,
        seedState: BlockState,
        candidateState: BlockState,
    ): Boolean {
        return stateMatchesGroup(seedState, ruleMatcher, customBlockIds, excludedBlockIds) &&
            stateMatchesGroup(candidateState, ruleMatcher, customBlockIds, excludedBlockIds)
    }

    private fun stateMatchesGroup(
        state: BlockState,
        ruleMatcher: (BlockState) -> Boolean,
        customBlockIds: Set<String>,
        excludedBlockIds: Set<String>,
    ): Boolean {
        val blockId = blockId(state)
        if (blockId in excludedBlockIds) {
            return false
        }
        return ruleMatcher(state) || blockId in customBlockIds
    }

    private fun matchesCustomBlocks(
        customBlockIds: Set<String>,
        seedState: BlockState,
        candidateState: BlockState,
    ): Boolean {
        if (customBlockIds.isEmpty()) {
            return false
        }
        val seedId = blockId(seedState)
        val candidateId = blockId(candidateState)
        return seedId in customBlockIds && candidateId in customBlockIds
    }

    private fun blockId(state: BlockState): String = Registries.BLOCK.getId(state.block).toString()
}
