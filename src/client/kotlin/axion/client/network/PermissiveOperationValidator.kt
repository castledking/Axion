package axion.client.network

import axion.common.operation.EditOperation
import axion.common.operation.OperationValidator

class PermissiveOperationValidator : OperationValidator {
    override fun validate(operation: EditOperation): Boolean = true
}
