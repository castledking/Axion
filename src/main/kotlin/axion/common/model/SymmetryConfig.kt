package axion.common.model

data class SymmetryConfig(
    val anchor: SymmetryAnchor,
    val rotationalEnabled: Boolean = false,
    val mirrorYEnabled: Boolean = false,
)
