package axion.client.compat

/**
 * Initializes the version compatibility layer for Minecraft 1.20.6.
 * This version uses Registries instead of BuiltInRegistries and
 * PacketByteBuf instead of RegistryByteBuf.
 */
object VersionCompatInit {
    fun init() {
        axion.common.compat.VersionCompat.initialize(VersionCompatImpl)
    }
}
