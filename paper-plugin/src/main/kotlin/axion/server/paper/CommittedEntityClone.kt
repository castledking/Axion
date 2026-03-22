package axion.server.paper

import org.bukkit.Location
import java.util.UUID

data class CommittedEntityClone(
    val entityId: UUID,
    val entityData: String,
    val spawnLocation: Location,
)
