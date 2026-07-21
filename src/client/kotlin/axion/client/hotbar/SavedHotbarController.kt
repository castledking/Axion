package axion.client.hotbar

import axion.client.compat.LitematicaCompat
import axion.client.config.AxionClientConfig
import axion.client.config.SavedHotbarConfig
import axion.client.input.AxionModifierKeys
import axion.client.tool.AxionToolSelectionController
import axion.common.compat.VersionCompat
import io.netty.buffer.Unpooled
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.util.math.MathHelper
import java.util.Base64

object SavedHotbarController {
    const val PAGE_SIZE: Int = 9
    private const val HOTBAR_SIZE: Int = 9

    private var pendingSelectionIndex: Int? = null
    private var wasAltDown: Boolean = false
    private val stackCache: MutableMap<String, ItemStack> = linkedMapOf()

    data class DisplayHotbar(
        val index: Int,
        val stacks: List<ItemStack>,
        val selected: Boolean,
        val active: Boolean,
    )

    fun selectedIndex(): Int = pendingSelectionIndex ?: AxionClientConfig.activeSavedHotbarIndex()

    fun activeIndex(): Int = AxionClientConfig.activeSavedHotbarIndex()

    fun selectedPage(): Int = selectedIndex() / PAGE_SIZE

    fun totalPages(): Int {
        val totalHotbarCount = AxionClientConfig.savedHotbars().size
        return maxOf(1, MathHelper.ceil(totalHotbarCount.toDouble() / PAGE_SIZE.toDouble()))
    }

    fun isOverlayActive(client: MinecraftClient): Boolean {
        return supportsSavedHotbars(client) &&
            AxionModifierKeys.isAltDown(client) &&
            !LitematicaCompat.isHoldingConfiguredTool(client)
    }

    fun displayHotbarsForSelectedPage(client: MinecraftClient): List<DisplayHotbar> {
        val selectedIndex = selectedIndex()
        val activeIndex = activeIndex()
        val page = selectedPage()
        val pageStart = page * PAGE_SIZE
        val pageEndExclusive = pageStart + PAGE_SIZE
        val showLiveHotbar = activeIndex in pageStart until pageEndExclusive
        return (pageStart until (pageStart + PAGE_SIZE)).map { index ->
            DisplayHotbar(
                index = index,
                stacks = stacksForDisplay(client, index, activeIndex, showLiveHotbar),
                selected = index == selectedIndex,
                active = index == activeIndex,
            )
        }
    }

    fun changePage(direction: Int) {
        if (direction == 0) {
            return
        }

        val currentPage = selectedPage()
        val nextPage = (currentPage + direction).coerceAtLeast(0)
        val requiredCount = (nextPage + 1) * PAGE_SIZE
        AxionClientConfig.ensureSavedHotbarCapacity(requiredCount)
        val currentRow = selectedIndex() % PAGE_SIZE
        pendingSelectionIndex = (nextPage * PAGE_SIZE) + currentRow
    }

    fun selectHotbar(index: Int) {
        if (index < 0) {
            return
        }

        if (index >= AxionClientConfig.savedHotbars().size) {
            val requiredPages = (index / PAGE_SIZE) + 1
            AxionClientConfig.ensureSavedHotbarCapacity(requiredPages * PAGE_SIZE)
        }
        pendingSelectionIndex = index
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!supportsSavedHotbars(client)) {
            return false
        }

        val direction = scrollAmount.compareTo(0.0)
        if (direction == 0) {
            return false
        }

        val currentIndex = pendingSelectionIndex ?: AxionClientConfig.activeSavedHotbarIndex()
        val nextIndex = (currentIndex - direction).coerceAtLeast(0)
        if (nextIndex >= AxionClientConfig.savedHotbars().size) {
            val requiredPages = (nextIndex / PAGE_SIZE) + 1
            AxionClientConfig.ensureSavedHotbarCapacity(requiredPages * PAGE_SIZE)
        }
        pendingSelectionIndex = nextIndex
        return true
    }

    fun onEndTick(client: MinecraftClient) {
        SavedHotbarGameModeController.onEndTick(client)
        val isAltDown = AxionModifierKeys.isAltDown(client)
        val shouldRequestCreative = SavedHotbarAltPolicy.shouldRequestCreative(
            wasAltDown = wasAltDown,
            isAltDown = isAltDown,
            isSpectator = client.player?.isSpectator == true,
            menuEligible = isSavedHotbarContextEligible(client),
        )
        wasAltDown = isAltDown

        if (shouldRequestCreative) {
            SavedHotbarGameModeController.request(client, SavedHotbarMenuAction.CREATIVE)
        }

        val player = client.player
        if (player == null || client.world == null) {
            pendingSelectionIndex = null
            return
        }

        if (!supportsSavedHotbars(client)) {
            pendingSelectionIndex = null
            return
        }

        if (isAltDown) {
            if (pendingSelectionIndex == null) {
                pendingSelectionIndex = AxionClientConfig.activeSavedHotbarIndex()
            }
            return
        }

        val pendingIndex = pendingSelectionIndex ?: return
        pendingSelectionIndex = null

        val activeIndex = AxionClientConfig.activeSavedHotbarIndex()
        if (pendingIndex == activeIndex) {
            return
        }

        saveCurrentHotbar(client, activeIndex)
        loadSavedHotbar(client, pendingIndex)
        AxionClientConfig.setActiveSavedHotbarIndex(pendingIndex)
    }

    fun flushActiveHotbar(client: MinecraftClient) {
        if (!canPersistActiveHotbar(client)) {
            return
        }
        saveCurrentHotbar(client, AxionClientConfig.activeSavedHotbarIndex())
    }

    private fun supportsSavedHotbars(client: MinecraftClient): Boolean {
        return isSavedHotbarContextEligible(client) &&
            AxionToolSelectionController.isCreativeModeAllowed() &&
            !AxionToolSelectionController.isAxionSlotActive()
    }

    private fun isSavedHotbarContextEligible(client: MinecraftClient): Boolean {
        return client.currentScreen == null &&
            !client.options.hudHidden &&
            !SavedHotbarGameModeController.isTransitionPending() &&
            !AxionToolSelectionController.isAxionSlotActive() &&
            !LitematicaCompat.isHoldingConfiguredTool(client)
    }

    private fun canPersistActiveHotbar(client: MinecraftClient): Boolean {
        return client.player != null &&
            client.world != null &&
            AxionToolSelectionController.isCreativeModeAllowed() &&
            !AxionToolSelectionController.isAxionSlotActive()
    }

    private fun saveCurrentHotbar(client: MinecraftClient, hotbarIndex: Int) {
        val player = client.player ?: return
        val world = client.world ?: return
        val hotbar = SavedHotbarConfig(
            slots = List(HOTBAR_SIZE) { slot ->
                serializeStack(world.registryManager, player.inventory.getStack(slot))
            },
        )
        hotbar.slots.filterNotNull().forEach(stackCache::remove)
        AxionClientConfig.updateSavedHotbar(hotbarIndex, hotbar)
    }

    private fun loadSavedHotbar(client: MinecraftClient, hotbarIndex: Int) {
        val player = client.player ?: return
        val world = client.world ?: return
        val interactionManager = client.interactionManager ?: return
        val savedHotbar = AxionClientConfig.savedHotbar(hotbarIndex) ?: SavedHotbarConfig.empty()
        repeat(HOTBAR_SIZE) { slot ->
            val stack = deserializeStack(world.registryManager, savedHotbar.slots.getOrNull(slot)).copy()
            player.inventory.setStack(slot, stack)
            interactionManager.clickCreativeStack(stack, 36 + slot)
        }
    }

    fun deserializeStackForDisplay(
        registryManager: net.minecraft.registry.DynamicRegistryManager,
        serialized: String?,
    ): ItemStack {
        return deserializeStack(registryManager, serialized)
    }

    private fun stacksForDisplay(
        client: MinecraftClient,
        index: Int,
        activeIndex: Int,
        showLiveHotbar: Boolean,
    ): List<ItemStack> {
        val player = client.player
        return if (showLiveHotbar && index == activeIndex && player != null) {
            List(HOTBAR_SIZE) { slot -> player.inventory.getStack(slot).copy() }
        } else {
            val savedHotbar = AxionClientConfig.savedHotbar(index) ?: SavedHotbarConfig.empty()
            val registryManager = client.world?.registryManager ?: return List(HOTBAR_SIZE) { ItemStack.EMPTY }
            List(HOTBAR_SIZE) { slot ->
                deserializeStack(registryManager, savedHotbar.slots.getOrNull(slot)).copy()
            }
        }
    }

    fun clearSlotItem(hotbarIndex: Int, slotIndex: Int) {
        setSlotItem(hotbarIndex, slotIndex, null)
    }

    fun setSlotItem(hotbarIndex: Int, slotIndex: Int, serialized: String?) {
        val config = AxionClientConfig.savedHotbar(hotbarIndex) ?: return
        val slots = config.slots.toMutableList()
        while (slots.size <= slotIndex) slots.add(null)
        slots[slotIndex] = serialized
        AxionClientConfig.updateSavedHotbar(hotbarIndex, config.copy(slots = slots))
    }

    fun clearPage(page: Int) {
        val pageStart = page * PAGE_SIZE
        for (i in pageStart until pageStart + PAGE_SIZE) {
            AxionClientConfig.updateSavedHotbar(i, SavedHotbarConfig.empty())
        }
    }

    private fun serializeStack(
        registryManager: net.minecraft.registry.DynamicRegistryManager,
        stack: ItemStack,
    ): String? {
        if (stack.isEmpty) {
            return null
        }

        // Use VersionCompat helper for 26.1.x, fall back to reflection for older versions
        val bytes = VersionCompat.INSTANCE.itemStackEncode(registryManager, stack)
        if (bytes != null) {
            return Base64.getEncoder().encodeToString(bytes)
        }

        // Fallback to reflection for older versions
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.buffer(), registryManager)
            itemStackStreamCodec().javaClass
                .methods
                .first { it.name == "encode" && it.parameterTypes.size == 2 }
                .invoke(itemStackStreamCodec(), buf, stack)
            val fallbackBytes = ByteArray(buf.readableBytes())
            buf.getBytes(0, fallbackBytes)
            Base64.getEncoder().encodeToString(fallbackBytes)
        }.getOrNull()
    }

    private fun deserializeStack(
        registryManager: net.minecraft.registry.DynamicRegistryManager,
        serialized: String?,
    ): ItemStack {
        if (serialized.isNullOrBlank()) {
            return ItemStack.EMPTY
        }

        stackCache[serialized]?.let { return it.copy() }

        // Use VersionCompat helper for 26.1.x, fall back to reflection for older versions
        val bytes = Base64.getDecoder().decode(serialized)
        val decoded = VersionCompat.INSTANCE.itemStackDecode(registryManager, bytes)
        if (decoded != null) {
            return (decoded as ItemStack).also { stackCache[serialized] = it.copy() }
        }

        // Fallback to reflection for older versions
        return runCatching {
            val buf = RegistryByteBuf(Unpooled.wrappedBuffer(bytes), registryManager)
            itemStackStreamCodec().javaClass
                .methods
                .first { it.name == "decode" && it.parameterTypes.size == 1 }
                .invoke(itemStackStreamCodec(), buf) as ItemStack
        }.getOrDefault(ItemStack.EMPTY).also { decoded ->
            stackCache[serialized] = decoded.copy()
        }
    }

    private fun itemStackStreamCodec(): Any {
        return runCatching { ItemStack::class.java.getField("PACKET_CODEC").get(null) }
            .getOrElse { ItemStack::class.java.getField("STREAM_CODEC").get(null) }
    }
}
