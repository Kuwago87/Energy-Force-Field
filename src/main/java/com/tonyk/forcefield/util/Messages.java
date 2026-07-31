package com.tonyk.forcefield.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads user-facing messages from config.yml (with '&' colour codes) and
 * sends them to players/console, with a prefix and simple %placeholder%
 * substitution.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private String raw(String key) {
        String value = plugin.getConfig().getString("messages." + key);
        return value == null ? key : value;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    public void send(CommandSender to, String key, String... replacements) {
        String message = prefix() + raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        to.sendMessage(LEGACY.deserialize(message));
    }

    public Component component(String key, String... replacements) {
        String message = prefix() + raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return LEGACY.deserialize(message);
    }
}
