package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.FieldShape;
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
import java.util.Comparator;
import java.util.List;

/**
 * Builds the paginated field list: one icon per zone, with paper Previous/
 * Next buttons along the bottom row. The player's own "My Energy Force
 * Fields" book still shows every field they own together, grouped by type
 * (every rod/cuboid field, then every beacon bubble) so the two don't
 * interleave. The admin book instead opens a category chooser first (see
 * {@link AdminCategoryMenu}) and only ever shows one type at a time here -
 * a true separate, independently-paginated list per type, not just a sort
 * order, so the two can never end up mixed on the same page.
 */
public final class FieldsListMenu {

    public static final int SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final int BACK_TO_CATEGORIES_SLOT = 49;

    /** Cuboid fields before spherical ones, alphabetical (case-insensitive) within each. */
    private static final Comparator<ForceFieldZone> ZONE_ORDER = Comparator
            .comparing(ForceFieldZone::getShape)
            .thenComparing(ForceFieldZone::getName, String.CASE_INSENSITIVE_ORDER);

    private FieldsListMenu() {
    }

    public static Inventory create(JavaPlugin plugin, FieldManager fields, Player player, int requestedPage) {
        List<ForceFieldZone> owned = new ArrayList<>(fields.getZonesOwnedBy(player.getUniqueId()));
        owned.sort(ZONE_ORDER);
        List<String> names = new ArrayList<>();
        for (ForceFieldZone zone : owned) {
            names.add(zone.getName());
        }
        return build(plugin, fields, names, requestedPage, false, null,
                Component.text("My Energy Force Fields", NamedTextColor.DARK_AQUA));
    }

    /**
     * Admin variant: every zone of one specific shape on the server,
     * regardless of owner - only ever opened from the admin book's category
     * chooser (itself gated behind forcefield.admin), one category at a
     * time, so Rod Fields and Beacon Generators are always fully separate
     * lists rather than one list sorted to merely group them.
     */
    public static Inventory createAll(JavaPlugin plugin, FieldManager fields, int requestedPage, FieldShape categoryFilter) {
        List<ForceFieldZone> all = new ArrayList<>();
        for (ForceFieldZone zone : fields.getZones().values()) {
            if (zone.getShape() == categoryFilter) {
                all.add(zone);
            }
        }
        all.sort(ZONE_ORDER);
        List<String> names = new ArrayList<>();
        for (ForceFieldZone zone : all) {
            names.add(zone.getName());
        }
        String title = categoryFilter == FieldShape.SPHERE ? "All Beacon Generators" : "All Rod Fields";
        return build(plugin, fields, names, requestedPage, true, categoryFilter,
                Component.text(title, NamedTextColor.LIGHT_PURPLE));
    }

    private static Inventory build(JavaPlugin plugin, FieldManager fields, List<String> names,
                                    int requestedPage, boolean adminView, FieldShape categoryFilter, Component title) {
        int maxPage = names.isEmpty() ? 0 : (names.size() - 1) / PAGE_SIZE;
        int page = Math.max(0, Math.min(requestedPage, maxPage));

        FieldsListHolder holder = new FieldsListHolder(names, page, adminView, categoryFilter);
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
        if (adminView && categoryFilter != null) {
            inventory.setItem(BACK_TO_CATEGORIES_SLOT, backToCategoriesIcon());
        }

        return inventory;
    }

    private static ItemStack fieldIcon(JavaPlugin plugin, ForceFieldZone zone, boolean adminView) {
        ItemStack item = new ItemStack(zone.isSpherical() ? sphereIconMaterial(plugin) : cuboidIconMaterial(plugin));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(zone.getName(), NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + (zone.isSpherical() ? "Beacon bubble" : "Rod (cuboid)"), NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("State: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(zone.isEnabled() ? "RAISED" : "lowered",
                        zone.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("World: " + zone.getCuboid().getWorldName(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (zone.isSpherical()) {
            List<ForceFieldZone.SphereComponent> comps = zone.getSphereComponents();
            if (comps.size() > 1) {
                lore.add(Component.text("Beacons: " + comps.size() + " merged, each independently on/off:", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                for (ForceFieldZone.SphereComponent c : comps) {
                    lore.add(Component.text("  - " + c.getRadius() + " blocks: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(c.isEnabled() ? "RAISED" : "lowered",
                                    c.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false));
                }
            } else if (!comps.isEmpty()) {
                lore.add(Component.text("Radius: " + comps.get(0).getRadius() + " blocks", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("Volume: " + zone.getCuboid().volume() + " blocks", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
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

    private static Material cuboidIconMaterial(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("fields-list-icon-material", "NETHERITE_NAUTILUS_ARMOR");
        try {
            return Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown fields-list-icon-material '" + materialName + "' in config.yml, falling back to NAUTILUS_SHELL");
            return Material.NAUTILUS_SHELL;
        }
    }

    private static Material sphereIconMaterial(JavaPlugin plugin) {
        String materialName = plugin.getConfig().getString("beacon-fields-list-icon-material", "BEACON");
        try {
            return Material.valueOf(materialName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown beacon-fields-list-icon-material '" + materialName + "' in config.yml, falling back to BEACON");
            return Material.BEACON;
        }
    }

    private static ItemStack navIcon(String label) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backToCategoriesIcon() {
        ItemStack item = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("« Back to Categories", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
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
