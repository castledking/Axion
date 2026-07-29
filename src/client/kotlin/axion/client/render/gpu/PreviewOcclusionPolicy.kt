package axion.client.render.gpu

object PreviewOcclusionPolicy {
    inline fun <T> isFaceExposed(
        neighbor: T?,
        isOpaqueFullCube: (T) -> Boolean,
    ): Boolean {
        return neighbor == null || !isOpaqueFullCube(neighbor)
    }

    /**
     * Decides whether a preview neighbor should be reported as air so vanilla
     * emits the touching face. Identical translucent blocks must remain in the
     * view: vanilla glass/leaf side-culling then removes their shared face.
     */
    inline fun <T> shouldReplaceNeighborWithAir(
        rendering: T?,
        neighbor: T?,
        isSameOcclusionGroup: (T, T) -> Boolean,
        isOpaqueFullCube: (T) -> Boolean,
    ): Boolean {
        if (neighbor == null) return true
        if (rendering != null && isSameOcclusionGroup(rendering, neighbor)) return false
        return !isOpaqueFullCube(neighbor)
    }

    fun isDirectNeighbor(
        x: Int,
        y: Int,
        z: Int,
        originX: Int,
        originY: Int,
        originZ: Int,
    ): Boolean {
        return absoluteDifference(x, originX) +
            absoluteDifference(y, originY) +
            absoluteDifference(z, originZ) == 1
    }

    private fun absoluteDifference(first: Int, second: Int): Int {
        val difference = first - second
        return if (difference < 0) -difference else difference
    }
}
