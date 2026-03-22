package axion.common.model

enum class SymmetryMirrorAxis {
    X,
    Z,
}

data class SymmetryConfig(
    val anchor: SymmetryAnchor,
    val rotationalEnabled: Boolean = false,
    val mirrorEnabled: Boolean = false,
    val mirrorAxis: SymmetryMirrorAxis = SymmetryMirrorAxis.X,
)
