package axion.common.model

enum class SymmetryMirrorAxis {
    X,
    Y,
    Z,
}

data class SymmetryConfig(
    val anchor: SymmetryAnchor,
    val rotationalEnabled: Boolean = false,
    val mirrorEnabled: Boolean = false,
    val mirrorAxis: SymmetryMirrorAxis = SymmetryMirrorAxis.X,
    val constructEnabled: Boolean = false,
)
