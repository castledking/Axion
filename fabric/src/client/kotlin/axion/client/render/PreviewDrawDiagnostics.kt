package axion.client.render

import org.slf4j.LoggerFactory

/**
 * One-shot-on-change logging for the direct GPU preview draw.
 *
 * The draw is a hand-rolled render pass, so when it renders wrongly there is no
 * exception and no warning — it just draws. This records the handful of values
 * the pass depends on and logs a line only when one of them changes, so a
 * side-by-side run (with and without a suspect mod) shows exactly which input
 * differs rather than requiring a graphics capture.
 *
 * Off unless `-Daxion.previewDiagnostics=true` is set, and silent while nothing
 * changes, so it costs a string build per frame at most when enabled.
 */
object PreviewDrawDiagnostics {
    private val logger = LoggerFactory.getLogger(PreviewDrawDiagnostics::class.java)

    val isEnabled: Boolean by lazy {
        System.getProperty("axion.previewDiagnostics")?.equals("true", ignoreCase = true) == true
    }

    private var lastLine: String? = null
    private var drawCount: Long = 0

    /**
     * Logs [fields] the first time they are seen and on every later change.
     *
     * Identity matters as much as value here — a texture view or pipeline that
     * is silently swapped between frames is exactly the kind of thing being
     * hunted — so pass those through [identity] rather than `toString`.
     */
    fun record(vararg fields: Pair<String, Any?>) {
        if (!isEnabled) return

        drawCount++
        val line = fields.joinToString(" ") { (name, value) -> "$name=$value" }
        if (line == lastLine) return

        lastLine = line
        logger.info("[Axion GPU] preview draw #{} {}", drawCount, line)
    }

    /** Stable per-object tag: `SimpleName@hash`, or `null`. */
    fun identity(value: Any?): String =
        if (value == null) "null" else "${value.javaClass.simpleName}@${System.identityHashCode(value)}"

    fun reset() {
        lastLine = null
        drawCount = 0
    }
}
