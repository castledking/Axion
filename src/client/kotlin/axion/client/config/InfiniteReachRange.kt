package axion.client.config

object InfiniteReachRange {
    const val MIN_FINITE: Double = 6.0
    const val RAYCAST_CEILING: Double = 256.0
    const val INFINITY_LABEL: String = "∞"

    fun effective(configured: Double?): Double = configured
        ?.takeIf(Double::isFinite)
        ?.coerceIn(MIN_FINITE, RAYCAST_CEILING)
        ?: RAYCAST_CEILING

    fun parse(input: String): Double? {
        val normalized = input.trim()
        if (isUnlimitedInput(normalized)) {
            return null
        }
        return normalized.toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?.coerceIn(MIN_FINITE, RAYCAST_CEILING)
    }

    fun isUnlimitedInput(input: String): Boolean {
        val normalized = input.trim()
        return normalized.isEmpty() || normalized == INFINITY_LABEL ||
            normalized.equals("infinity", ignoreCase = true) ||
            normalized.equals("infinite", ignoreCase = true) ||
            normalized.equals("unlimited", ignoreCase = true)
    }

    fun display(configured: Double?): String {
        val value = configured?.takeIf(Double::isFinite) ?: return INFINITY_LABEL
        val clamped = value.coerceIn(MIN_FINITE, RAYCAST_CEILING)
        return if (clamped % 1.0 == 0.0) clamped.toInt().toString() else clamped.toString()
    }
}
