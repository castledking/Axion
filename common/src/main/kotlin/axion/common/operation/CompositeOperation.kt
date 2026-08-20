package axion.common.operation

data class CompositeOperation(
    val operations: List<EditOperation>,
) : EditOperation {
    override val kind: String = "composite"
}
