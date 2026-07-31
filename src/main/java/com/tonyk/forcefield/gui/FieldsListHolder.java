package com.tonyk.forcefield.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Marker holder for the paginated "My Energy Force Fields" list GUI. Carries
 * a snapshot of the owner's zone names (taken when the menu was built) and
 * the current page, so clicks can be resolved without re-querying in a way
 * that could shift items mid-navigation.
 */
public final class FieldsListHolder implements InventoryHolder {

    private Inventory inventory;
    private final List<String> zoneNames;
    private final int page;
    private final boolean adminView;

    FieldsListHolder(List<String> zoneNames, int page) {
        this(zoneNames, page, false);
    }

    FieldsListHolder(List<String> zoneNames, int page, boolean adminView) {
        this.zoneNames = zoneNames;
        this.page = page;
        this.adminView = adminView;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public List<String> getZoneNames() {
        return zoneNames;
    }

    public int getPage() {
        return page;
    }

    /** True if this list was opened from the admin book (shows every zone, not just the viewer's own). */
    public boolean isAdminView() {
        return adminView;
    }
}
