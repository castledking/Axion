package axion.client.itemStack

import axion.client.current.AxionTarget
import axion.client.current.toDirection
import axion.client.itemStack.directionGetFacing
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
