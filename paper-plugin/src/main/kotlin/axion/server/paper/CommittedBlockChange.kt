package axion.server.paper

import axion.protocol.CommittedBlockChangePayload

data class CommittedBlockChange(
    val payload: CommittedBlockChangePayload,
)
