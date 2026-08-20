package axion.client.render

import axion.client.render.gpu.PreviewOcclusionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewOcclusionPolicyTest {
    @Test
    fun `non-occluding fluid neighbor leaves the touching block face visible`() {
        assertTrue(
            PreviewOcclusionPolicy.isFaceExposed("water") { neighbor ->
                neighbor == "solid"
            },
        )
    }

    @Test
    fun `opaque neighbor hides the touching block face`() {
        assertFalse(
            PreviewOcclusionPolicy.isFaceExposed("solid") { neighbor ->
                neighbor == "solid"
            },
        )
    }

    @Test
    fun `missing neighbor leaves the block face visible`() {
        assertTrue(
            PreviewOcclusionPolicy.isFaceExposed<String>(null) { true },
        )
    }

    @Test
    fun `same translucent block remains visible to vanilla side culling`() {
        assertFalse(
            PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(
                rendering = "glass",
                neighbor = "glass",
                isSameOcclusionGroup = String::equals,
                isOpaqueFullCube = { false },
            ),
        )
    }

    @Test
    fun `different translucent block is exposed as air`() {
        assertTrue(
            PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(
                rendering = "glass",
                neighbor = "water",
                isSameOcclusionGroup = String::equals,
                isOpaqueFullCube = { false },
            ),
        )
    }

    @Test
    fun `missing preview neighbor is exposed as air`() {
        assertTrue(
            PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(
                rendering = "glass",
                neighbor = null,
                isSameOcclusionGroup = String::equals,
                isOpaqueFullCube = { true },
            ),
        )
    }

    @Test
    fun `only cardinally adjacent blocks participate in face occlusion`() {
        assertTrue(
            PreviewOcclusionPolicy.isDirectNeighbor(
                x = 11,
                y = 20,
                z = 30,
                originX = 10,
                originY = 20,
                originZ = 30,
            ),
        )
        assertFalse(
            PreviewOcclusionPolicy.isDirectNeighbor(
                x = 11,
                y = 21,
                z = 30,
                originX = 10,
                originY = 20,
                originZ = 30,
            ),
        )
    }

    @Test
    fun `surface candidates retain full occupancy when counting exposed faces`() {
        val full = buildSet {
            for (x in 0..2) for (y in 0..2) for (z in 0..2) add(Cell(x, y, z))
        }
        val candidates = full.filterTo(linkedSetOf()) { cell ->
            NEIGHBORS.any { direction -> cell.offset(direction) !in full }
        }

        assertEquals(27, full.size)
        assertEquals(26, candidates.size)
        assertEquals(54, exposedFaceCount(candidates, full))
        assertEquals(
            60,
            exposedFaceCount(candidates, candidates),
            "discarding the center before face culling invents six inward cavity faces",
        )
    }

    private fun exposedFaceCount(candidates: Set<Cell>, occupancy: Set<Cell>): Int =
        candidates.sumOf { cell -> NEIGHBORS.count { direction -> cell.offset(direction) !in occupancy } }

    private data class Cell(val x: Int, val y: Int, val z: Int) {
        fun offset(direction: Cell): Cell = Cell(x + direction.x, y + direction.y, z + direction.z)
    }

    private companion object {
        val NEIGHBORS = listOf(
            Cell(-1, 0, 0), Cell(1, 0, 0),
            Cell(0, -1, 0), Cell(0, 1, 0),
            Cell(0, 0, -1), Cell(0, 0, 1),
        )
    }
}
