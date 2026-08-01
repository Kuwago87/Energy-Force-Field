package com.tonyk.forcefield.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder for a Force Field Beacon's control GUI. Remembers the
 * zone's id (not its name, so a rename never breaks an already-open menu
 * reference) and the physical beacon block's location, needed to break it
 * on delete.
 */
public final class BeaconFieldHolder implements InventoryHolder {

    private Inventory inventory;
    private final UUID zoneId;
    private final Location beaconLocation;

    BeaconFieldHolder(UUID zoneId, Location beaconLocation) {
        this.zoneId = zoneId;
        this.beaconLocation = beaconLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public Location getBeaconLocation() {
        return beaconLocation;
    }
}
