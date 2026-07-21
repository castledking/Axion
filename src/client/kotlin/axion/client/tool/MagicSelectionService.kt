package axion.client.tool

import axion.client.config.AxionClientConfig
import axion.client.network.BlockEntitySnapshotService
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.ClipboardCell
import net.minecraft.block.BlockState
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import net.minecraft.world.World
import axion.client.compat.toImmutable
import axion.client.compat.add
import java.util.ArrayDeque

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

    fun select(world: World, center: BlockPos, radius: Int = brushRadius): Result? {
        val radiusSquared = radius * radius
        val seedState = world.getBlockState(center)
        if (seedState.isAir) {
            return null
        }

        val selectedPositions = collectConnectedSelection(
            world = world,
            center = center,
            radiusSquared = radiusSquared,
            seedState = seedState,
        )

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

    private fun collectConnectedSelection(
        world: World,
        center: BlockPos,
        radiusSquared: Int,
        seedState: BlockState,
    ): List<BlockPos> {
        val selected = linkedSetOf<BlockPos>()
        val visited = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        val centerPos = center.toImmutable()

        visited += centerPos
        queue += centerPos

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            val state = world.getBlockState(pos)
            if (pos != centerPos && !matchesFamily(seedState, state)) {
                continue
            }

            selected += pos

            Direction.entries.forEach { direction ->
                val next = pos.add(direction.vector).toImmutable()
                if (next in visited || !withinRadius(centerPos, next, radiusSquared)) {
                    return@forEach
                }
                visited += next
                queue += next
            }
        }

        return selected.toList()
    }

    private fun withinRadius(center: BlockPos, candidate: BlockPos, radiusSquared: Int): Boolean {
        val dx = candidate.x - center.x
        val dy = candidate.y - center.y
        val dz = candidate.z - center.z
        return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared
    }

    private fun matchesFamily(seedState: BlockState, candidateState: BlockState): Boolean {
        if (candidateState.isAir) {
            return false
        }
        val enabledTemplates = AxionClientConfig.enabledMagicSelectTemplates()
        if (enabledTemplates.isEmpty() || shouldUseSameBlockFallback(enabledTemplates, seedState)) {
            return seedState.block == candidateState.block
        }
        return enabledTemplates.any { template ->
            templateMatches(template, seedState, candidateState) ||
                template.selectedCustomMaskIds
                    .mapNotNull(AxionClientConfig::customMaskById)
                    .any { mask -> customMaskMatches(mask, seedState, candidateState) }
        }
    }

    private fun shouldUseSameBlockFallback(
        enabledTemplates: List<axion.client.config.MagicSelectTemplateConfig>,
        seedState: BlockState,
    ): Boolean {
        if (!AxionClientConfig.sameBlockMagicSelectEnabled()) {
            return false
        }
        return enabledTemplates.none { template ->
            templateIncludesSeed(template, seedState)
        }
    }

    private fun templateIncludesSeed(
        template: axion.client.config.MagicSelectTemplateConfig,
        seedState: BlockState,
    ): Boolean {
        val seedId = blockId(seedState)
        if (seedId in template.customBlockIds) {
            return true
        }
        if (template.rules().any { rule -> rule.includes(seedState) }) {
            return true
        }
        return template.selectedCustomMaskIds
            .mapNotNull(AxionClientConfig::customMaskById)
            .any { mask ->
                seedId !in mask.excludedBlockIds &&
                    (seedId in mask.customBlockIds || mask.rules().any { rule -> rule.includes(seedState) })
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
