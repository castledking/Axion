package axion.client.render.gpu

import net.minecraft.block.BlockState

object PreviewOcclusionCompat {
    fun isOpaqueFullCube(state: BlockState): Boolean = state.isOpaqueFullCube
}
