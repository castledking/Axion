package axion.client.symmetry

import axion.client.network.BlockWrite
import axion.client.network.WritePlan
import axion.common.model.SymmetryConfig

object SymmetryWritePlanExpander {
    fun expand(plan: WritePlan, config: SymmetryConfig?): WritePlan {
        if (!ActiveSymmetryConfig.hasDerivedTransforms(config)) {
            return plan
        }

        val resolvedConfig = config ?: return plan
        val writesByPosition = linkedMapOf<net.minecraft.util.math.BlockPos, BlockWrite>()
        SymmetryTransformService.activeTransforms(resolvedConfig).forEach { transform ->
            plan.writes.forEach { write ->
                val transformedPos = SymmetryTransformService.transformBlock(
                    write.pos,
                    resolvedConfig.anchor.position,
                    transform,
                )
                writesByPosition[transformedPos] = BlockWrite(
                    pos = transformedPos,
                    state = write.state,
                    blockEntityData = write.blockEntityData?.copy(),
                )
            }
        }

        return WritePlan(
            label = plan.label,
            writes = writesByPosition.values.toList(),
        )
    }
}
