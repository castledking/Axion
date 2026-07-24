package axion.client.selection

import net.minecraft.util.hit.HitResult

fun isBlockHit(hit: HitResult): Boolean =
    hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK
