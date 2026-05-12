package axion.mixin.compat

import net.minecraft.client.MinecraftClient
import java.lang.reflect.Field

object MinecraftClientAccess {
    private val itemUseCooldownField: Field? by lazy {
        val clazz = MinecraftClient::class.java
        sequenceOf("itemUseCooldown", "itemUseCooldownTicks", "useCooldown")
            .mapNotNull { name ->
                try {
                    clazz.getDeclaredField(name).apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    null
                }
            }
            .firstOrNull()
    }

    fun getItemUseCooldown(client: MinecraftClient): Int {
        val field = itemUseCooldownField ?: return 0
        return try {
            field.getInt(client)
        } catch (_: IllegalAccessException) {
            0
        }
    }

    fun setItemUseCooldown(client: MinecraftClient, value: Int) {
        val field = itemUseCooldownField ?: return
        try {
            field.setInt(client, value)
        } catch (_: IllegalAccessException) {
            // Ignore
        }
    }
}
