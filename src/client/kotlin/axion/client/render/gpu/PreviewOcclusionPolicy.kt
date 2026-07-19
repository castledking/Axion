package axion.client.render.gpu

object PreviewOcclusionPolicy {
    inline fun <T> isFaceExposed(
        neighbor: T?,
        isOpaqueFullCube: (T) -> Boolean,
    ): Boolean {
        return neighbor == null || !isOpaqueFullCube(neighbor)
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
