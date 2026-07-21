package axion.client.hotbar

enum class SavedHotbarMenuAction(
    val label: String,
    val gameModeId: String? = null,
) {
    CREATE_DISPLAY_ENTITY("Create Display Entity"),
    EDIT_BLOCK_ATTRIBUTES("Edit Block Attributes"),
    SURVIVAL("Survival", "survival"),
    SPECTATOR("Spectator", "spectator"),
    CREATIVE("Creative", "creative"),
}
