package axion.client.tool

import axion.client.compat.rotationVecClient
import axion.client.selection.AxionTarget
import axion.client.selection.toDirection
import axion.client.tool.directionGetFacing
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction

object ExtrudeTargetService {
    fun resolveDirection(client: Minecraft, target: AxionTarget): Direction {
        return when (target) {
            is AxionTarget.FaceTarget -> target.face.toDirection()
            is AxionTarget.BlockTarget -> dominantLookDirection(client)
            AxionTarget.MissTarget -> dominantLookDirection(client)
        }
    }

    private fun dominantLookDirection(client: Minecraft): Direction {
        val look = client.player?.rotationVecClient ?: return Direction.UP
        return directionGetFacing(look)
    }
}
