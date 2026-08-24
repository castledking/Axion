package axion.client.itemStack

sealed interface ExtrudeToolState {
    data object Idle : ExtrudeToolState

    data class Previewing(val preview: ExtrudePreviewState) : ExtrudeToolState
}
