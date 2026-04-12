package axion.client.compat

import axion.common.compat.VersionCompat

/**
 * Initializes the version compatibility layer for Minecraft 1.21.5 - 1.21.7
 */
object VersionCompatInit {
    fun init() {
        VersionCompat.initialize(VersionCompatImpl)
    }
}
