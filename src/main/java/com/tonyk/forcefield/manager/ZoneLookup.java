package com.tonyk.forcefield.manager;

import com.tonyk.forcefield.model.ForceFieldZone;
import org.bukkit.Location;

/**
 * Small helper mixed into FieldManager's public surface via composition -
 * kept as a static utility so listeners don't need the whole manager API.
 */
public final class ZoneLookup {

    private ZoneLookup() {
    }

    public static ForceFieldZone findEnabledZoneContaining(FieldManager manager, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        for (ForceFieldZone zone : manager.getZones().values()) {
            if (zone.isEnabled() && zone.getCuboid().contains(loc)) {
                return zone;
            }
        }
        return null;
    }
}
