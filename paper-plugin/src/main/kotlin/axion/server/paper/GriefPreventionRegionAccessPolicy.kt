package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

class GriefPreventionRegionAccessPolicy private constructor(
    private val integration: Integration,
) : RegionAccessPolicy {
    override fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        if (touchedPositions.isEmpty()) {
            return PolicyDecision(allowed = true)
        }

        val denial = integration.firstDenied(context.player, context.world, touchedPositions) ?: return PolicyDecision(allowed = true)
        integration.sendDenial(context.player, denial.message)
        return PolicyDecision(
            allowed = false,
            code = AxionResultCode.PROTECTED_REGION,
            source = AxionResultSource.GRIEF_PREVENTION,
            reason = denial.message,
            blockedPosition = denial.position,
        )
    }

    companion object {
        fun tryCreate(): GriefPreventionRegionAccessPolicy? {
            return try {
                GriefPreventionRegionAccessPolicy(Integration())
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: LinkageError) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: NullPointerException) {
                null
            }
        }
    }

    private class Integration {
        private val plugin: Plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention")
            ?: error("GriefPrevention not present")
        private val pluginClass: Class<*> = plugin.javaClass
        private val dataStore: Any = resolveDataStore(plugin)
        private val allowBuildMethod: Method = pluginClass.methods.firstOrNull { method ->
            method.name == "allowBuild" &&
                method.parameterCount == 3 &&
                Player::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                Location::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                Material::class.java.isAssignableFrom(method.parameterTypes[2])
        } ?: error("GriefPrevention allowBuild(Player, Location, Material) not found")
        private val sendMessageMethod: Method = pluginClass.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 3 &&
                Player::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                ChatColor::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                String::class.java.isAssignableFrom(method.parameterTypes[2])
        } ?: error("GriefPrevention sendMessage(Player, ChatColor, String) not found")

        private val getClaimAtMethod: Method?
        private val getClaimAtArity: Int

        init {
            var resolvedMethod: Method? = null
            var resolvedArity = 0
            for (method in dataStore.javaClass.methods) {
                if (method.name != "getClaimAt") {
                    continue
                }

                val parameterTypes = method.parameterTypes
                if (parameterTypes.isEmpty() || !Location::class.java.isAssignableFrom(parameterTypes[0])) {
                    continue
                }

                when {
                    parameterTypes.size == 4 &&
                        parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                        parameterTypes[2] == Boolean::class.javaPrimitiveType -> {
                        resolvedMethod = method
                        resolvedArity = 4
                        break
                    }

                    parameterTypes.size == 3 &&
                        parameterTypes[1] == Boolean::class.javaPrimitiveType -> {
                        resolvedMethod = method
                        resolvedArity = 3
                    }

                    parameterTypes.size == 2 &&
                        parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                        resolvedMethod == null -> {
                        resolvedMethod = method
                        resolvedArity = 2
                    }
                }
            }

            getClaimAtMethod = resolvedMethod
            getClaimAtArity = resolvedArity
        }

        fun firstDenied(player: Player, world: World, touchedPositions: Set<IntVector3>): ClaimDenial? {
            val claimResults = IdentityHashMap<Any, String?>()
            touchedPositions.forEach { position ->
                val location = Location(world, position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
                val claim = getClaimAt(location, player)
                val denial = if (claim != null) {
                    claimResults.getOrPut(claim) {
                        allowBuildMethod.invoke(plugin, player, location, location.block.type) as String?
                    }
                } else {
                    allowBuildMethod.invoke(plugin, player, location, location.block.type) as String?
                }
                if (denial != null) {
                    return ClaimDenial(position = position, message = denial)
                }
            }
            return null
        }

        fun sendDenial(player: Player, message: String) {
            sendMessageMethod.invoke(null, player, ChatColor.RED, message)
        }

        private fun getClaimAt(location: Location, player: Player): Any? {
            val method = getClaimAtMethod ?: return null
            return when (getClaimAtArity) {
                4 -> method.invoke(dataStore, location, false, false, null)
                    ?: method.invoke(dataStore, location, true, false, null)

                3 -> {
                    val thirdArg = resolveThirdArgument(method.parameterTypes[2], player)
                    method.invoke(dataStore, location, false, thirdArg)
                        ?: method.invoke(dataStore, location, true, thirdArg)
                }

                2 -> method.invoke(dataStore, location, false)
                    ?: method.invoke(dataStore, location, true)

                else -> null
            }
        }

        private fun resolveThirdArgument(parameterType: Class<*>, player: Player): Any? {
            return when {
                Player::class.java.isAssignableFrom(parameterType) -> player
                parameterType.simpleName == "PlayerData" || parameterType.name.endsWith(".PlayerData") -> {
                    runCatching {
                        dataStore.javaClass
                            .getMethod("getPlayerData", java.util.UUID::class.java)
                            .invoke(dataStore, player.uniqueId)
                    }.getOrNull()
                }

                else -> null
            }
        }

        private fun resolveDataStore(plugin: Plugin): Any {
            findField(plugin.javaClass, "dataStore")?.let { field ->
                field.isAccessible = true
                field.get(plugin)?.let { return it }
            }
            throw IllegalStateException("GriefPrevention dataStore field not found")
        }

        private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
            var current: Class<*>? = type
            while (current != null) {
                runCatching { current.getDeclaredField(name) }
                    .getOrNull()
                    ?.let { return it }
                runCatching { current.getField(name) }
                    .getOrNull()
                    ?.let { return it }
                current = current.superclass
            }
            return null
        }
    }

    private data class ClaimDenial(
        val position: IntVector3,
        val message: String,
    )
}
