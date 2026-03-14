package axion.client.symmetry

import axion.common.model.SymmetryConfig
import axion.common.operation.ClearRegionOperation
import axion.common.operation.CloneRegionOperation
import axion.common.operation.CompositeOperation
import axion.common.operation.EditOperation
import axion.common.operation.SmearRegionOperation
import axion.common.operation.StackRegionOperation
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

        return when (operation) {
            is CloneRegionOperation -> expandClone(operation, resolvedConfig)
            is StackRegionOperation -> expandStack(operation, resolvedConfig)
            is SmearRegionOperation -> expandSmear(operation, resolvedConfig)
            is CompositeOperation -> operation.operations
                .flatMap { nested -> expand(nested, resolvedConfig) }
                .distinct()

            is ClearRegionOperation -> listOf(operation)
            else -> listOf(operation)
        }
    }

    private fun expandClone(operation: CloneRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                CloneRegionOperation(
                    sourceRegion = operation.sourceRegion,
                    destinationOrigin = SymmetryTransformService.transformBlock(
                        operation.destinationOrigin,
                        config.anchor.position,
                        transform,
                    ),
                )
            }
            .distinct()
    }

    private fun expandStack(operation: StackRegionOperation, config: SymmetryConfig): List<EditOperation> {
        return SymmetryTransformService.activeTransforms(config)
            .map { transform ->
                StackRegionOperation(
                    sourceRegion = transformRepeatSourceRegion(operation.sourceRegion, config, transform),
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
                    sourceRegion = transformRepeatSourceRegion(operation.sourceRegion, config, transform),
                    clipboardBuffer = operation.clipboardBuffer,
                    step = SymmetryTransformService.transformVector(operation.step, transform),
                    repeatCount = operation.repeatCount,
                )
            }
            .distinct()
    }

    private fun transformRepeatSourceRegion(
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
