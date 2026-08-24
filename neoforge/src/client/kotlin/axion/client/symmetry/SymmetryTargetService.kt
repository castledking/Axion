package axion.client.symmetry

import axion.client.compat.rotationVecClient
import axion.client.selection.AxionTarget
import axion.client.symmetry.directionGetFacing
import axion.common.model.SymmetryMirrorAxis
import axion.common.model.SymmetryAnchor
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
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

    fun resolveNudgeDirection(client: Minecraft, target: AxionTarget): Direction {
        return when (target) {
            is AxionTarget.FaceTarget -> target.face.direction
            is AxionTarget.BlockTarget -> directionGetFacing(client.player?.rotationVecClient ?: Vec3(0.0, 1.0, 0.0))
            AxionTarget.MissTarget -> directionGetFacing(client.player?.rotationVecClient ?: Vec3(0.0, 1.0, 0.0))
        }
    }

    fun resolveMirrorAxis(client: Minecraft): SymmetryMirrorAxis {
        val look = client.player?.rotationVecClient ?: return SymmetryMirrorAxis.X
        val ax = abs(look.x)
        val ay = abs(look.y)
        val az = abs(look.z)
        return when {
            ay >= ax && ay >= az -> SymmetryMirrorAxis.Y
            ax >= ay && ax >= az -> SymmetryMirrorAxis.X
            else -> SymmetryMirrorAxis.Z
        }
    }

    private fun quantizeToHalfGrid(pos: Vec3): Vec3 {
        return Vec3(
            quantizeToHalf(pos.x),
            quantizeToHalf(pos.y),
            quantizeToHalf(pos.z),
        )
    }

    private fun quantizeToHalf(value: Double): Double {
        return round(value * 2.0) / 2.0
    }
}
