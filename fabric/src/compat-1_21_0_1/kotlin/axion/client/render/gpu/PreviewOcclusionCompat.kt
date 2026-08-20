package axion.client.render.gpu

import net.minecraft.block.BlockState

object PreviewOcclusionCompat {
    // The full-cube query changed shape around 1.21.2. The cached opacity
    // flag is the stable legacy equivalent and still excludes foliage,
    // glass, fluids, and other non-occluding preview blocks.
    fun isOpaqueFullCube(state: BlockState): Boolean = state.isOpaque
}
