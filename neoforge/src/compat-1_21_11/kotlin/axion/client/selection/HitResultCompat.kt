package axion.client.selection

import net.minecraft.world.phys.HitResult

fun isBlockHit(hit: HitResult): Boolean = hit.type == HitResult.Type.BLOCK
