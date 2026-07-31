package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.util.OnOffCrystal;
import com.tonyk.forcefield.util.WandItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Builds the "Energy Force Field Tools" GUI: a single row with the
 * Create/Delete rod, the On/Off crystal, and the tracking book.
 */
public final class ToolsMenu {

    public static final int ROD_SLOT = 2;
    public static final int CRYSTAL_SLOT = 4;
    public static final int BOOK_SLOT = 6;

    private ToolsMenu() {
    }

    public static Inventory create(JavaPlugin plugin) {
        ToolsMenuHolder holder = new ToolsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("Energy Force Field Tools", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(ROD_SLOT, WandItem.create(plugin));
        inventory.setItem(CRYSTAL_SLOT, OnOffCrystal.create(plugin));
        inventory.setItem(BOOK_SLOT, bookIcon());

        return inventory;
    }

    private static ItemStack fillerPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack bookIcon() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("My Energy Force Fields", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click to view your fields", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }
}
