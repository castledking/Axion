package axion.client.compat

import net.minecraft.client.network.ClientPlayerInteractionManager
import java.lang.reflect.Field

/**
 * Reflection helper for the block-breaking cooldown field on
 * [ClientPlayerInteractionManager]. The field name has changed across
 * versions (`blockBreakingCooldown` / `breakCooldown` / `cooldown`),
 * so we look it up by name at runtime.
 *
 * Lives in `axion.client.compat` (not `axion.mixin.*`) because it is
 * not a mixin — it is plain reflection. Keeping it outside any
 * mixin-owned package guarantees it never trips Mixin's class-load
 * policy if a future mixin handler ends up calling into it.
 */
object ClientPlayerInteractionManagerAccess {
    private val blockBreakingCooldownField: Field? by lazy {
        val clazz = ClientPlayerInteractionManager::class.java
        sequenceOf("blockBreakingCooldown", "breakCooldown", "cooldown")
            .mapNotNull { name ->
                try {
                    clazz.getDeclaredField(name).apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    null
                }
            }
            .firstOrNull()
    }

    fun getBlockBreakingCooldown(manager: ClientPlayerInteractionManager): Int {
        val field = blockBreakingCooldownField ?: return 0
        return try {
            field.getInt(manager)
        } catch (_: IllegalAccessException) {
            0
        }
    }

    fun setBlockBreakingCooldown(manager: ClientPlayerInteractionManager, value: Int) {
        val field = blockBreakingCooldownField ?: return
        try {
            field.setInt(manager, value)
        } catch (_: IllegalAccessException) {
            // Ignore
        }
    }
}
