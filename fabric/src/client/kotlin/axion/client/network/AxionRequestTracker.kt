package axion.client.network

object AxionRequestTracker {
    sealed interface RequestKind {
        data object Operation : RequestKind
        data class Undo(val transactionId: Long) : RequestKind
        data class Redo(val transactionId: Long) : RequestKind
    }

    private val pending = mutableMapOf<Long, RequestKind>()

    fun register(requestId: Long, kind: RequestKind) {
        pending[requestId] = kind
    }

    fun complete(requestId: Long): RequestKind? = pending.remove(requestId)

    fun clear() {
        pending.clear()
    }
}
