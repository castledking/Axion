package axion.mixin.client

import net.minecraft.client.MinecraftClient
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(MinecraftClient::class)
interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    fun axionGetItemUseCooldown(): Int

    @Accessor("itemUseCooldown")
    fun axionSetItemUseCooldown(value: Int)
}
