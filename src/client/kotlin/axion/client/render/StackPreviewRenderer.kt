package axion.client.render

import axion.client.tool.StackPlacementService
import axion.client.tool.StackToolController
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object StackPreviewRenderer {
    private const val DESTINATION_COLOR: Int = 0xFFFF9F5A.toInt()
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: WorldRenderContext) {
        val preview = StackToolController.currentPreview() ?: return
        RepeatPreviewRenderer.render(
            context = context,
            preview = preview,
            mode = StackPlacementService.repeatMode(),
            destinationColor = DESTINATION_COLOR,
            lineWidth = LINE_WIDTH,
        )
    }
}
