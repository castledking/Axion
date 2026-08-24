package axion.client.symmetry

import axion.client.current.AxionTarget
import axion.client.current.blockPosOrNull
import axion.common.model.SymmetryConfig

object SymmetryPreviewService {
    fun createPreview(config: SymmetryConfig, target: AxionTarget): SymmetryPreviewState? {
        val sourceBlock = target.blockPosOrNull()?.immutable() ?: return null
        val transformedBlocks = SymmetryTransformService.transformedBlocks(config, sourceBlock)
            .filterNot { it == sourceBlock }

        return SymmetryPreviewState(
            anchor = config.anchor,
            sourceBlock = sourceBlock,
            transformedBlocks = transformedBlocks,
        )
    }
}
