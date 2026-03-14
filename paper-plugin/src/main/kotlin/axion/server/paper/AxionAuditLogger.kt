package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class AxionAuditLogger(
    private val plugin: Plugin,
    private val statsTracker: AxionTimingStatsTracker,
    private val slowThresholdMillis: Long,
) {
    fun logAccepted(
        player: Player,
        world: World,
        actionLabel: String,
        operationSummary: String,
        usesSymmetry: Boolean,
        touchedBlockCount: Int,
        plannedWriteCount: Int,
        changedBlockCount: Int,
        transactionId: Long?,
        timing: AxionTimingSnapshot,
    ) {
        statsTracker.record(operationSummary, timing)
        log(
            level = if (timing.totalMillis >= slowThresholdMillis) LogLevel.WARN else LogLevel.INFO,
            message = buildString {
                append("AxionAudit result=accepted")
                appendCommon(
                    player = player,
                    world = world,
                    actionLabel = actionLabel,
                    operationSummary = operationSummary,
                    usesSymmetry = usesSymmetry,
                    touchedBlockCount = touchedBlockCount,
                    plannedWriteCount = plannedWriteCount,
                )
                append(" changed=").append(changedBlockCount)
                append(" transactionId=").append(transactionId ?: "none")
                appendTiming(timing)
            },
        )
    }

    fun logDenied(
        player: Player,
        world: World,
        actionLabel: String,
        operationSummary: String,
        usesSymmetry: Boolean,
        touchedBlockCount: Int,
        plannedWriteCount: Int,
        rejection: AxionRejection,
        timing: AxionTimingSnapshot,
    ) {
        statsTracker.record(operationSummary, timing)
        log(
            level = if (timing.totalMillis >= slowThresholdMillis) LogLevel.WARN else LogLevel.INFO,
            message = buildString {
                append("AxionAudit result=denied")
                appendCommon(
                    player = player,
                    world = world,
                    actionLabel = actionLabel,
                    operationSummary = operationSummary,
                    usesSymmetry = usesSymmetry,
                    touchedBlockCount = touchedBlockCount,
                    plannedWriteCount = plannedWriteCount,
                )
                append(" code=").append(rejection.code.name)
                append(" source=").append(rejection.source.name)
                rejection.blockedPosition?.let { pos ->
                    append(" blocked=").append(pos.x).append(',').append(pos.y).append(',').append(pos.z)
                }
                append(" message=\"").append(rejection.message.replace("\"", "'")).append('"')
                appendTiming(timing)
            },
        )
    }

    fun logTransportDenied(
        player: Player,
        world: World,
        actionLabel: String,
        operationSummary: String,
        usesSymmetry: Boolean,
        touchedBlockCount: Int,
        plannedWriteCount: Int,
        timing: AxionTimingSnapshot,
    ) {
        logDenied(
            player = player,
            world = world,
            actionLabel = actionLabel,
            operationSummary = operationSummary,
            usesSymmetry = usesSymmetry,
            touchedBlockCount = touchedBlockCount,
            plannedWriteCount = plannedWriteCount,
            rejection = AxionRejection(
                code = AxionResultCode.TRANSPORT_BUDGET_EXCEEDED,
                source = AxionResultSource.TRANSPORT,
                message = "Committed diff exceeded transport budget",
            ),
            timing = timing,
        )
    }

    private fun StringBuilder.appendCommon(
        player: Player,
        world: World,
        actionLabel: String,
        operationSummary: String,
        usesSymmetry: Boolean,
        touchedBlockCount: Int,
        plannedWriteCount: Int,
    ) {
        append(" player=").append(player.name)
        append(" uuid=").append(player.uniqueId)
        append(" world=").append(world.name)
        append(" action=").append(actionLabel)
        append(" ops=").append(operationSummary)
        append(" symmetry=").append(usesSymmetry)
        append(" touched=").append(touchedBlockCount)
        append(" plannedWrites=").append(plannedWriteCount)
    }

    private fun StringBuilder.appendTiming(timing: AxionTimingSnapshot) {
        append(" validationMs=").append(timing.validationMillis)
        append(" worldGuardMs=").append(timing.worldGuardMillis)
        append(" planningMs=").append(timing.planningMillis)
        append(" applyMs=").append(timing.applyMillis)
        append(" diffMs=").append(timing.diffMillis)
        append(" transportMs=").append(timing.transportMillis)
        append(" totalMs=").append(timing.totalMillis)
    }

    private fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.INFO -> plugin.logger.info(message)
            LogLevel.WARN -> plugin.logger.warning(message)
        }
    }

    private enum class LogLevel {
        INFO,
        WARN,
    }
}
