package com.tonyk.forcefield.tasks;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs frequently (every few ticks, much faster than {@link AmbientEffectTask})
 * to keep the top and bottom perimeter of every raised field traced with
 * tight, un-jittered particles. Minecraft particles fade out within roughly
 * a second, so this needs a short period to look like a continuously visible
 * line rather than a flicker. The cost per pass only scales with a field's
 * perimeter (not its volume), so it stays cheap even for large fields.
 */
public final class EdgeOutlineTask extends BukkitRunnable {

    private static final double STEP = 0.5;

    private final FieldManager fields;
    private final EffectService effects;

    public EdgeOutlineTask(FieldManager fields, EffectService effects) {
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
            outline(world, zone.getCuboid());
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
