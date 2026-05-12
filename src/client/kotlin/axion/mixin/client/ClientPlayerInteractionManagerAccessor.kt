package axion.mixin.compat

import net.minecraft.client.network.ClientPlayerInteractionManager
import java.lang.reflect.Field

/**
 * Helper object to access ClientPlayerInteractionManager fields via reflection.
 * Uses reflection to handle different Minecraft versions.
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
