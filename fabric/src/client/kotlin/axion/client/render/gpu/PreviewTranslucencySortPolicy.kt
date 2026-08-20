package axion.client.render.gpu

import kotlin.math.floor

/** Camera-dependent ordering contract for the 26.x translucent GPU meshes. */
object PreviewTranslucencySortPolicy {
    private const val RESORT_DISTANCE_SQUARED = 1.0f

    data class SectionOrigin(
        val x: Int,
        val y: Int,
        val z: Int,
    )

    data class Point(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    data class GlobalMeshPlan(
        val anchor: SectionOrigin,
        val sectionCount: Int,
    ) {
        /** Every translucent quad must participate in the same index sort. */
        val batchCount: Int = 1
    }

    /**
     * A per-section draw order cannot correctly blend sections whose screen
     * projections and depth intervals overlap. Plan one mesh anchored at the
     * component-wise minimum so all quad centroids share one vanilla sort.
     */
    fun globalMeshPlan(sections: Collection<SectionOrigin>): GlobalMeshPlan? {
        if (sections.isEmpty()) return null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        sections.forEach { section ->
            minX = minOf(minX, section.x)
            minY = minOf(minY, section.y)
            minZ = minOf(minZ, section.z)
        }
        return GlobalMeshPlan(
            anchor = SectionOrigin(minX, minY, minZ),
            sectionCount = sections.size,
        )
    }

    /** Moving the preview is equivalent to moving the camera oppositely. */
    fun effectiveCamera(
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
        deltaX: Int,
        deltaY: Int,
        deltaZ: Int,
    ): Point = Point(
        x = (cameraX - deltaX).toFloat(),
        y = (cameraY - deltaY).toFloat(),
        z = (cameraZ - deltaZ).toFloat(),
    )

    /**
     * Match vanilla's bounded re-sort cadence. Quad distance from the camera is
     * rotation invariant, and sub-block camera jitter should not upload a fresh
     * index buffer every frame.
     */
    fun shouldResort(
        previousX: Float,
        previousY: Float,
        previousZ: Float,
        currentX: Float,
        currentY: Float,
        currentZ: Float,
    ): Boolean {
        if (!previousX.isFinite() || !previousY.isFinite() || !previousZ.isFinite()) return true
        if (
            floor(previousX) != floor(currentX) ||
            floor(previousY) != floor(currentY) ||
            floor(previousZ) != floor(currentZ)
        ) return true
        val dx = currentX - previousX
        val dy = currentY - previousY
        val dz = currentZ - previousZ
        return dx * dx + dy * dy + dz * dz >= RESORT_DISTANCE_SQUARED
    }
}
