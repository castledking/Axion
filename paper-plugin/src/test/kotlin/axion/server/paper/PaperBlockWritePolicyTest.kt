package axion.server.paper

import kotlin.test.Test
import kotlin.test.assertFalse

class PaperBlockWritePolicyTest {
    @Test
    fun `all Paper edit writes disable Bukkit physics`() {
        assertFalse(PaperBlockWritePolicy.APPLY_PHYSICS)
    }
}
