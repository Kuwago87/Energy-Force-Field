package com.tonyk.forcefield.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder so listeners can reliably recognize the EFF Tools GUI
 * (rather than guessing from its title, which can change with locale/config).
 */
public final class ToolsMenuHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
