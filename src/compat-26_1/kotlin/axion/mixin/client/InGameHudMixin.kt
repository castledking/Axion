package axion.mixin.client

// HUD rendering stub for 26.1.2
// Minecraft 26.1.2 has significantly refactored the GUI/HUD system:
// - InGameHud was renamed/moved to net.minecraft.client.gui.Gui
// - DrawContext now requires GuiGraphicsExtractor instead of MinecraftClient
// - The entire rendering pipeline uses new render-state approach
//
// TODO: Implement HUD rendering for 26.1.2 when:
// 1. Fabric API includes HudElementRegistry (in development)
// 2. Proper method signatures are documented
// 3. GuiGraphicsExtractor usage is understood
//
// For now, the mod loads but HUD (Axion tool slot) will not appear in 26.1.2.
// Core functionality (commands, operations, keybindings) still works.

/**
 * Placeholder mixin - HUD rendering not yet implemented for 26.1.2.
 * The axion.client.mixins.json still references this file for compatibility.
 */
object InGameHudMixin

