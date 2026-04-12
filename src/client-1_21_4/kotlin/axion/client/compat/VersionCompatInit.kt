package axion.client.compat

import axion.common.compat.VersionCompat

/**
 * Initializes the version compatibility layer for Minecraft 1.21.0 - 1.21.4
 */
object VersionCompatInit {
    fun init() {
        VersionCompat.initialize(VersionCompatImpl)
    }
}
