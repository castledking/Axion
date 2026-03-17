package axion.client.render

import axion.client.tool.SmearToolController
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object SmearPreviewRenderer {
    private const val DESTINATION_COLOR: Int = 0xFF8CCBFF.toInt()
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: WorldRenderContext) {
        val preview = SmearToolController.currentPreview() ?: return
        val source = preview.sourceRegion.normalized()
        val destinationRegions = (1..preview.repeatCount).map { index ->
            source.offset(preview.step.multiply(index)).normalized()
        }
            .distinct()

        if (destinationRegions.isEmpty()) {
            return
        }

        RepeatPreviewRenderer.render(
            context = context,
            preview = preview.copy(
                repeatCount = destinationRegions.size,
                destinationRegions = destinationRegions,
            ),
            destinationColor = DESTINATION_COLOR,
            lineWidth = LINE_WIDTH,
        )
    }
}
