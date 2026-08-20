package net.minecraft.nbt

object StringNbtReader {
    fun readCompound(snbt: String): NbtCompound {
        return TagParser.parseCompoundFully(snbt)
    }
}
