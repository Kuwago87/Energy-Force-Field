package com.tonyk.forcefield.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder for the admin book's category chooser - the small screen
 * that opens first, letting an admin pick "Rod Fields" or "Beacon
 * Generators" before seeing an actual list. Keeps the two types from ever
 * appearing mixed together in the same paginated view.
 */
public final class AdminCategoryHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
