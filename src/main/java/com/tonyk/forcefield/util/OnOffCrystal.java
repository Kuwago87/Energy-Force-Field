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
 * Creates and identifies the "On / Off" remote - an End Crystal by default -
 * used to raise or lower the nearest Energy Force Field. Tagged with
 * persistent data so it can be told apart from a plain material item of the
 * same type (and so it never triggers vanilla End Crystal placement).
 */
public final class OnOffCrystal {

    private static final String KEY = "onoff_crystal";

    private OnOffCrystal() {
    }

    public static ItemStack create(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("on-off-crystal-material", "END_CRYSTAL");
        Material material;
        try {
            material = Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown on-off-crystal-material '" + materialName + "' in config.yml, falling back to END_CRYSTAL");
            material = Material.END_CRYSTAL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("On / Off", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click near your field to toggle it", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCrystal(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, KEY), PersistentDataType.BYTE);
    }
}
