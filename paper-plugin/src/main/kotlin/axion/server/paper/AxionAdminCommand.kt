package axion.server.paper

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender

class AxionAdminCommand(
    private val plugin: AxionPaperPlugin,
) : BasicCommand {
    override fun execute(
        commandSourceStack: CommandSourceStack,
        args: Array<out String>,
    ) {
        val sender = commandSourceStack.sender
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("Missing permission $PERMISSION")
            return
        }

        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            sender.sendMessage("/axion timing summary")
            sender.sendMessage("/axion timing reset")
            if (sender is ConsoleCommandSender) {
                sender.sendMessage("/axion reload")
            }
            return
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (sender !is ConsoleCommandSender) {
                sender.sendMessage("Axion config reload is console-only.")
                return
            }
            plugin.reloadAxionConfig()
            sender.sendMessage("Axion config reloaded.")
            return
        }

        if (!args[0].equals("timing", ignoreCase = true)) {
            sender.sendMessage("Unknown subcommand. Try /axion timing summary")
            return
        }

        val action = args.getOrNull(1)?.lowercase()
        when (action) {
            "summary" -> {
                plugin.logTimingSummary("command")
                sender.sendMessage("Axion timing summary logged.")
            }

            "reset" -> {
                plugin.resetTimingSummary()
                sender.sendMessage("Axion timing summary reset.")
            }

            else -> sender.sendMessage("Unknown timing action. Use summary or reset.")
        }
    }

    override fun suggest(
        commandSourceStack: CommandSourceStack,
        args: Array<out String>,
    ): List<String> {
        return when (args.size) {
            1 -> subcommands(commandSourceStack.sender).filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> listOf("summary", "reset").filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }

    override fun canUse(sender: CommandSender): Boolean = sender is ConsoleCommandSender || sender.hasPermission(PERMISSION)

    override fun permission(): String = PERMISSION

    companion object {
        const val PERMISSION: String = "axion.admin.timing"

        private fun subcommands(sender: CommandSender): List<String> {
            return if (sender is ConsoleCommandSender) {
                listOf("timing", "reload")
            } else {
                listOf("timing")
            }
        }
    }
}
