package axion.server.paper

import axion.protocol.AxionInteractionOrigin
import axion.protocol.ClearRegionRequest
import axion.protocol.CloneRegionRequest
import axion.protocol.IntVector3
import axion.protocol.PlaceBlocksRequest
import axion.protocol.PlacedBlockPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaperInteractionEventPolicyTest {
    @Test
    fun `ordinary Axion edits do not emit Bukkit interaction events`() {
        assertEquals(
            emptyList(),
            PaperInteractionEventPolicy.eventKinds(
                origin = AxionInteractionOrigin.NONE,
                stateChanged = true,
                oldIsAir = false,
                newIsAir = true,
            ),
        )
    }

    @Test
    fun `infinite reach removals emit a break event`() {
        assertEquals(
            listOf(PaperInteractionEventKind.BREAK),
            PaperInteractionEventPolicy.eventKinds(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                stateChanged = true,
                oldIsAir = false,
                newIsAir = true,
            ),
        )
    }

    @Test
    fun `infinite reach placements emit a place event`() {
        assertEquals(
            listOf(PaperInteractionEventKind.PLACE),
            PaperInteractionEventPolicy.eventKinds(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                stateChanged = true,
                oldIsAir = true,
                newIsAir = false,
            ),
        )
    }

    @Test
    fun `infinite reach replacements emit break before place`() {
        assertEquals(
            listOf(PaperInteractionEventKind.BREAK, PaperInteractionEventKind.PLACE),
            PaperInteractionEventPolicy.eventKinds(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                stateChanged = true,
                oldIsAir = false,
                newIsAir = false,
            ),
        )
    }

    @Test
    fun `no-op writes emit no Bukkit events`() {
        assertEquals(
            emptyList(),
            PaperInteractionEventPolicy.eventKinds(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                stateChanged = false,
                oldIsAir = false,
                newIsAir = false,
            ),
        )
    }

    @Test
    fun `infinite reach metadata permits single-cell interactions`() {
        assertNull(
            PaperInteractionEventPolicy.validateOperations(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                operations = listOf(
                    ClearRegionRequest(IntVector3(1, 2, 3), IntVector3(1, 2, 3)),
                    PlaceBlocksRequest(
                        listOf(PlacedBlockPayload(IntVector3(4, 5, 6), "minecraft:stone", null)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `infinite reach metadata cannot turn bulk edits into event storms`() {
        assertEquals(
            PaperInteractionRequestProblem.BULK_CLEAR,
            PaperInteractionEventPolicy.validateOperations(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                operations = listOf(
                    ClearRegionRequest(IntVector3(1, 2, 3), IntVector3(2, 2, 3)),
                ),
            ),
        )
        assertEquals(
            PaperInteractionRequestProblem.UNSUPPORTED_OPERATION,
            PaperInteractionEventPolicy.validateOperations(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                operations = listOf(
                    CloneRegionRequest(
                        sourceMin = IntVector3(1, 2, 3),
                        sourceMax = IntVector3(1, 2, 3),
                        destinationOrigin = IntVector3(4, 5, 6),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `non-history interaction-shaped batches are inferred for markerless clients`() {
        val route = PaperInteractionEventPolicy.routeBatch(
            origin = AxionInteractionOrigin.NONE,
            recordHistory = false,
            operations = listOf(
                ClearRegionRequest(IntVector3(1, 2, 3), IntVector3(1, 2, 3)),
                PlaceBlocksRequest(
                    listOf(PlacedBlockPayload(IntVector3(4, 5, 6), "minecraft:stone", null)),
                ),
            ),
        )

        assertTrue(route.eventEligible)
        assertTrue(route.inferred)
        assertEquals(2, route.writeCount)
        assertNull(route.problem)
    }

    @Test
    fun `history edits and bulk clears are not inferred as player interactions`() {
        val historical = PaperInteractionEventPolicy.routeBatch(
            origin = AxionInteractionOrigin.NONE,
            recordHistory = true,
            operations = listOf(
                PlaceBlocksRequest(
                    listOf(PlacedBlockPayload(IntVector3(4, 5, 6), "minecraft:stone", null)),
                ),
            ),
        )
        val bulk = PaperInteractionEventPolicy.routeBatch(
            origin = AxionInteractionOrigin.NONE,
            recordHistory = false,
            operations = listOf(
                ClearRegionRequest(IntVector3(1, 2, 3), IntVector3(2, 2, 3)),
            ),
        )

        assertFalse(historical.eventEligible)
        assertNull(historical.problem)
        assertFalse(bulk.eventEligible)
        assertNull(bulk.problem)
    }

    @Test
    fun `tagged and inferred interaction batches are capped before event dispatch`() {
        val placements = List(PaperInteractionEventPolicy.MAX_INTERACTION_WRITES + 1) { index ->
            PlacedBlockPayload(IntVector3(index, 64, 0), "minecraft:stone", null)
        }

        assertNull(
            PaperInteractionEventPolicy.routeBatch(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                recordHistory = false,
                operations = listOf(
                    PlaceBlocksRequest(placements.take(PaperInteractionEventPolicy.MAX_INTERACTION_WRITES)),
                ),
            ).problem,
        )
        assertEquals(
            PaperInteractionRequestProblem.TOO_MANY_WRITES,
            PaperInteractionEventPolicy.routeBatch(
                origin = AxionInteractionOrigin.INFINITE_REACH,
                recordHistory = false,
                operations = listOf(PlaceBlocksRequest(placements)),
            ).problem,
        )
        assertEquals(
            PaperInteractionRequestProblem.TOO_MANY_WRITES,
            PaperInteractionEventPolicy.routeBatch(
                origin = AxionInteractionOrigin.NONE,
                recordHistory = false,
                operations = listOf(PlaceBlocksRequest(placements)),
            ).problem,
        )
    }

    @Test
    fun `only writes beyond the vanilla reach receive Bukkit interaction events`() {
        val route = PaperInteractionEventPolicy.routeBatch(
            origin = AxionInteractionOrigin.INFINITE_REACH,
            recordHistory = false,
            operations = listOf(
                PlaceBlocksRequest(
                    listOf(PlacedBlockPayload(IntVector3(7, 1, 0), "minecraft:stone", null)),
                ),
            ),
        )

        assertEquals(
            AxionInteractionOrigin.NONE,
            PaperInteractionEventPolicy.originForWrite(
                route = route,
                eyeX = 0.5,
                eyeY = 1.62,
                eyeZ = 0.5,
                pos = IntVector3(6, 1, 0),
            ),
        )
        assertEquals(
            AxionInteractionOrigin.INFINITE_REACH,
            PaperInteractionEventPolicy.originForWrite(
                route = route,
                eyeX = 0.5,
                eyeY = 1.62,
                eyeZ = 0.5,
                pos = IntVector3(7, 1, 0),
            ),
        )
    }
}
