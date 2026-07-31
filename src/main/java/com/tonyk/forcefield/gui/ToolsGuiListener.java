package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.util.FieldBook;
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
 * Handles the EFF Tools GUI. The rod, crystal, and book sit in fixed slots
 * as real, take-able items: a normal (or shift-) click picks one up like any
 * other inventory, so a player can grab all three in one visit instead of
 * re-running /eff_tools for each. The slot is refilled with a fresh item a
 * tick later, but only once it's confirmed empty - so nothing duplicates if
 * the player's inventory was full and the item never actually left.
 */
public final class ToolsGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;

    public ToolsGuiListener(JavaPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
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

        int slot = event.getSlot();
        if (slot != ToolsMenu.ROD_SLOT && slot != ToolsMenu.CRYSTAL_SLOT && slot != ToolsMenu.BOOK_SLOT) {
            // Filler pane slot - don't let it be taken.
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
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
                topInventory.setItem(slot, freshItemFor(slot, player));
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

    private ItemStack freshItemFor(int slot, Player player) {
        if (slot == ToolsMenu.ROD_SLOT) {
            return WandItem.create(plugin);
        }
        if (slot == ToolsMenu.CRYSTAL_SLOT) {
            return OnOffCrystal.create(plugin);
        }
        if (slot == ToolsMenu.BOOK_SLOT) {
            return FieldBook.build(fields, player);
        }
        return null;
    }
}
