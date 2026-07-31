package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.util.AdminBookItem;
import com.tonyk.forcefield.util.BookItem;
import com.tonyk.forcefield.util.OnOffCrystal;
import com.tonyk.forcefield.util.WandItem;
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

/**
 * Builds the "Energy Force Field Tools" GUI: a single row with the
 * Create/Delete rod, the On/Off remote crystal, and the tracking book
 * (creating a field via the rod also hands out physical lecterns as its
 * on/off switches - see LecternItem). Each item only appears for a player
 * who actually has the matching forcefield.tool.* permission - a slot for
 * an item you're not allowed to use is left as a plain filler pane, same
 * treatment the admin book already got. Players with forcefield.admin also
 * get that fourth item, the admin "All Energy Force Fields" book.
 */
public final class ToolsMenu {

    public static final int ROD_SLOT = 2;
    public static final int CRYSTAL_SLOT = 4;
    public static final int BOOK_SLOT = 6;
    public static final int ADMIN_BOOK_SLOT = 8;

    private ToolsMenu() {
    }

    public static Inventory create(JavaPlugin plugin, Player player) {
        ToolsMenuHolder holder = new ToolsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text("Energy Force Field Tools", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        if (player != null && player.hasPermission("forcefield.tool.rod")) {
            inventory.setItem(ROD_SLOT, WandItem.create(plugin));
        }
        if (player != null && player.hasPermission("forcefield.tool.crystal")) {
            inventory.setItem(CRYSTAL_SLOT, OnOffCrystal.create(plugin));
        }
        if (player != null && player.hasPermission("forcefield.tool.book")) {
            inventory.setItem(BOOK_SLOT, BookItem.create(plugin));
        }
        if (player != null && player.hasPermission("forcefield.admin")) {
            inventory.setItem(ADMIN_BOOK_SLOT, AdminBookItem.create(plugin));
        }

        return inventory;
    }

    private static ItemStack fillerPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
