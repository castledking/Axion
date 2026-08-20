package axion.client.symmetry

import axion.client.selection.AxionTarget
import axion.client.selection.blockPosOrNull
import axion.common.model.SymmetryConfig

object SymmetryPreviewService {
    fun createPreview(config: SymmetryConfig, target: AxionTarget): SymmetryPreviewState? {
        val sourceBlock = target.blockPosOrNull()?.toImmutable() ?: return null
        val transformedBlocks = SymmetryTransformService.transformedBlocks(config, sourceBlock)
            .filterNot { it == sourceBlock }

        return SymmetryPreviewState(
            anchor = config.anchor,
            sourceBlock = sourceBlock,
            transformedBlocks = transformedBlocks,
        )
    }
}
