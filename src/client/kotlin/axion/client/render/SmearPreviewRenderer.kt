package axion.client.render

import axion.client.symmetry.SymmetryOperationExpander
import axion.client.tool.SmearToolController
import axion.common.operation.SmearRegionOperation
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object SmearPreviewRenderer {
    private const val DESTINATION_COLOR: Int = 0xFF8CCBFF.toInt()
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: WorldRenderContext) {
        val preview = SmearToolController.currentPreview() ?: return
        val destinationRegions = SymmetryOperationExpander.expand(
            SmearRegionOperation(
                sourceRegion = preview.sourceRegion,
                clipboardBuffer = preview.clipboardBuffer,
                step = preview.step,
                repeatCount = preview.repeatCount,
            ),
        ).filterIsInstance<SmearRegionOperation>()
            .flatMap { operation ->
                val source = operation.sourceRegion.normalized()
                (1..operation.repeatCount).map { index ->
                    source.offset(operation.step.multiply(index)).normalized()
                }
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
