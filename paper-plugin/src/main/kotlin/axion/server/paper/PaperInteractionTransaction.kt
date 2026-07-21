package axion.server.paper

internal class PaperInteractionDeniedException(
    val rejection: AxionRejection,
) : RuntimeException(rejection.message, null, false, false)

internal object PaperInteractionTransaction {
    sealed interface Result {
        data object Applied : Result

        data class Denied(
            val rejection: AxionRejection,
        ) : Result
    }

    fun run(
        apply: () -> Unit,
        rollback: () -> Unit,
    ): Result {
        return try {
            apply()
            Result.Applied
        } catch (denied: PaperInteractionDeniedException) {
            rollback()
            Result.Denied(denied.rejection)
        }
    }
}
