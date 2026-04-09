package axion.common.model

data class GlobalModeState(
    val noClipEnabled: Boolean = false,
    val replaceModeEnabled: Boolean = false,
    val infiniteReachEnabled: Boolean = false,
    val bulldozerEnabled: Boolean = false,
    val fastPlaceEnabled: Boolean = false,
)
