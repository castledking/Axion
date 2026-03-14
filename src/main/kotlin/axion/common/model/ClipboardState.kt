package axion.common.model

sealed interface ClipboardState {
    data object Empty : ClipboardState
}
