package com.tonyk.forcefield.manager;

import com.tonyk.forcefield.model.ForceFieldZone;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Small helper mixed into FieldManager's public surface via composition -
 * kept as a static utility so listeners don't need the whole manager API.
 */
public final class ZoneLookup {

    private ZoneLookup() {
    }

    /**
     * Finds the enabled zone (if any) whose barrier actually occupies this
     * location. Both shape- and material-aware: a cuboid zone only matches
     * a BARRIER block within its box, and a sphere zone only matches its own
     * (configurable) shell material precisely on its shell - not just
     * anywhere in its bounding cube. That pairing matters now that a beacon
     * field's shell is a real placeable material (stained glass by default):
     * without it, an unrelated matching block a player happens to have built
     * somewhere else inside the same bounding cube could get "protected" by
     * mistake.
     */
    public static ForceFieldZone findEnabledZoneContaining(FieldManager manager, Location loc,
                                                             Material blockMaterial, Material sphereShellMaterial) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        for (ForceFieldZone zone : manager.getZones().values()) {
            if (!zone.isEnabled()) {
                continue;
            }
            if (zone.isSpherical()) {
                if (blockMaterial == sphereShellMaterial && isOnSphereShell(zone, loc)) {
                    return zone;
                }
            } else if (blockMaterial == Material.BARRIER && zone.getCuboid().contains(loc)) {
                return zone;
            }
        }
        return null;
    }

    /** Checked against every currently-raised component of a (possibly merged, multi-beacon) spherical zone - a disabled component's sphere doesn't protect anything even if a merged neighbor is still up. */
    private static boolean isOnSphereShell(ForceFieldZone zone, Location loc) {
        if (!loc.getWorld().getName().equals(zone.getCuboid().getWorldName())) {
            return false;
        }
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            if (!c.isEnabled()) {
                continue;
            }
            double dx = loc.getBlockX() - c.getX();
            double dy = loc.getBlockY() - c.getY();
            double dz = loc.getBlockZ() - c.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // Generous-ish tolerance: SphereGeometry's sweep generator rounds
            // to integer block coordinates, so genuine shell blocks can land
            // up to roughly this far from the mathematically exact radius.
            if (Math.abs(distance - c.getRadius()) <= 1.5) {
                return true;
            }
        }
        return false;
    }
}
