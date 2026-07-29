package axion.client.compat

import axion.common.compat.VersionCompat

object VersionCompatInit {
    fun init() {
        VersionCompat.initialize(VersionCompatImpl)
        PreviewResourceReloadInvalidation.register()
    }
}
