package com.tonyk.forcefield.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Creates and identifies the admin-only "All Energy Force Fields" book - a
 * real, take-able item (an Enchanted Book by default, so it's visually
 * distinct from the regular per-player book at a glance). Right-clicking it
 * opens the admin fields list GUI, which shows every zone on the server
 * regardless of owner. Only ever handed out from the Tools GUI to players
 * with forcefield.admin.
 */
public final class AdminBookItem {

    private static final String KEY = "admin_fields_book";

    private AdminBookItem() {
    }

    public static ItemStack create(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("admin-fields-book-material", "ENCHANTED_BOOK");
        Material material;
        try {
            material = Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown admin-fields-book-material '" + materialName + "' in config.yml, falling back to ENCHANTED_BOOK");
            material = Material.ENCHANTED_BOOK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("All Energy Force Fields", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Admin only", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to open every field on the server", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isAdminBook(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, KEY), PersistentDataType.BYTE);
    }
}
