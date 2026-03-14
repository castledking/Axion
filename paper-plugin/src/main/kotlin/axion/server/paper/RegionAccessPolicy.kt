package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3
import org.bukkit.World
import org.bukkit.entity.Player

data class PolicyDecision(
    val allowed: Boolean,
    val code: AxionResultCode = AxionResultCode.OK,
    val source: AxionResultSource = AxionResultSource.SERVER,
    val reason: String? = null,
    val blockedPosition: IntVector3? = null,
)

data class OperationPolicyContext(
    val player: Player,
    val world: World,
    val operationName: String,
    val tool: AxionToolKind?,
    val usesSymmetry: Boolean,
    val timing: AxionTimingContext? = null,
)

fun interface RegionAccessPolicy {
    fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision
}

object AllowAllRegionAccessPolicy : RegionAccessPolicy {
    override fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        return PolicyDecision(allowed = true)
    }
}

class CompositeRegionAccessPolicy(
    private val delegates: List<RegionAccessPolicy>,
) : RegionAccessPolicy {
    constructor(vararg delegates: RegionAccessPolicy) : this(delegates.toList())

    override fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        delegates.forEach { delegate ->
            val decision = delegate.validate(context, touchedPositions)
            if (!decision.allowed) {
                return decision
            }
        }
        return PolicyDecision(allowed = true)
    }
}

class ConfigRegionAccessPolicy(
    private val policyLookup: (World) -> AxionWorldPolicy,
) : RegionAccessPolicy {
    override fun validate(context: OperationPolicyContext, touchedPositions: Set<IntVector3>): PolicyDecision {
        val region = policyLookup(context.world).editRegion ?: return PolicyDecision(allowed = true)
        if (touchedPositions.all(region::contains)) {
            return PolicyDecision(allowed = true)
        }
        return PolicyDecision(
            allowed = false,
            code = AxionResultCode.OUTSIDE_ALLOWED_CUBOID,
            source = AxionResultSource.CONFIG_CUBOID,
            reason = "Edit is outside the allowed region for world ${context.world.name}",
        )
    }
}
