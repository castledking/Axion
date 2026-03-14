package axion.server.paper

import java.util.concurrent.TimeUnit

class AxionTimingContext {
    private val requestStartNanos: Long = System.nanoTime()
    private var validationNanos: Long = 0L
    private var worldGuardNanos: Long = 0L
    private var planningNanos: Long = 0L
    private var applyNanos: Long = 0L
    private var diffNanos: Long = 0L
    private var transportNanos: Long = 0L

    fun <T> measureValidation(block: () -> T): T = measure(block) { validationNanos += it }

    fun <T> measureWorldGuard(block: () -> T): T = measure(block) { worldGuardNanos += it }

    fun <T> measurePlanning(block: () -> T): T = measure(block) { planningNanos += it }

    fun <T> measureApply(block: () -> T): T = measure(block) { applyNanos += it }

    fun <T> measureDiff(block: () -> T): T = measure(block) { diffNanos += it }

    fun <T> measureTransport(block: () -> T): T = measure(block) { transportNanos += it }

    fun snapshot(): AxionTimingSnapshot {
        return AxionTimingSnapshot(
            validationMillis = validationNanos.toMillis(),
            worldGuardMillis = worldGuardNanos.toMillis(),
            planningMillis = planningNanos.toMillis(),
            applyMillis = applyNanos.toMillis(),
            diffMillis = diffNanos.toMillis(),
            transportMillis = transportNanos.toMillis(),
            totalMillis = (System.nanoTime() - requestStartNanos).toMillis(),
        )
    }

    private inline fun <T> measure(block: () -> T, record: (Long) -> Unit): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(System.nanoTime() - start)
        }
    }

    private fun Long.toMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)
}

data class AxionTimingSnapshot(
    val validationMillis: Long,
    val worldGuardMillis: Long,
    val planningMillis: Long,
    val applyMillis: Long,
    val diffMillis: Long,
    val transportMillis: Long,
    val totalMillis: Long,
)
