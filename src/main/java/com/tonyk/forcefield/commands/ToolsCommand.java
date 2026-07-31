package com.tonyk.forcefield.commands;

import com.tonyk.forcefield.gui.ToolsMenu;
import com.tonyk.forcefield.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /eff_tools (aliases: /eff, /efftools) - opens the Energy Force Field
 * Tools GUI.
 */
public final class ToolsCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final Messages messages;

    public ToolsCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This can only be used in-game.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("forcefield.use")) {
            messages.send(player, "no-permission");
            return true;
        }
        player.openInventory(ToolsMenu.create(plugin));
        return true;
    }
}
