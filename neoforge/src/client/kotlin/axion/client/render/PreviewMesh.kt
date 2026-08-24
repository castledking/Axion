package axion.client.render

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.core.Direction

data class PreviewMesh(
    val quads: List<PreviewQuad>,
)

data class PreviewQuad(
    val state: BlockState,
    val face: Direction,
    val bounds: AABB,
)
