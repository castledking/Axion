package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import kotlin.math.max
import kotlin.math.min

class WorldGuardRegionAccessPolicy private constructor(
    private val integration: Integration,
) : RegionAccessPolicy {
    override fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        val timing = context.timing
        return if (timing != null) {
            timing.measureWorldGuard { validateInternal(context, touchedPositions) }
        } else {
            validateInternal(context, touchedPositions)
        }
    }

    private fun validateInternal(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        if (touchedPositions.isEmpty()) {
            return PolicyDecision(allowed = true)
        }
        if (integration.hasBypass(context.player, context.world)) {
            return PolicyDecision(allowed = true)
        }

        val groupedByChunk = touchedPositions.groupBy(ChunkKey::of)
        groupedByChunk.values.forEach { chunkPositions ->
            if (shouldUseOverlapProbe(chunkPositions)) {
                val bounds = boundsOf(chunkPositions)
                if (!integration.hasRegionOverlap(context.world, bounds)) {
                    return@forEach
                }
            }

            val firstDenied = chunkPositions.firstOrNull { pos ->
                !integration.canBuild(context.player, context.world, pos)
            }
            if (firstDenied != null) {
                return PolicyDecision(
                    allowed = false,
                    code = AxionResultCode.PROTECTED_REGION,
                    source = AxionResultSource.WORLDGUARD,
                    reason = "Blocked by WorldGuard at ${firstDenied.x}, ${firstDenied.y}, ${firstDenied.z}",
                    blockedPosition = firstDenied,
                )
            }
        }

        return PolicyDecision(allowed = true)
    }

    private fun shouldUseOverlapProbe(positions: List<IntVector3>): Boolean {
        if (positions.size < DENSE_MIN_BLOCKS) {
            return false
        }

        val bounds = boundsOf(positions)
        val volume = (bounds.max.x - bounds.min.x + 1L) *
            (bounds.max.y - bounds.min.y + 1L) *
            (bounds.max.z - bounds.min.z + 1L)
        if (volume <= 0L) {
            return false
        }

        return positions.size.toDouble() / volume.toDouble() >= DENSE_VOLUME_RATIO
    }

    private fun boundsOf(positions: List<IntVector3>): CuboidBounds {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        positions.forEach { pos ->
            minX = min(minX, pos.x)
            minY = min(minY, pos.y)
            minZ = min(minZ, pos.z)
            maxX = max(maxX, pos.x)
            maxY = max(maxY, pos.y)
            maxZ = max(maxZ, pos.z)
        }
        return CuboidBounds(
            min = IntVector3(minX, minY, minZ),
            max = IntVector3(maxX, maxY, maxZ),
        )
    }

    companion object {
        fun tryCreate(plugin: Plugin): WorldGuardRegionAccessPolicy? {
            return try {
                WorldGuardRegionAccessPolicy(Integration())
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: LinkageError) {
                null
            }
        }

        private const val DENSE_MIN_BLOCKS: Int = 32
        private const val DENSE_VOLUME_RATIO: Double = 0.35
    }

    private class Integration {
        private val worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin")
        private val worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard")
        private val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
        private val blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3")
        private val protectedCuboidRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")

        private val worldGuardPlugin = worldGuardPluginClass.getMethod("inst").invoke(null)
        private val wrapPlayerMethod = worldGuardPluginClass.getMethod("wrapPlayer", Player::class.java)
        private val adaptWorldMethod = bukkitAdapterClass.getMethod("adapt", World::class.java)

        private val regionContainer = run {
            val worldGuard = worldGuardClass.getMethod("getInstance").invoke(null)
            val platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard)
            platform.javaClass.getMethod("getRegionContainer").invoke(platform)
        }

        private val createQueryMethod = regionContainer.javaClass.getMethod("createQuery")
        private val query = createQueryMethod.invoke(regionContainer)
        private val getRegionManagerMethod = regionContainer.javaClass.getMethod("get", adaptWorldMethod.returnType)
        private val adaptLocationMethod = bukkitAdapterClass.getMethod("adapt", Location::class.java)
        private val testBuildMethod = query.javaClass.methods.firstOrNull { method ->
            method.name == "testBuild" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0].name == "com.sk89q.worldedit.util.Location"
        } ?: error("WorldGuard RegionQuery#testBuild(Location, LocalPlayer) not found")
        private val blockVectorAtMethod = blockVector3Class.getMethod(
            "at",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        private val protectedCuboidConstructor = protectedCuboidRegionClass.getConstructor(
            String::class.java,
            blockVector3Class,
            blockVector3Class,
        )
        private val getApplicableRegionsMethod = Class
            .forName("com.sk89q.worldguard.protection.managers.RegionManager")
            .methods
            .firstOrNull { method ->
                method.name == "getApplicableRegions" && method.parameterCount == 1
            } ?: error("WorldGuard RegionManager#getApplicableRegions(ProtectedRegion) not found")

        fun canBuild(player: Player, world: World, pos: IntVector3): Boolean {
            val localPlayer = wrapPlayerMethod.invoke(worldGuardPlugin, player)
            val location = adaptLocationMethod.invoke(
                null,
                Location(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()),
            )
            return testBuildMethod.invoke(query, location, localPlayer) as Boolean
        }

        fun hasBypass(player: Player, world: World): Boolean {
            return player.hasPermission("worldguard.region.bypass.${world.name}")
        }

        fun hasRegionOverlap(world: World, bounds: CuboidBounds): Boolean {
            val worldEditWorld = adaptWorldMethod.invoke(null, world)
            val regionManager = getRegionManagerMethod.invoke(regionContainer, worldEditWorld) ?: return true
            val min = blockVectorAtMethod.invoke(null, bounds.min.x, bounds.min.y, bounds.min.z)
            val max = blockVectorAtMethod.invoke(null, bounds.max.x, bounds.max.y, bounds.max.z)
            val probeRegion = protectedCuboidConstructor.newInstance("axion-probe", min, max)
            val applicable = getApplicableRegionsMethod.invoke(regionManager, probeRegion)
            val iterator = applicable.javaClass.getMethod("iterator").invoke(applicable) as Iterator<*>
            return iterator.hasNext()
        }
    }

    private data class ChunkKey(
        val x: Int,
        val z: Int,
    ) {
        companion object {
            fun of(pos: IntVector3): ChunkKey = ChunkKey(pos.x shr 4, pos.z shr 4)
        }
    }

    private data class CuboidBounds(
        val min: IntVector3,
        val max: IntVector3,
    )
}
