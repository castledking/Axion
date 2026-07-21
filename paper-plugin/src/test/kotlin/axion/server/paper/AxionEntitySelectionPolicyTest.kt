package axion.server.paper

import axion.protocol.EntitySelectionMask
import axion.protocol.IntVector3
import axion.protocol.MoveEntitiesRequest
import axion.protocol.PlacementMirrorAxisPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AxionEntitySelectionPolicyTest {
    private val move = MoveEntitiesRequest(
        entitySelection = EntitySelectionMask.sparseOffsets(
            listOf(IntVector3(0, 0, 0), IntVector3(8, 0, 0)),
        ),
        sourceMin = IntVector3(10, 20, 30),
        sourceMax = IntVector3(18, 20, 30),
        destinationOrigin = IntVector3(40, 25, 50),
        rotationQuarterTurns = 0,
        mirrorAxis = PlacementMirrorAxisPayload.NONE,
    )

    @Test
    fun `every entity move requires the move tool`() {
        assertEquals(setOf(AxionToolKind.MOVE), AxionOperationToolPolicy.requiredTools(listOf(move)))
    }

    @Test
    fun `entity policy touches exact sparse source and destination cells but not gaps`() {
        val touched = AxionCommittedDiffBuilder.collectPolicyTouched(listOf(move))

        assertEquals(
            setOf(
                IntVector3(10, 20, 30),
                IntVector3(18, 20, 30),
                IntVector3(40, 25, 50),
                IntVector3(48, 25, 50),
            ),
            touched,
        )
        assertFalse(IntVector3(14, 20, 30) in touched)
        assertFalse(IntVector3(44, 25, 50) in touched)
    }
}
