package axion.client.symmetry

import axion.common.model.SymmetryConfig
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.FilteredCloneRegionOperation
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
import axion.common.operation.SymmetryPlacementOperation
import axion.common.operation.SymmetryBlockPlacement
import axion.common.model.BlockRegion

object SymmetryOperationExpander {
    fun expand(operation: EditOperation): List<EditOperation> {
        return expand(operation, ActiveSymmetryConfig.current())
    }

    fun expand(operation: EditOperation, config: SymmetryConfig?): List<EditOperation> {
        if (!ActiveSymmetryConfig.hasDerivedTransforms(config)) {
            return listOf(operation)
        }

        val resolvedConfig = config ?: return listOf(operation)
        val constructMode = resolvedConfig.constructEnabled

        return when (operation) {
            is CloneRegionOperation -> if (constructMode) expandCloneConstruct(operation, resolvedConfig) else listOf(operation)
            is FilteredCloneRegionOperation -> if (constructMode) expandFilteredCloneConstruct(operation, resolvedConfig) else listOf(operation)
            is ClearRegionOperation -> if (constructMode) expandClear(operation, resolvedConfig) else listOf(operation)
            is SymmetryPlacementOperation -> if (constructMode) expandSymmetryPlacement(operation, resolvedConfig) else listOf(operation)
            is StackRegionOperation -> expandStack(operation, resolvedConfig)
            is SmearRegionOperation -> expandSmear(operation, resolvedConfig)
            is CompositeOperation -> {
                val expanded = operation.operations.flatMap { nested -> expand(nested, resolvedConfig) }
                if (expanded.size == 1) expanded else listOf(CompositeOperation(expanded.distinct()))
            }

            else -> listOf(operation)
        }
    }

    /**
     * Construct mode clone: transform BOTH source region and destination origin.
     * Each symmetry transform reads from the transformed source and writes to the
     * transformed destination, preserving the relative offset in mirrored/rotated space.
     */
    private fun expandCloneConstruct(operation: CloneRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                val transformedSource = transformRegion(operation.sourceRegion, config, transform)
                CloneRegionOperation(
                    sourceRegion = transformedSource,
                    destinationOrigin = SymmetryTransformService.transformBlock(
                        operation.destinationOrigin,
                        config.anchor.position,
                        transform,
                    ),
                )
            }
            .distinct()
    }

    private fun expandFilteredCloneConstruct(operation: FilteredCloneRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                val transformedSource = transformRegion(operation.sourceRegion, config, transform)
                FilteredCloneRegionOperation(
                    sourceRegion = transformedSource,
                    destinationOrigin = SymmetryTransformService.transformBlock(
                        operation.destinationOrigin,
                        config.anchor.position,
                        transform,
                    ),
                    copyAir = operation.copyAir,
                    keepExisting = operation.keepExisting,
                )
            }
            .distinct()
    }

    /**
     * Construct mode erase: transform the erase region to all symmetry positions.
     */
    private fun expandClear(operation: ClearRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                ClearRegionOperation(
                    region = transformRegion(operation.region, config, transform),
                )
            }
            .distinct()
    }

    /**
     * Construct mode for SymmetryPlacementOperation (used by complex Move operations).
     * Transforms every individual block placement position through all symmetry transforms.
     */
    private fun expandSymmetryPlacement(operation: SymmetryPlacementOperation, config: SymmetryConfig): List<EditOperation> {
        val transforms = SymmetryTransformService.activeTransforms(config)
        val allPlacements = linkedMapOf<net.minecraft.util.math.BlockPos, SymmetryBlockPlacement>()
        transforms.forEach { transform ->
            operation.placements.forEach { placement ->
                val transformedPos = SymmetryTransformService.transformBlock(
                    placement.pos,
                    config.anchor.position,
                    transform,
                )
                allPlacements[transformedPos] = SymmetryBlockPlacement(
                    pos = transformedPos,
                    state = placement.state,
                    blockEntityData = placement.blockEntityData?.copy(),
                )
            }
        }
        return listOf(SymmetryPlacementOperation(allPlacements.values.toList()))
    }

    private fun expandStack(operation: StackRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                StackRegionOperation(
                    sourceRegion = transformRegion(operation.sourceRegion, config, transform),
                    clipboardBuffer = operation.clipboardBuffer,
                    step = SymmetryTransformService.transformVector(operation.step, transform),
                    repeatCount = operation.repeatCount,
                )
            }
            .distinct()
    }

    private fun expandSmear(operation: SmearRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                SmearRegionOperation(
                    sourceRegion = transformRegion(operation.sourceRegion, config, transform),
                    clipboardBuffer = operation.clipboardBuffer,
                    step = SymmetryTransformService.transformVector(operation.step, transform),
                    repeatCount = operation.repeatCount,
                )
            }
            .distinct()
    }

    private fun transformRegion(
        sourceRegion: BlockRegion,
        config: SymmetryConfig,
        transform: SymmetryTransformSpec,
    ): BlockRegion {
        val normalized = sourceRegion.normalized()
        val transformedMin = SymmetryTransformService.transformBlock(
            normalized.minCorner(),
            config.anchor.position,
            transform,
        )
        return normalized.offset(transformedMin.subtract(normalized.minCorner())).normalized()
    }
}
