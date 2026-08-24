package axion.client.mode

import net.minecraft.world.entity.player.Player

fun Player.blockInteractionRange(): Double = blockInteractionRange

fun blockInteractionRangeOf(player: Player): Double = player.blockInteractionRange()
