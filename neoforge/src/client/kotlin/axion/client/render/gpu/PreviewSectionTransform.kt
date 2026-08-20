package axion.client.render.gpu

/**
 * Converts a section-local preview mesh origin into the camera-relative
 * translation expected by Minecraft's world-render pipelines.
 */
object PreviewSectionTransform {
    data class Translation(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    fun cameraRelative(
        sectionOriginX: Int,
        sectionOriginY: Int,
        sectionOriginZ: Int,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        deltaX: Int,
        deltaY: Int,
        deltaZ: Int,
    ): Translation = Translation(
        x = (sectionOriginX.toDouble() + deltaX.toDouble() - cameraX).toFloat(),
        y = (sectionOriginY.toDouble() + deltaY.toDouble() - cameraY).toFloat(),
        z = (sectionOriginZ.toDouble() + deltaZ.toDouble() - cameraZ).toFloat(),
    )
}
