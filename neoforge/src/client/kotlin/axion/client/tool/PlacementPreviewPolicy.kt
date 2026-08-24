package axion.client.tool

object PlacementPreviewPolicy {
    fun activePreview(state: CloneToolState): ClonePreviewState? = when (state) {
        CloneToolState.Idle,
        is CloneToolState.FirstCornerSet,
        is CloneToolState.RegionDefined,
            -> null

        is CloneToolState.PreviewingOffset -> state.preview
        is CloneToolState.AwaitingConfirm -> state.preview
    }

    fun shouldRenderMoveSourceReplacement(preview: ClonePreviewState): Boolean {
        return preview.mode == PlacementToolMode.MOVE
    }
}
