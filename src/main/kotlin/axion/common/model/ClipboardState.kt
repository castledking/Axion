package axion.common.model

sealed interface ClipboardState {
    data object Empty : ClipboardState

    data class MagicSelection(
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer,
    ) : ClipboardState
}
