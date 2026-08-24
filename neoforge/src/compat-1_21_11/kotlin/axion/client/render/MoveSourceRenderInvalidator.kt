package axion.client.render

import net.minecraft.client.multiplayer.ClientLevel

internal object MoveSourceRenderInvalidator {
    fun invalidate(
        world: ClientLevel,
        sections: Set<MoveSourceRenderState.SectionCoordinate>,
    ) {
        sections.forEach { section ->
            (world as net.minecraft.client.multiplayer.ClientLevel).setSectionDirtyWithNeighbors(
                section.x - 1,
                section.y - 1,
                section.z - 1,
            )
            (world as net.minecraft.client.multiplayer.ClientLevel).setSectionDirtyWithNeighbors(
                section.x + 1,
                section.y + 1,
                section.z + 1,
            )
        }
    }
}
