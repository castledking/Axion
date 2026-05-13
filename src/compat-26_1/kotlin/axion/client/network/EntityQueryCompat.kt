package axion.client.network

import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import java.util.function.Predicate

fun <T : Entity> ServerWorld.getEntitiesByClass(
    clazz: Class<T>,
    box: Box,
    predicate: Predicate<in T>,
): List<T> =
    getEntitiesOfClass(clazz, box).filter { predicate.test(it) }
