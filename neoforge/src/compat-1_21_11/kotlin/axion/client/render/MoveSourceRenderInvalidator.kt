package axion.client.render

import net.minecraft.client.multiplayer.ClientLevel

internal object MoveSourceRenderInvalidator {
    fun invalidate(
        world: ClientLevel,
        sections: Set<MoveSourceRenderState.SectionCoordinate>,
    ) {
        sections.forEach { section ->
            world.scheduleChunkRenders(
                section.x - 1,
                section.y - 1,
                section.z - 1,
                section.x + 1,
                section.y + 1,
                section.z + 1,
            )
        }
    }
}
