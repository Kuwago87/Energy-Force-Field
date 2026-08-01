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
 * Creates and identifies the Force Field Beacon - a tagged Beacon block that,
 * once placed, generates a spherical "bubble" shield centered on itself.
 * Unlike the rod's rectangular zones, a beacon field is a hollow shell (not a
 * solid-filled ball) so even its largest preset radius stays a manageable
 * number of blocks. Right-click the placed beacon to open its controls
 * (on/off, delete, and three size presets); breaking it (if you're allowed
 * to) deletes the field entirely.
 */
public final class BeaconItem {

    private static final String KEY = "beacon_field_tool";

    private BeaconItem() {
    }

    public static ItemStack create(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("beacon-field-material", "BEACON");
        Material material;
        try {
            material = Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown beacon-field-material '" + materialName + "' in config.yml, falling back to BEACON");
            material = Material.BEACON;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Force Field Generator", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place it to create a spherical bubble", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("shield centered on itself.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click the placed beacon for its", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("controls (on/off, size, delete).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBeaconTool(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, KEY), PersistentDataType.BYTE);
    }
}
