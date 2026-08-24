package axion.client.symmetry

import axion.common.model.SymmetryAnchor
import net.minecraft.core.BlockPos

data class SymmetryPreviewState(
    val anchor: SymmetryAnchor,
    val sourceBlock: BlockPos,
    val transformedBlocks: List<BlockPos>,
)
