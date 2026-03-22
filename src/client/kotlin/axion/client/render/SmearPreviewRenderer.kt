package axion.client.render

import axion.client.tool.SmearToolController
import axion.client.tool.RegionRepeatPlacementService
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object SmearPreviewRenderer {
    private const val DESTINATION_COLOR: Int = 0xFF8CCBFF.toInt()
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: WorldRenderContext) {
        val preview = SmearToolController.currentPreview() ?: return
        RepeatPreviewRenderer.render(
            context = context,
            preview = preview,
            mode = RegionRepeatPlacementService.Mode.SMEAR,
            destinationColor = DESTINATION_COLOR,
            lineWidth = LINE_WIDTH,
        )
    }
}
