package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds one beacon's own control GUI: a lever to raise/lower just its own
 * bubble (independent of every other beacon it might be merged with), three
 * amethyst-growth-tier buttons to set its radius (small, medium, large -
 * each configurable in config.yml), and a barrier to delete just this
 * beacon's own bubble. Changing size always lowers this beacon first if it's
 * raised - the player has to click On/Off again afterward to raise it at the
 * new size, since resizing while raised means restoring the old shell before
 * the new one can be captured/filled. None of this ever touches a merged
 * neighbor's own state.
 */
public final class BeaconFieldMenu {

    public static final int SIZE = 9;
    public static final int ON_OFF_SLOT = 1;
    public static final int SMALL_SLOT = 3;
    public static final int MEDIUM_SLOT = 4;
    public static final int LARGE_SLOT = 5;
    public static final int DELETE_SLOT = 7;

    private BeaconFieldMenu() {
    }

    public static Inventory create(JavaPlugin plugin, FieldManager fields, UUID zoneId, Location beaconLocation) {
        ForceFieldZone zone = fields.getZoneById(zoneId);
        ForceFieldZone.SphereComponent component = zone != null
                ? zone.findComponentAt(beaconLocation.getBlockX(), beaconLocation.getBlockY(), beaconLocation.getBlockZ())
                : null;
        BeaconFieldHolder holder = new BeaconFieldHolder(zoneId, beaconLocation);
        String title = zone != null ? zone.getName() : "Force Field Generator";

        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(title, NamedTextColor.AQUA));
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        int small = radiusConfig(plugin, "beacon-field-radius-small", 50);
        int medium = radiusConfig(plugin, "beacon-field-radius-medium", 150);
        int large = radiusConfig(plugin, "beacon-field-radius-large", 250);
        int otherBeacons = zone != null ? zone.getSphereComponents().size() - 1 : 0;

        inventory.setItem(ON_OFF_SLOT, onOffIcon(component, otherBeacons));
        inventory.setItem(SMALL_SLOT, radiusIcon("Small", Material.SMALL_AMETHYST_BUD, component, small));
        inventory.setItem(MEDIUM_SLOT, radiusIcon("Medium", Material.MEDIUM_AMETHYST_BUD, component, medium));
        inventory.setItem(LARGE_SLOT, radiusIcon("Large", Material.AMETHYST_CLUSTER, component, large));
        inventory.setItem(DELETE_SLOT, deleteIcon(otherBeacons > 0));

        return inventory;
    }

    private static int radiusConfig(JavaPlugin plugin, String key, int def) {
        return Math.max(1, plugin.getConfig().getInt(key, def));
    }

    private static ItemStack onOffIcon(ForceFieldZone.SphereComponent component, int otherBeacons) {
        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("On / Off", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (component != null) {
            lore.add(Component.text("Currently: ", NamedTextColor.GRAY)
                    .append(Component.text(component.isEnabled() ? "RAISED" : "lowered",
                            component.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Radius: " + component.getRadius() + " blocks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (otherBeacons > 0) {
                lore.add(Component.text("Merged with " + otherBeacons + " other beacon(s)", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("This lever controls only this beacon -", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("the other one keeps its own state", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }
        lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack radiusIcon(String label, Material material, ForceFieldZone.SphereComponent component, int radius) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        boolean current = component != null && component.getRadius() == radius;
        meta.displayName(Component.text(label + " (" + radius + " blocks)",
                        current ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (current) {
            lore.add(Component.text("Current size", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("Click to set this beacon's own bubble to this size", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("(lowers just this beacon first if it's raised -", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("a merged neighbor is left alone)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack deleteIcon(boolean partOfMerge) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Delete", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        if (partOfMerge) {
            meta.lore(List.of(
                    Component.text("Click to remove just this beacon's own", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("bubble and break this beacon - the rest", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("of the merged field stays up", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            meta.lore(List.of(
                    Component.text("Click to permanently delete this field", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("and break this beacon", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        }
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
