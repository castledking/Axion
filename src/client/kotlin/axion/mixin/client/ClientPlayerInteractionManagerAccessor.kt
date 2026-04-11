package axion.mixin.client

import net.minecraft.client.network.ClientPlayerInteractionManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ClientPlayerInteractionManager::class)
interface ClientPlayerInteractionManagerAccessor {
    @Accessor("blockBreakingCooldown")
    fun axionGetBlockBreakingCooldown(): Int

    @Accessor("blockBreakingCooldown")
    fun axionSetBlockBreakingCooldown(value: Int)
}
