package axion.server.paper

import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3

data class AxionRejection(
    val code: AxionResultCode,
    val source: AxionResultSource,
    val message: String,
    val blockedPosition: IntVector3? = null,
)
