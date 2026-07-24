package axion.client.render.gpu

import net.minecraft.world.level.block.state.BlockState

object PreviewOcclusionCompat {
    fun isOpaqueFullCube(state: BlockState): Boolean = state.canOcclude()
}
