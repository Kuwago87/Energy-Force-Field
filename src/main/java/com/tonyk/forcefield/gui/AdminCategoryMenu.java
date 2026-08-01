package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The admin book's landing screen: pick "Rod Fields" or "Beacon Generators"
 * before seeing an actual list, so the two very differently-shaped field
 * types are never shown mixed together on the same page - each opens its
 * own dedicated, independently-paginated {@link FieldsListMenu}.
 */
public final class AdminCategoryMenu {

    public static final int SIZE = 9;
    public static final int ROD_SLOT = 3;
    public static final int BEACON_SLOT = 5;

    private AdminCategoryMenu() {
    }

    public static Inventory create(FieldManager fields) {
        AdminCategoryHolder holder = new AdminCategoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("All Energy Force Fields", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        int rodCount = 0;
        int beaconCount = 0;
        for (ForceFieldZone zone : fields.getZones().values()) {
            if (zone.isSpherical()) {
                beaconCount++;
            } else {
                rodCount++;
            }
        }

        inventory.setItem(ROD_SLOT, categoryIcon(Material.LIGHTNING_ROD, "Rod Fields", rodCount, NamedTextColor.AQUA));
        inventory.setItem(BEACON_SLOT, categoryIcon(Material.BEACON, "Beacon Generators", beaconCount, NamedTextColor.LIGHT_PURPLE));

        return inventory;
    }

    private static ItemStack categoryIcon(Material material, String label, int count, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(count + " field" + (count == 1 ? "" : "s") + " on the server", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to view", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
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
