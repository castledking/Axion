package axion.client.tool

enum class PlacementMirrorAxis {
    NONE,
    X,
    Y,
    Z,
}

data class PlacementTransform(
    val rotationQuarterTurns: Int = 0,
    val mirrorAxis: PlacementMirrorAxis = PlacementMirrorAxis.NONE,
) {
    val normalizedRotationQuarterTurns: Int = Math.floorMod(rotationQuarterTurns, 4)

    fun rotateClockwise(): PlacementTransform {
        return copy(rotationQuarterTurns = normalizedRotationQuarterTurns + 1)
    }

    fun toggleMirror(axis: PlacementMirrorAxis): PlacementTransform {
        return copy(mirrorAxis = if (mirrorAxis == axis) PlacementMirrorAxis.NONE else axis)
    }

    fun isIdentity(): Boolean {
        return normalizedRotationQuarterTurns == 0 && mirrorAxis == PlacementMirrorAxis.NONE
    }
}
