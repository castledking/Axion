package axion.client.render

object SymmetryGizmoStylePolicy {
    const val INACTIVE_COLOR: Int = 0xFF9E9E9E.toInt()
    const val ACTIVE_COLOR: Int = 0xFFF2C94C.toInt()

    fun color(
        rotationalEnabled: Boolean,
        mirrorEnabled: Boolean,
    ): Int = if (rotationalEnabled || mirrorEnabled) ACTIVE_COLOR else INACTIVE_COLOR
}
