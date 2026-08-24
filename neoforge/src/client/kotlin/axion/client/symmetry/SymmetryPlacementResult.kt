package axion.client.symmetry

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos

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
