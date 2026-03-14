package axion.common.model

sealed interface SymmetryState {
    data object Inactive : SymmetryState

    data class Active(val config: SymmetryConfig) : SymmetryState
}
