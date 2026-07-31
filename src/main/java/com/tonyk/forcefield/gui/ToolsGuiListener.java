package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.util.AdminBookItem;
import com.tonyk.forcefield.util.BookItem;
import com.tonyk.forcefield.util.OnOffCrystal;
import com.tonyk.forcefield.util.WandItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the EFF Tools GUI. The rod, crystal, and book (and, for admins,
 * the admin book) all sit in fixed slots as real, take-able items: a normal
 * (or shift-) click picks one up like any other inventory, and the slot
 * refills a tick later (only once confirmed empty, so nothing duplicates if
 * the player's inventory was full). What each item then *does* once it's in
 * hand is handled elsewhere (WandListener for the rod, CrystalListener for
 * the remote, FieldsGuiListener for the books).
 */
public final class ToolsGuiListener implements Listener {

    private final JavaPlugin plugin;

    public ToolsGuiListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ToolsMenuHolder)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory != topInventory) {
            // Clicked their own inventory (or outside) while the menu is open - leave it alone.
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        String requiredPermission = permissionFor(slot);
        if (requiredPermission == null) {
            // Filler pane slot - don't let it be taken.
            event.setCancelled(true);
            return;
        }
        if (!player.hasPermission(requiredPermission)) {
            // ToolsMenu only ever puts a real item here for a player who has
            // this permission - for everyone else this slot is just a filler
            // pane, which should never be pickable.
            event.setCancelled(true);
            return;
        }

        // Only allow click types that *take* the item (plain/shift click, drop,
        // double-click collect). Anything that could place or swap an item INTO
        // the slot (SWAP_WITH_CURSOR, PLACE_*, HOTBAR_SWAP, ...) is denied, so
        // players can't dump junk into the menu instead of picking a tool up.
        boolean isTakeAction = switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, CLONE_STACK, COLLECT_TO_CURSOR -> true;
            default -> false;
        };
        if (!isTakeAction) {
            event.setCancelled(true);
            return;
        }

        // Let the click go through normally (item moves to their inventory/cursor
        // like any chest), then check next tick whether the slot is now empty
        // before refilling it - this avoids duplicating items if their
        // inventory was full and the take silently failed.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack current = topInventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                topInventory.setItem(slot, freshItemFor(slot));
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ToolsMenuHolder)) {
            return;
        }
        // Dragging in/out could straddle both inventories or split stacks across
        // filler slots - simplest to keep the menu drag-free and rely on single
        // clicks (and shift-clicks, which aren't drags) for picking tools up.
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private ItemStack freshItemFor(int slot) {
        if (slot == ToolsMenu.ROD_SLOT) {
            return WandItem.create(plugin);
        }
        if (slot == ToolsMenu.CRYSTAL_SLOT) {
            return OnOffCrystal.create(plugin);
        }
        if (slot == ToolsMenu.BOOK_SLOT) {
            return BookItem.create(plugin);
        }
        if (slot == ToolsMenu.ADMIN_BOOK_SLOT) {
            return AdminBookItem.create(plugin);
        }
        return null;
    }

    /** The permission ToolsMenu requires before it'll put a real item in this slot, or null for a filler-only slot. */
    private String permissionFor(int slot) {
        if (slot == ToolsMenu.ROD_SLOT) {
            return "forcefield.tool.rod";
        }
        if (slot == ToolsMenu.CRYSTAL_SLOT) {
            return "forcefield.tool.crystal";
        }
        if (slot == ToolsMenu.BOOK_SLOT) {
            return "forcefield.tool.book";
        }
        if (slot == ToolsMenu.ADMIN_BOOK_SLOT) {
            return "forcefield.admin";
        }
        return null;
    }
}
