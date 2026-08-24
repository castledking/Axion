package axion.client.mode

import net.minecraft.world.entity.player.Player

fun Player.blockInteractionRangeCompat(): Double = this.blockInteractionRange()

fun blockInteractionRangeOf(player: Player): Double = player.blockInteractionRange()
