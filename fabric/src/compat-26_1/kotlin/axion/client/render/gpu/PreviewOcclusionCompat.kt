package axion.client.render.gpu

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import java.util.concurrent.ConcurrentHashMap

object PreviewOcclusionCompat {
    private val fullCubeCache = ConcurrentHashMap<BlockState, Boolean>()

    /**
     * [BlockState.canOcclude] also returns true for partial occluders such as
     * stairs.  Treating those as full cubes drops otherwise visible preview
     * cells before the shape-aware block tessellator gets a chance to render
     * them.  Require the state's actual occlusion shape to cover the complete
     * unit cube as well.
     */
    fun isOpaqueFullCube(state: BlockState): Boolean = fullCubeCache.computeIfAbsent(state) {
        it.canOcclude() && !Shapes.joinIsNotEmpty(
            Shapes.block(),
            it.getOcclusionShape(),
            BooleanOp.ONLY_FIRST,
        )
    }
}
