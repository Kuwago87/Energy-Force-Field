package com.tonyk.forcefield.util;

import com.tonyk.forcefield.model.ForceFieldZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and identifies the physical on/off lectern for a specific Energy
 * Force Field zone. Placing one and right-clicking it toggles that field -
 * for public fields, any player can; for private ones, only the owner or an
 * admin. Double-left-clicking a placed one (if you can manage that field)
 * opens an edit menu.
 *
 * Lecterns are linked by the zone's stable internal id, not its name, so
 * renaming a field never breaks an already-placed lectern. Older lecterns
 * placed before ids existed only carry the legacy name tag; LecternListener
 * upgrades them to the id-based tag the first time they're interacted with
 * (as long as the field hasn't since been renamed itself - if it has,
 * there's no way to recover the link and the old lectern needs replacing).
 */
public final class LecternItem {

    private static final String ID_KEY = "lectern_zone_id";
    private static final String LEGACY_NAME_KEY = "lectern_zone";

    private LecternItem() {
    }

    public static NamespacedKey idKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, ID_KEY);
    }

    public static NamespacedKey legacyNameKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, LEGACY_NAME_KEY);
    }

    public static ItemStack create(JavaPlugin plugin, ForceFieldZone zone) {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Force Field Lectern", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Linked to: " + zone.getName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Double left-click to edit the field", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(idKey(plugin), PersistentDataType.STRING, zone.getId().toString());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the zone id tagged on this item (current format), or null if it isn't one of ours. */
    public static UUID idOf(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.LECTERN || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(idKey(plugin), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Returns the zone name tagged on a pre-id legacy item, or null. */
    public static String legacyNameOf(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.LECTERN || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(legacyNameKey(plugin), PersistentDataType.STRING);
    }

    /**
     * Gives the player {@code count} lecterns linked to {@code zone}, adding
     * to their inventory and dropping any overflow at their feet if it's
     * full.
     */
    public static void giveSet(JavaPlugin plugin, Player player, ForceFieldZone zone, int count) {
        for (int i = 0; i < count; i++) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(create(plugin, zone));
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
        }
    }
}
