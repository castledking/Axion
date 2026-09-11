package axion.client.editor.ui

import com.mojang.blaze3d.platform.Window

/**
 * Yarn-named window accessors for shared editor UI code. Official-mapping
 * ranges resolve these as extensions; yarn ranges have real members of the
 * same names, which take precedence.
 */
val Window.handle: Long
    get() = handle()

val Window.scaledWidth: Int
    get() = guiScaledWidth

val Window.scaledHeight: Int
    get() = guiScaledHeight

val Window.width: Int
    get() = screenWidth

val Window.height: Int
    get() = screenHeight
