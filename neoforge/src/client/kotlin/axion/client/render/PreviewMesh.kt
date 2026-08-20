package axion.client.render

import net.minecraft.block.BlockState
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

data class PreviewMesh(
    val quads: List<PreviewQuad>,
)

data class PreviewQuad(
    val state: BlockState,
    val face: Direction,
    val bounds: Box,
)
