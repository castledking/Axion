package axion.client.mode

import axion.client.AxionClientState
import axion.client.selection.AxionTarget
import axion.client.selection.AxionTargeting
import axion.client.selection.SelectionRaycast
import axion.client.selection.toDirection
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult

object ModeTargeting {
    data class BlockTarget(
        val hitResult: BlockHitResult,
        val squaredDistance: Double,
        val beyondVanillaReach: Boolean,
    )

    fun currentBlockTarget(client: MinecraftClient): BlockTarget? {
        val player = client.player ?: return null
        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val vanillaReach = player.blockInteractionRange
        val hitResult = if (AxionClientState.globalModeState.infiniteReachEnabled) {
            val selectionTarget = SelectionRaycast.raycast(client, AxionTargeting.DEFAULT_REACH)
            when (selectionTarget) {
                is AxionTarget.FaceTarget -> BlockHitResult(
                    selectionTarget.hitPos,
                    selectionTarget.face.toDirection(),
                    selectionTarget.blockPos,
                    false,
                )

                is AxionTarget.BlockTarget -> BlockHitResult(
                    selectionTarget.hitPos,
                    net.minecraft.util.math.Direction.UP,
                    selectionTarget.blockPos,
                    false,
                )

                AxionTarget.MissTarget -> null
            }
        } else {
            when (val crosshair = client.crosshairTarget) {
                is BlockHitResult -> {
                    if (crosshair.type == HitResult.Type.BLOCK) {
                        crosshair
                    } else {
                        null
                    }
                }

                else -> null
            }
        } ?: return null
        val squaredDistance = origin.squaredDistanceTo(hitResult.pos)
        val beyondVanillaReach = squaredDistance > (vanillaReach * vanillaReach) + 1.0e-6
        if (beyondVanillaReach && !AxionClientState.globalModeState.infiniteReachEnabled) {
            return null
        }
        return BlockTarget(
            hitResult = hitResult,
            squaredDistance = squaredDistance,
            beyondVanillaReach = beyondVanillaReach,
        )
    }
}
