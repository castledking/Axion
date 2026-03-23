package axion.server.paper

import axion.protocol.AxionRemoteOperation
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneEntitiesRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.DeleteEntitiesRequest
import axion.protocol.ExtrudeRequest
import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.PlaceBlocksRequest
import axion.protocol.SmearRegionRequest
import axion.protocol.StackRegionRequest
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

class AxionPolicyService(
    private val plugin: AxionPaperPlugin,
) {
    fun validateBatchStart(
        player: Player,
        world: World,
        operations: List<AxionRemoteOperation>,
        usesSymmetry: Boolean,
        timing: AxionTimingContext,
    ): AxionRejection? {
        val policy = timing.measureValidation { effectiveWorldPolicy(player, world) }
        if (!policy.enabled) {
            return AxionRejection(
                code = AxionResultCode.WORLD_DISABLED,
                source = AxionResultSource.POLICY,
                message = "Axion is disabled in world ${world.name}",
            )
        }
        if (!player.hasPermission(Permissions.USE)) {
            return AxionRejection(
                code = AxionResultCode.PERMISSION_DENIED,
                source = AxionResultSource.PERMISSION,
                message = "Missing permission ${Permissions.USE}",
            )
        }
        if (usesSymmetry && !player.hasPermission(Permissions.SYMMETRY)) {
            return AxionRejection(
                code = AxionResultCode.PERMISSION_DENIED,
                source = AxionResultSource.PERMISSION,
                message = "Missing permission ${Permissions.SYMMETRY}",
            )
        }

        requiredTools(operations).forEach { tool ->
            if (!policy.isToolEnabled(tool)) {
                return AxionRejection(
                    code = AxionResultCode.TOOL_DISABLED,
                    source = AxionResultSource.POLICY,
                    message = "${tool.displayName} is disabled in world ${world.name}",
                )
            }
            val permission = permissionFor(tool)
            if (!player.hasPermission(permission)) {
                return AxionRejection(
                    code = AxionResultCode.PERMISSION_DENIED,
                    source = AxionResultSource.PERMISSION,
                    message = "Missing permission $permission",
                )
            }
        }

        return null
    }

    fun validateUndo(player: Player, world: World, touchedPositions: Set<IntVector3>): AxionRejection? {
        return validateUndo(player, world, touchedPositions, AxionTimingContext())
    }

    fun validateUndo(player: Player, world: World, touchedPositions: Set<IntVector3>, timing: AxionTimingContext): AxionRejection? {
        if (!player.hasPermission(Permissions.UNDO)) {
            return AxionRejection(
                code = AxionResultCode.PERMISSION_DENIED,
                source = AxionResultSource.PERMISSION,
                message = "Missing permission ${Permissions.UNDO}",
            )
        }
        return validateTouchedPositions(
            player = player,
            world = world,
            operationName = "Undo",
            tool = null,
            usesSymmetry = false,
            touchedPositions = touchedPositions,
            timing = timing,
        )
    }

    fun validateRedo(player: Player, world: World, touchedPositions: Set<IntVector3>): AxionRejection? {
        return validateRedo(player, world, touchedPositions, AxionTimingContext())
    }

    fun validateRedo(player: Player, world: World, touchedPositions: Set<IntVector3>, timing: AxionTimingContext): AxionRejection? {
        if (!player.hasPermission(Permissions.UNDO)) {
            return AxionRejection(
                code = AxionResultCode.PERMISSION_DENIED,
                source = AxionResultSource.PERMISSION,
                message = "Missing permission ${Permissions.UNDO}",
            )
        }
        return validateTouchedPositions(
            player = player,
            world = world,
            operationName = "Redo",
            tool = null,
            usesSymmetry = false,
            touchedPositions = touchedPositions,
            timing = timing,
        )
    }

    fun validatePlannedWrites(
        player: Player,
        world: World,
        operations: List<AxionRemoteOperation>,
        usesSymmetry: Boolean,
        touchedPositions: Set<IntVector3>,
        plannedWriteCount: Int,
        timing: AxionTimingContext,
    ): AxionRejection? {
        val policy = timing.measureValidation { effectiveWorldPolicy(player, world) }
        if (touchedPositions.size > policy.maxBlocksPerBatch) {
            return AxionRejection(
                code = AxionResultCode.WRITE_LIMIT_EXCEEDED,
                source = AxionResultSource.POLICY,
                message = "Edit exceeds the configured touched-block limit for world ${world.name}",
            )
        }
        if (plannedWriteCount > policy.maxTotalWrites) {
            return AxionRejection(
                code = AxionResultCode.WRITE_LIMIT_EXCEEDED,
                source = AxionResultSource.POLICY,
                message = "Edit exceeds the configured write limit for world ${world.name}",
            )
        }

        val tool = primaryTool(operations)
        return validateTouchedPositions(
            player = player,
            world = world,
            operationName = tool?.displayName ?: "Edit",
            tool = tool,
            usesSymmetry = usesSymmetry,
            touchedPositions = touchedPositions,
            timing = timing,
        )
    }

    fun worldPolicy(world: World): AxionWorldPolicy {
        val root = plugin.config
        val defaultSection = root.getConfigurationSection("limits.default")
        val worldSection = root.getConfigurationSection("limits.worlds.${world.name}")

        val tools = AxionToolKind.entries.associateWith { tool ->
            getBoolean(worldSection, "tools.${tool.configKey()}", getBoolean(defaultSection, "tools.${tool.configKey()}", true))
        }

        return AxionWorldPolicy(
            enabled = getBoolean(worldSection, "enabled", getBoolean(defaultSection, "enabled", true)),
            tools = tools,
            maxBlocksPerBatch = getInt(worldSection, "max-blocks-per-batch", getInt(defaultSection, "max-blocks-per-batch", 262_144)),
            maxClipboardCells = getInt(worldSection, "max-clipboard-cells", getInt(defaultSection, "max-clipboard-cells", 262_144)),
            maxRepeatCount = getInt(worldSection, "max-repeat-count", getInt(defaultSection, "max-repeat-count", 64)),
            maxTotalWrites = getInt(worldSection, "max-total-writes", getInt(defaultSection, "max-total-writes", 2_097_152)),
            maxExtrudeFootprintSize = getInt(
                worldSection,
                "max-extrude-footprint-size",
                getInt(defaultSection, "max-extrude-footprint-size", 32_768),
            ),
            maxExtrudeWrites = getInt(worldSection, "max-extrude-writes", getInt(defaultSection, "max-extrude-writes", 32_768)),
            historyBudget = HistoryBudget(
                maxEntries = getInt(worldSection, "history.max-entries", getInt(defaultSection, "history.max-entries", 100)),
                maxBytes = getInt(worldSection, "history.max-bytes", getInt(defaultSection, "history.max-bytes", 64 * 1024 * 1024)),
            ),
            largeEditMultiplier = getInt(worldSection, "large-edit-multiplier", getInt(defaultSection, "large-edit-multiplier", 4))
                .coerceAtLeast(1),
            editRegion = resolveRegion(worldSection) ?: resolveRegion(defaultSection),
        )
    }

    fun historyBudget(world: World): HistoryBudget = worldPolicy(world).historyBudget

    fun effectiveWorldPolicy(player: Player, world: World): AxionWorldPolicy {
        val base = worldPolicy(world)
        return if (player.hasPermission(Permissions.LARGE_EDITS)) {
            base.scaledForLargeEdits()
        } else {
            base
        }
    }

    fun primaryTool(operations: List<AxionRemoteOperation>): AxionToolKind? {
        val hasClone = operations.any { it is CloneRegionRequest }
        val hasClear = operations.any { it is ClearRegionRequest }
        val hasStack = operations.any { it is StackRegionRequest }
        val hasSmear = operations.any { it is SmearRegionRequest }
        val hasExtrude = operations.any { it is ExtrudeRequest }
        val hasPlace = operations.any { it is PlaceBlocksRequest }
        val hasDeleteEntities = operations.any { it is DeleteEntitiesRequest }
        return when {
            hasClone && hasClear && operations.all {
                it is CloneRegionRequest || it is ClearRegionRequest || it is MoveEntitiesRequest
            } -> AxionToolKind.MOVE
            hasClone || operations.any { it is CloneEntitiesRequest } -> AxionToolKind.CLONE
            hasStack -> AxionToolKind.STACK
            hasSmear -> AxionToolKind.SMEAR
            hasExtrude -> AxionToolKind.EXTRUDE
            hasDeleteEntities || hasClear -> AxionToolKind.ERASE
            hasPlace -> null
            else -> null
        }
    }

    private fun requiredTools(operations: List<AxionRemoteOperation>): Set<AxionToolKind> {
        primaryTool(operations)?.let { return setOf(it) }
        return operations.mapNotNullTo(linkedSetOf()) { operation ->
            when (operation) {
                is ClearRegionRequest -> AxionToolKind.ERASE
                is CloneRegionRequest -> AxionToolKind.CLONE
                is CloneEntitiesRequest -> AxionToolKind.CLONE
                is DeleteEntitiesRequest -> AxionToolKind.ERASE
                is MoveEntitiesRequest -> null
                is StackRegionRequest -> AxionToolKind.STACK
                is SmearRegionRequest -> AxionToolKind.SMEAR
                is ExtrudeRequest -> AxionToolKind.EXTRUDE
                is PlaceBlocksRequest -> null
            }
        }
    }

    private fun validateTouchedPositions(
        player: Player,
        world: World,
        operationName: String,
        tool: AxionToolKind?,
        usesSymmetry: Boolean,
        touchedPositions: Set<IntVector3>,
        timing: AxionTimingContext,
    ): AxionRejection? {
        if (touchedPositions.isEmpty()) {
            return null
        }

        val regionAccessPolicy = CompositeRegionAccessPolicy(
            ConfigRegionAccessPolicy(::worldPolicy),
            plugin.regionAccessPolicy,
        )
        val decision = regionAccessPolicy.validate(
            context = OperationPolicyContext(
                player = player,
                world = world,
                operationName = operationName,
                tool = tool,
                usesSymmetry = usesSymmetry,
                timing = timing,
            ),
            touchedPositions = touchedPositions,
        )
        return if (decision.allowed) {
            null
        } else {
            AxionRejection(
                code = decision.code,
                source = decision.source,
                message = decision.reason ?: "Edit is denied by server policy",
                blockedPosition = decision.blockedPosition,
            )
        }
    }

    private fun permissionFor(tool: AxionToolKind): String {
        return when (tool) {
            AxionToolKind.ERASE -> Permissions.TOOL_ERASE
            AxionToolKind.CLONE -> Permissions.TOOL_CLONE
            AxionToolKind.MOVE -> Permissions.TOOL_MOVE
            AxionToolKind.STACK -> Permissions.TOOL_STACK
            AxionToolKind.SMEAR -> Permissions.TOOL_SMEAR
            AxionToolKind.EXTRUDE -> Permissions.TOOL_EXTRUDE
        }
    }

    private fun resolveRegion(section: ConfigurationSection?): AxionEditRegion? {
        val regionSection = section?.getConfigurationSection("edit-region") ?: return null
        if (!regionSection.getBoolean("enabled", false)) {
            return null
        }
        val minX = regionSection.getInt("min.x")
        val minY = regionSection.getInt("min.y")
        val minZ = regionSection.getInt("min.z")
        val maxX = regionSection.getInt("max.x")
        val maxY = regionSection.getInt("max.y")
        val maxZ = regionSection.getInt("max.z")
        return AxionEditRegion(
            min = IntVector3(minOf(minX, maxX), minOf(minY, maxY), minOf(minZ, maxZ)),
            max = IntVector3(maxOf(minX, maxX), maxOf(minY, maxY), maxOf(minZ, maxZ)),
        )
    }

    private fun getBoolean(section: ConfigurationSection?, path: String, defaultValue: Boolean): Boolean {
        return section?.getBoolean(path, defaultValue) ?: defaultValue
    }

    private fun getInt(section: ConfigurationSection?, path: String, defaultValue: Int): Int {
        return section?.getInt(path, defaultValue) ?: defaultValue
    }

    object Permissions {
        const val USE: String = "axion.use"
        const val TOOL_CLONE: String = "axion.tool.clone"
        const val TOOL_MOVE: String = "axion.tool.move"
        const val TOOL_STACK: String = "axion.tool.stack"
        const val TOOL_SMEAR: String = "axion.tool.smear"
        const val TOOL_EXTRUDE: String = "axion.tool.extrude"
        const val TOOL_ERASE: String = "axion.tool.erase"
        const val SYMMETRY: String = "axion.symmetry"
        const val UNDO: String = "axion.undo"
        const val LARGE_EDITS: String = "axion.largeedits"
    }
}

private fun AxionToolKind.configKey(): String {
    return name.lowercase().replace('_', '-')
}
