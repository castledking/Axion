package axion.common.operation

interface OperationDispatcher {
    fun dispatch(operation: EditOperation)
}
