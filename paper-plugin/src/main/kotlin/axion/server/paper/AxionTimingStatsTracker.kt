package axion.server.paper

import org.bukkit.plugin.Plugin

class AxionTimingStatsTracker(
    private val plugin: Plugin,
    private val enabled: Boolean,
    private val summaryEvery: Int,
) {
    private val statsByOperation = linkedMapOf<String, OperationStats>()
    private var recordedActions: Int = 0

    fun record(operationSummary: String, timing: AxionTimingSnapshot) {
        if (!enabled) {
            return
        }
        val stats = statsByOperation.getOrPut(operationSummary) { OperationStats() }
        stats.record(timing)
        recordedActions++
        if (summaryEvery > 0 && recordedActions % summaryEvery == 0) {
            logSummary("interval")
        }
    }

    fun logSummary(reason: String) {
        if (!enabled) {
            return
        }
        if (statsByOperation.isEmpty()) {
            return
        }

        plugin.logger.info("AxionTimingSummary reason=$reason operations=${statsByOperation.size} actions=$recordedActions")
        statsByOperation.forEach { (operation, stats) ->
            plugin.logger.info(
                buildString {
                    append("AxionTimingSummary")
                    append(" op=").append(operation)
                    append(" count=").append(stats.count)
                    append(" avgTotalMs=").append(stats.averageTotalMillis())
                    append(" p95TotalMs=").append(stats.p95TotalMillis())
                    append(" avgValidationMs=").append(stats.average(stats.validationMillisSum))
                    append(" avgWorldGuardMs=").append(stats.average(stats.worldGuardMillisSum))
                    append(" avgPlanningMs=").append(stats.average(stats.planningMillisSum))
                    append(" avgApplyMs=").append(stats.average(stats.applyMillisSum))
                    append(" avgDiffMs=").append(stats.average(stats.diffMillisSum))
                    append(" avgTransportMs=").append(stats.average(stats.transportMillisSum))
                },
            )
        }
    }

    fun reset() {
        statsByOperation.clear()
        recordedActions = 0
    }

    private class OperationStats {
        var count: Int = 0
            private set
        var validationMillisSum: Long = 0
            private set
        var worldGuardMillisSum: Long = 0
            private set
        var planningMillisSum: Long = 0
            private set
        var applyMillisSum: Long = 0
            private set
        var diffMillisSum: Long = 0
            private set
        var transportMillisSum: Long = 0
            private set
        private var totalMillisSum: Long = 0
        private val totalSamples = mutableListOf<Long>()

        fun record(timing: AxionTimingSnapshot) {
            count++
            validationMillisSum += timing.validationMillis
            worldGuardMillisSum += timing.worldGuardMillis
            planningMillisSum += timing.planningMillis
            applyMillisSum += timing.applyMillis
            diffMillisSum += timing.diffMillis
            transportMillisSum += timing.transportMillis
            totalMillisSum += timing.totalMillis
            totalSamples += timing.totalMillis
        }

        fun averageTotalMillis(): Long = average(totalMillisSum)

        fun p95TotalMillis(): Long {
            if (totalSamples.isEmpty()) {
                return 0L
            }
            val sorted = totalSamples.sorted()
            val index = ((sorted.size - 1) * 0.95).toInt()
            return sorted[index]
        }

        fun average(sum: Long): Long {
            return if (count == 0) 0L else sum / count
        }
    }
}
