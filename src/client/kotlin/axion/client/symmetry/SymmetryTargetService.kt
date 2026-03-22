package axion.client.symmetry

import axion.client.selection.AxionTarget
import axion.common.model.SymmetryMirrorAxis
import axion.common.model.SymmetryAnchor
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.round

object SymmetryTargetService {
    fun resolveAnchor(target: AxionTarget): SymmetryAnchor? {
        return when (target) {
            is AxionTarget.FaceTarget -> SymmetryAnchor(
                position = quantizeToHalfGrid(target.hitPos),
                face = target.face.direction,
            )

            is AxionTarget.BlockTarget -> SymmetryAnchor(position = quantizeToHalfGrid(target.hitPos))
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

    fun resolveMirrorAxis(client: MinecraftClient): SymmetryMirrorAxis {
        val look = client.player?.rotationVecClient ?: return SymmetryMirrorAxis.X
        return if (abs(look.x) >= abs(look.z)) {
            SymmetryMirrorAxis.X
        } else {
            SymmetryMirrorAxis.Z
        }
    }

    private fun quantizeToHalfGrid(pos: Vec3d): Vec3d {
        return Vec3d(
            quantizeToHalf(pos.x),
            quantizeToHalf(pos.y),
            quantizeToHalf(pos.z),
        )
    }

    private fun quantizeToHalf(value: Double): Double {
        return round(value * 2.0) / 2.0
    }
}
