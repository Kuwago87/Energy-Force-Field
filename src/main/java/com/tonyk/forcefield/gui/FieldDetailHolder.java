package com.tonyk.forcefield.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder for a single field's management GUI (location / remote
 * on-off / delete). Remembers which list page to return to on "back" - or,
 * if opened by double-left-clicking a lectern, the location of that lectern
 * instead, so the last slot becomes "Remove this lectern" rather than a
 * back button.
 */
public final class FieldDetailHolder implements InventoryHolder {

    private Inventory inventory;
    private final String zoneName;
    private final int returnPage;
    private final boolean adminView;
    private final Location lecternLocation;

    FieldDetailHolder(String zoneName, int returnPage) {
        this(zoneName, returnPage, false);
    }

    FieldDetailHolder(String zoneName, int returnPage, boolean adminView) {
        this.zoneName = zoneName;
        this.returnPage = returnPage;
        this.adminView = adminView;
        this.lecternLocation = null;
    }

    FieldDetailHolder(String zoneName, boolean adminView, Location lecternLocation) {
        this.zoneName = zoneName;
        this.returnPage = 0;
        this.adminView = adminView;
        this.lecternLocation = lecternLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getZoneName() {
        return zoneName;
    }

    public int getReturnPage() {
        return returnPage;
    }

    /** True if this detail menu was reached from the admin book (adds the Change Owner button, and "back" returns to the admin list). */
    public boolean isAdminView() {
        return adminView;
    }

    /** Non-null only if this menu was opened by double-left-clicking a lectern - the location of that lectern block. */
    public Location getLecternLocation() {
        return lecternLocation;
    }
}
