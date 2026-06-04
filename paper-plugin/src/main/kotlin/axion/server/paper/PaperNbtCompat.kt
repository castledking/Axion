package axion.server.paper

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser

object PaperNbtCompat {
    private val parseMethods = listOf("parseCompoundFully", "parseCompound", "parseTag")

    fun parseCompound(payload: String): CompoundTag {
        val method = parseMethods
            .asSequence()
            .flatMap { name -> TagParser::class.java.methods.asSequence().filter { it.name == name } }
            .firstOrNull { method ->
                method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java &&
                    Tag::class.java.isAssignableFrom(method.returnType)
            } ?: error("No compatible TagParser compound parser found")

        return method.invoke(null, payload) as CompoundTag
    }
}
