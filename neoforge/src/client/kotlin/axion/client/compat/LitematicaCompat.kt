package axion.client.compat

import net.neoforged.fml.ModList
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries

object LitematicaCompat {
    private val available: Boolean by lazy {
        ModList.get().hasClientLoaded("litematica")
    }

    private val genericConfigsClass: Class<*>? by lazy {
        reflectClass("fi.dy.masa.litematica.config.Configs\$Generic")
    }

    private val configStringClass: Class<*>? by lazy {
        reflectClass("fi.dy.masa.malilib.config.options.ConfigString")
    }

    private val configBooleanClass: Class<*>? by lazy {
        reflectClass("fi.dy.masa.malilib.config.options.ConfigBoolean")
    }

    fun isHoldingConfiguredTool(client: Minecraft): Boolean {
        if (!available) {
            return false
        }

        val player = client.player ?: return false
        val configuredItemId = configuredToolItemId() ?: return false
        val heldItemId = BuiltInRegistries.ITEM.getId(player.mainHandStack.item).toString()
        return heldItemId == configuredItemId
    }

    private fun configuredToolItemId(): String? {
        if (!isToolItemEnabled()) {
            return null
        }

        val genericClass = genericConfigsClass ?: return null
        val stringClass = configStringClass ?: return null
        return runCatching {
            val field = genericClass.getField("TOOL_ITEM")
            val config = field.get(null) ?: return null
            val method = stringClass.getMethodName("getStringValue")
            val configured = method.invoke(config) as? String ?: return null
            configured
                .substringBefore("[")
                .substringBefore("{")
                .trim()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun isToolItemEnabled(): Boolean {
        val genericClass = genericConfigsClass ?: return false
        val booleanClass = configBooleanClass ?: return false
        return runCatching {
            val field = genericClass.getField("TOOL_ITEM_ENABLED")
            val config = field.get(null) ?: return false
            val method = booleanClass.getMethodName("getBooleanValue")
            method.invoke(config) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun reflectClass(name: String): Class<*>? {
        return runCatching { Class.forName(name) }.getOrNull()
    }
}
