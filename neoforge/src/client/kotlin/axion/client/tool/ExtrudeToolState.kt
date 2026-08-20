package axion.client.tool

sealed interface ExtrudeToolState {
    data object Idle : ExtrudeToolState

    data class Previewing(val preview: ExtrudePreviewState) : ExtrudeToolState
}
