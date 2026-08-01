package com.tonyk.forcefield.tasks;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs frequently (every few ticks, much faster than {@link AmbientEffectTask})
 * to keep the top and bottom perimeter of every raised cuboid field traced
 * with tight, un-jittered particles, and to draw a raised beacon (spherical)
 * field's vertical "power on" beam. Minecraft particles fade out within
 * roughly a second, so this needs a short period to look like a continuously
 * visible line rather than a flicker. The cost per pass only scales with a
 * cuboid field's perimeter (not its volume) or a beam's fixed height, so it
 * stays cheap even for large fields.
 */
public final class EdgeOutlineTask extends BukkitRunnable {

    private static final double STEP = 0.5;

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final EffectService effects;

    public EdgeOutlineTask(JavaPlugin plugin, FieldManager fields, EffectService effects) {
        this.plugin = plugin;
        this.fields = fields;
        this.effects = effects;
    }

    @Override
    public void run() {
        for (ForceFieldZone zone : fields.getZones().values()) {
            if (!zone.isEnabled()) {
                continue;
            }
            World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
            if (world == null) {
                continue;
            }
            // A beacon (spherical) field draws a vertical beam instead of
            // tracing its bounding cube's edges - a box outline would float
            // around an invisible corner, nowhere near the actual round
            // shell.
            if (zone.isSpherical()) {
                if (beamEnabled()) {
                    drawBeam(world, zone);
                }
                continue;
            }
            outline(world, zone.getCuboid());
        }
    }

    private boolean beamEnabled() {
        return plugin.getConfig().getBoolean("beacon-beam-enabled", true);
    }

    private double beamSpacing() {
        return Math.max(0.1, plugin.getConfig().getDouble("beacon-beam-spacing", 1.0));
    }

    /**
     * Draws a straight vertical line of particles from each of the zone's
     * beacons up to the top of its own bubble, then stops there - it reads
     * as energy radiating from the generator to power the shell, rather than
     * a beam poking out through the dome into the sky. A merged (multi-
     * beacon) zone draws one beam per beacon, each at its own component's
     * radius. While a field is mid "charge-up" (see FieldManager#raiseSphere),
     * every beam extends from 0 up to its own full length first (a smaller
     * bubble's beam simply finishes first and holds there), before the shell
     * itself starts forming.
     */
    private void drawBeam(World world, ForceFieldZone zone) {
        double spacing = beamSpacing();
        int elapsed = fields.getBeamLength(zone);
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            if (!c.isEnabled()) {
                continue;
            }
            double x = c.getX() + 0.5;
            double z = c.getZ() + 0.5;
            double startY = c.getY() + 0.5;
            int length = Math.min(elapsed, c.getRadius());
            double topY = c.getY() + length;
            for (double y = startY; y <= topY; y += spacing) {
                effects.beaconBeamPulse(world, new Location(world, x, y, z));
            }
        }
    }

    private void outline(World world, Cuboid c) {
        double minX = c.getMinX();
        double maxX = c.getMaxX() + 1.0;
        double minZ = c.getMinZ();
        double maxZ = c.getMaxZ() + 1.0;
        double bottomY = c.getMinY();
        double topY = c.getMaxY() + 1.0;

        for (double y : new double[]{bottomY, topY}) {
            for (double x = minX; x <= maxX; x += STEP) {
                effects.edgePulse(world, new Location(world, x, y, minZ));
                effects.edgePulse(world, new Location(world, x, y, maxZ));
            }
            for (double z = minZ; z <= maxZ; z += STEP) {
                effects.edgePulse(world, new Location(world, minX, y, z));
                effects.edgePulse(world, new Location(world, maxX, y, z));
            }
        }
    }
}
