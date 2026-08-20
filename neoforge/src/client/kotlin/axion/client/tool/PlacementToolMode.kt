package axion.client.tool

import axion.common.model.AxionSubtool

enum class PlacementToolMode {
    CLONE,
    MOVE;

    companion object {
        fun fromSubtool(subtool: AxionSubtool): PlacementToolMode? = when (subtool) {
            AxionSubtool.CLONE -> CLONE
            AxionSubtool.MOVE -> MOVE
            else -> null
        }
    }
}
