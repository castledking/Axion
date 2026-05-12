package axion.client.compat

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand

val PlayerEntity.mainHandStack: ItemStack
    get() = getItemInHand(Hand.MAIN_HAND)
