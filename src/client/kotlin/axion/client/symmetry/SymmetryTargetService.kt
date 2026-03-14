package axion.client.symmetry

import axion.client.selection.AxionTarget
import axion.common.model.SymmetryAnchor
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object SymmetryTargetService {
    fun resolveAnchor(target: AxionTarget): SymmetryAnchor? {
        return when (target) {
            is AxionTarget.FaceTarget -> {
                val blockCenter = Vec3d.ofCenter(target.blockPos)
                val faceOffset = Vec3d(
                    target.face.direction.offsetX * 0.5,
                    target.face.direction.offsetY * 0.5,
                    target.face.direction.offsetZ * 0.5,
                )
                SymmetryAnchor(blockCenter.add(faceOffset))
            }

            is AxionTarget.BlockTarget -> SymmetryAnchor(Vec3d.ofCenter(target.blockPos))
            AxionTarget.MissTarget -> null
        }
    }

    fun resolveNudgeDirection(client: MinecraftClient, target: AxionTarget): Direction {
        return when (target) {
            is AxionTarget.FaceTarget -> target.face.direction
            is AxionTarget.BlockTarget -> Direction.getFacing(client.player?.rotationVecClient ?: Vec3d(0.0, 1.0, 0.0))
            AxionTarget.MissTarget -> Direction.getFacing(client.player?.rotationVecClient ?: Vec3d(0.0, 1.0, 0.0))
        }
    }
}
