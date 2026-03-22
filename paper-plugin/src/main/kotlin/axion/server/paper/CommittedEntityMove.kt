package axion.server.paper

import org.bukkit.Location
import java.util.UUID

data class CommittedEntityMove(
    val entityId: UUID,
    val from: Location,
    val to: Location,
)
