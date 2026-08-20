package axion.client.mode

/** Version-neutral visual behavior while the local player is actively no-clipping. */
object NoClipVisualPolicy {
    fun cameraDistanceOverride(requestedDistance: Float, noClipActive: Boolean): Float? {
        return requestedDistance.takeIf { noClipActive }
    }

    fun suppressInWallOverlay(noClipActive: Boolean): Boolean = noClipActive

    fun forceStandingPose(noClipActive: Boolean): Boolean = noClipActive
}
