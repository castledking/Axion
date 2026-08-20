package axion.client.mode

import net.minecraft.entity.player.PlayerEntity

fun PlayerEntity.blockInteractionRange(): Double = blockInteractionRange

fun blockInteractionRangeOf(player: PlayerEntity): Double = player.blockInteractionRange()
