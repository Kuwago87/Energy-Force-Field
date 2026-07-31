package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the paginated "My Energy Force Fields" list: one icon per zone the
 * player owns, with paper Previous/Next buttons along the bottom row.
 */
public final class FieldsListMenu {

    public static final int SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;

    private FieldsListMenu() {
    }

    public static Inventory create(JavaPlugin plugin, FieldManager fields, Player player, int requestedPage) {
        List<ForceFieldZone> owned = fields.getZonesOwnedBy(player.getUniqueId());
        List<String> names = new ArrayList<>();
        for (ForceFieldZone zone : owned) {
            names.add(zone.getName());
        }
        return build(plugin, fields, names, requestedPage, false,
                Component.text("My Energy Force Fields", NamedTextColor.DARK_AQUA));
    }

    /**
     * Admin variant: every zone on the server, regardless of owner. Only ever
     * opened from the admin book, which is itself gated behind
     * forcefield.admin.
     */
    public static Inventory createAll(JavaPlugin plugin, FieldManager fields, int requestedPage) {
        List<String> names = new ArrayList<>(fields.getZones().keySet());
        return build(plugin, fields, names, requestedPage, true,
                Component.text("All Energy Force Fields", NamedTextColor.LIGHT_PURPLE));
    }

    private static Inventory build(JavaPlugin plugin, FieldManager fields, List<String> names,
                                    int requestedPage, boolean adminView, Component title) {
        int maxPage = names.isEmpty() ? 0 : (names.size() - 1) / PAGE_SIZE;
        int page = Math.max(0, Math.min(requestedPage, maxPage));

        FieldsListHolder holder = new FieldsListHolder(names, page, adminView);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        if (names.isEmpty()) {
            inventory.setItem(22, emptyIcon());
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(names.size(), start + PAGE_SIZE);
            for (int i = start; i < end; i++) {
                ForceFieldZone zone = fields.getZone(names.get(i));
                if (zone != null) {
                    inventory.setItem(i - start, fieldIcon(plugin, zone, adminView));
                }
            }
        }

        if (page > 0) {
            inventory.setItem(PREV_SLOT, navIcon("« Previous Page"));
        }
        if (page < maxPage) {
            inventory.setItem(NEXT_SLOT, navIcon("Next Page »"));
        }

        return inventory;
    }

    private static ItemStack fieldIcon(JavaPlugin plugin, ForceFieldZone zone, boolean adminView) {
        String materialName = plugin.getConfig().getString("fields-list-icon-material", "NETHERITE_NAUTILUS_ARMOR");
        Material material;
        try {
            material = Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown fields-list-icon-material '" + materialName + "' in config.yml, falling back to NAUTILUS_SHELL");
            material = Material.NAUTILUS_SHELL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(zone.getName(), NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("State: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(zone.isEnabled() ? "RAISED" : "lowered",
                        zone.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("World: " + zone.getCuboid().getWorldName(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Volume: " + zone.getCuboid().volume() + " blocks", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (adminView) {
            String owner = zone.getOwnerName() != null ? zone.getOwnerName() : "unowned";
            lore.add(Component.text("Owner: " + owner, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to manage", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navIcon(String label) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack emptyIcon() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("No fields yet", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Grab the Create/Delete rod from", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("/eff_tools to make your first one.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack fillerPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
