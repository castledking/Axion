package axion.client.compat

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

// Yarn-named aliases kept for shared call-site compatibility.
val Entity.rotationVecClient: Vec3 get() = getViewVector(1.0f)
