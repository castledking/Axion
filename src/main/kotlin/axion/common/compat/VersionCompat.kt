package axion.common.compat

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier

/**
 * Version compatibility abstraction layer.
 * Provides a common interface for operations that differ between Minecraft versions.
 */
interface VersionCompat {
    companion object {
        lateinit var INSTANCE: VersionCompat
            private set

        fun initialize(impl: VersionCompat) {
            INSTANCE = impl
        }
    }

    // Registry operations
    fun getBlock(id: Identifier): Block?
    fun getItem(id: Identifier): Item?
    fun getBlockId(block: Block): Identifier
    fun getItemId(item: Item): Identifier
    fun getAllBlocks(): Collection<Block>
    fun getAllItems(): Collection<Item>

    // ResourceLocation/Identifier operations  
    fun parseIdentifier(id: String): Identifier
    fun identifierOf(namespace: String, path: String): Identifier

    // BlockState operations
    fun blockStateToString(state: BlockState): String
    fun stringToBlockState(str: String): BlockState?

    // NBT serialization (differed between 1.20 and 1.21)
    fun itemStackToNbt(stack: ItemStack): NbtCompound
    fun nbtToItemStack(nbt: NbtCompound): ItemStack

    // Keybinding handling (1.21.7 and earlier need special handling for conflicting keys)
    fun shouldUseNonConsumingKeybind(): Boolean

    // Rendering helpers (for block tessellation)
    fun getBlockRenderManager(client: Any): Any
    fun getBlockRenderType(state: BlockState): Any
    fun getRenderingSeed(state: BlockState, pos: Any): Long
    fun matrixStackPush(stack: Any): Any
    fun matrixStackPop(stack: Any)
    fun blockRenderManagerGetModel(manager: Any, state: BlockState): Any
    fun blockRenderManagerRenderBlock(manager: Any, state: BlockState, pos: Any, world: Any, matrixStack: Any, consumer: Any, checkSides: Boolean, parts: List<Any>): Boolean
    fun blockRenderManagerRenderFluid(manager: Any, pos: Any, world: Any, consumer: Any, state: BlockState, fluidState: Any): Boolean

    // Entity API helpers
    fun entityIsRemoved(entity: Any): Boolean
    fun entityGetVehicle(entity: Any): Any?
    fun entityGetUuid(entity: Any): java.util.UUID
    fun entityGetX(entity: Any): Double
    fun entityGetY(entity: Any): Double
    fun entityGetZ(entity: Any): Double
    fun entityGetYaw(entity: Any): Float
    fun entityGetPitch(entity: Any): Float
    fun entityGetPassengerList(entity: Any): List<Any>
    fun entitySetUuid(entity: Any, uuid: java.util.UUID)
    fun entityRefreshPositionAndAngles(entity: Any)
    fun entityUpdatePassengerPosition(entity: Any, passenger: Any)
    fun entityTypeLoadEntityWithPassengers(tag: NbtCompound, world: Any, spawnReason: Any, entityProcessor: (Any) -> Any): Any?
    fun worldSpawnNewEntityAndPassengers(world: Any, entity: Any): Boolean
    fun worldGetOtherEntities(world: Any, entity: Any, box: Any): List<Any>

    // MinecraftClient API helpers
    fun clientGetServer(client: Any): Any?
    fun clientGetWorldRegistryKey(client: Any): Any?
    fun serverExecute(server: Any, task: Runnable)
    fun serverGetWorld(server: Any, registryKey: Any): Any?
    fun playerSendMessage(player: Any, message: Any, overlay: Boolean)

    // Direction/BlockState API helpers
    fun directionGetVector(direction: Any): Any
    fun blockStateStringify(state: BlockState): String

    // Registry/BlockArgumentParser API helpers
    fun worldGetRegistryManager(world: Any): Any
    fun blockArgumentParserBlock(registry: Any, state: String): Any

    // ItemStack codec helpers for hotbar save/load
    fun itemStackEncode(registryManager: Any, stack: Any): ByteArray?
    fun itemStackDecode(registryManager: Any, bytes: ByteArray): Any?
}
