package axion.client.render

import axion.client.symmetry.SymmetryOperationExpander
import axion.client.tool.StackToolController
import axion.common.operation.StackRegionOperation
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext

object StackPreviewRenderer {
    private const val DESTINATION_COLOR: Int = 0xFFFF9F5A.toInt()
    private const val LINE_WIDTH: Float = 1.5f

    fun render(context: WorldRenderContext) {
        val preview = StackToolController.currentPreview() ?: return
        val destinationRegions = SymmetryOperationExpander.expand(
            StackRegionOperation(
                sourceRegion = preview.sourceRegion,
                clipboardBuffer = preview.clipboardBuffer,
                step = preview.step,
                repeatCount = preview.repeatCount,
            ),
        ).filterIsInstance<StackRegionOperation>()
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
