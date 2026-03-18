package axion.client.render

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object SymmetryPreviewRenderer {
    fun render(context: WorldRenderContext) {
        // Symmetry keeps only the anchor gizmo visible; mirrored placement previews are intentionally hidden.
    }
}
