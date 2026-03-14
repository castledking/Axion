package axion.client.tool

import axion.client.selection.AxionTarget
import axion.client.selection.toDirection
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Direction

object ExtrudeTargetService {
    fun resolveDirection(client: MinecraftClient, target: AxionTarget): Direction {
        return when (target) {
            is AxionTarget.FaceTarget -> target.face.toDirection()
            is AxionTarget.BlockTarget -> dominantLookDirection(client)
            AxionTarget.MissTarget -> dominantLookDirection(client)
        }
    }

    private fun dominantLookDirection(client: MinecraftClient): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return Direction.getFacing(look)
    }
}
