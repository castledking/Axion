package axion.client.tool

data class PlacementTransform(
    val rotationQuarterTurns: Int = 0,
    val mirrored: Boolean = false,
) {
    val normalizedRotationQuarterTurns: Int = Math.floorMod(rotationQuarterTurns, 4)

    fun rotateClockwise(): PlacementTransform {
        return copy(rotationQuarterTurns = normalizedRotationQuarterTurns + 1)
    }

    fun toggleMirror(): PlacementTransform {
        return copy(mirrored = !mirrored)
    }

    fun isIdentity(): Boolean {
        return normalizedRotationQuarterTurns == 0 && !mirrored
    }
}
