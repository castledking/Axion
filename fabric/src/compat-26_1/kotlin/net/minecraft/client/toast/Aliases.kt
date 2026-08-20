package net.minecraft.client.toast

object SystemToast {
    object Type {
        val PERIODIC_NOTIFICATION: net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId =
            net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION
    }

    fun add(
        manager: net.minecraft.client.gui.components.toasts.ToastManager,
        type: net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId,
        title: net.minecraft.network.chat.Component,
        description: net.minecraft.network.chat.Component,
    ) {
        net.minecraft.client.gui.components.toasts.SystemToast.add(manager, type, title, description)
    }
}
