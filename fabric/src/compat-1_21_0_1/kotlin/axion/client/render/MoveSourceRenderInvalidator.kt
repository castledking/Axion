package axion.client.render

import net.minecraft.client.world.ClientWorld

internal object MoveSourceRenderInvalidator {
    fun invalidate(
        world: ClientWorld,
        sections: Set<MoveSourceRenderState.SectionCoordinate>,
    ) {
        sections.forEach { section ->
            world.scheduleBlockRenders(section.x, section.y, section.z)
        }
    }
}
