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
 * Creates and identifies the "My Energy Force Fields" book - a real,
 * take-able item (a Written Book by default). Right-clicking it opens the
 * fields list GUI instead of the vanilla book-reading screen. Tagged with
 * persistent data so it can be told apart from a plain material item of the
 * same type.
 */
public final class BookItem {

    private static final String KEY = "fields_book";

    private BookItem() {
    }

    public static ItemStack create(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("fields-book-material", "WRITTEN_BOOK");
        Material material;
        try {
            material = Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown fields-book-material '" + materialName + "' in config.yml, falling back to WRITTEN_BOOK");
            material = Material.WRITTEN_BOOK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("My Energy Force Fields", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to open your fields list", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBook(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, KEY), PersistentDataType.BYTE);
    }
}
