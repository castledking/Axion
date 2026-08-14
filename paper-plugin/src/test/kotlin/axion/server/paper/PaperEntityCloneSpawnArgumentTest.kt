package axion.server.paper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Pins the reflective shape of `EntityType.loadEntityRecursive`'s spawn argument.
 *
 * Getting this wrong does not throw anywhere the player can see it: the caller
 * wraps the load in `runCatching { }.getOrNull()`, so a bad argument just means
 * no entity is spawned. That is exactly how entity cloning silently stopped
 * working on 26.2 Paper when the parameter changed from the `EntitySpawnReason`
 * enum to the `EntitySpawnRequest` record that wraps it.
 */
class PaperEntityCloneSpawnArgumentTest {
    /** Stands in for `EntitySpawnReason` (26.1 and earlier). */
    enum class FakeSpawnReason { NATURAL, COMMAND }

    /** Stands in for 26.2's `EntitySpawnRequest(reason, ignoreChecks)` record. */
    data class FakeSpawnRequest(val reason: FakeSpawnReason, val ignoreChecks: Boolean)

    /** A shape neither version uses, to prove we fail closed rather than guess. */
    class FakeUnknownRequest(val label: String)

    @Test
    fun `an enum parameter resolves to its COMMAND constant`() {
        val argument = PaperEntityCloneService.commandSpawnArgument(FakeSpawnReason::class.java)

        assertEquals(FakeSpawnReason.COMMAND, argument)
    }

    @Test
    fun `a wrapper record is constructed around COMMAND without ignoring spawn checks`() {
        val argument = PaperEntityCloneService.commandSpawnArgument(FakeSpawnRequest::class.java)

        val request = assertIs<FakeSpawnRequest>(argument)
        assertEquals(FakeSpawnReason.COMMAND, request.reason)
        // Vanilla's own /summon passes false here; ignoring checks would let
        // clones land somewhere the server would normally refuse.
        assertEquals(false, request.ignoreChecks)
    }

    @Test
    fun `an unrecognised parameter shape yields null instead of a wrong argument`() {
        assertNull(PaperEntityCloneService.commandSpawnArgument(FakeUnknownRequest::class.java))
    }
}
