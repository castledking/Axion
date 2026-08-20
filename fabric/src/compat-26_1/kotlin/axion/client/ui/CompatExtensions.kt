package axion.client.ui

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.MutableComponent

fun MutableComponent.withFormatting(vararg formatting: ChatFormatting): MutableComponent =
    withStyle(*formatting)
