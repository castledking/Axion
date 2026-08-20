package axion.client.symmetry

import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos

data class SymmetryPlacementResult(
    val hitResult: BlockHitResult,
    val primaryPlacement: Placement,
    val derivedPlacements: List<Placement>,
) {
    data class Placement(
        val pos: BlockPos,
        val state: BlockState,
    )
}
