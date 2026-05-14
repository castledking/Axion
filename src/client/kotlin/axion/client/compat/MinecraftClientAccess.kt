package axion.client.compat

import net.minecraft.client.MinecraftClient
import java.lang.reflect.Field

/**
 * Reflection helper for the private `itemUseCooldown` field on
 * [MinecraftClient]. The field name has changed across versions
 * (`itemUseCooldown` / `itemUseCooldownTicks` / `useCooldown`),
 * so we look it up by name at runtime.
 *
 * Lives in `axion.client.compat` (not `axion.mixin.*`) because it is
 * not a mixin — it is plain reflection. Keeping it outside any
 * mixin-owned package guarantees it never trips Mixin's class-load
 * policy if a future mixin handler ends up calling into it.
 */
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
