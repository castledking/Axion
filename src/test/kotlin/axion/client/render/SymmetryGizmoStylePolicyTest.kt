package axion.client.render

import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryGizmoStylePolicyTest {
    @Test
    fun `configured anchor remains gray until a geometric symmetry is enabled`() {
        assertEquals(
            0xFF9E9E9E.toInt(),
            SymmetryGizmoStylePolicy.color(
                rotationalEnabled = false,
                mirrorEnabled = false,
            ),
        )
    }

    @Test
    fun `rotation or mirror makes the anchor yellow`() {
        assertEquals(
            0xFFF2C94C.toInt(),
            SymmetryGizmoStylePolicy.color(
                rotationalEnabled = true,
                mirrorEnabled = false,
            ),
        )
        assertEquals(
            0xFFF2C94C.toInt(),
            SymmetryGizmoStylePolicy.color(
                rotationalEnabled = false,
                mirrorEnabled = true,
            ),
        )
        assertEquals(
            0xFFF2C94C.toInt(),
            SymmetryGizmoStylePolicy.color(
                rotationalEnabled = true,
                mirrorEnabled = true,
            ),
        )
    }
}
