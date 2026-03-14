package axion.common.operation

interface OperationValidator {
    fun validate(operation: EditOperation): Boolean
}
