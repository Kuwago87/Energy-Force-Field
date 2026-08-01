package com.tonyk.forcefield.tasks;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically decorates every raised force field with a faint particle
 * shimmer on its surface and an occasional ambient hum, so a raised shield
 * reads as "energized" rather than just a wall of invisible blocks.
 */
public final class AmbientEffectTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final EffectService effects;
    private int tickCounter = 0;

    public AmbientEffectTask(JavaPlugin plugin, FieldManager fields, EffectService effects) {
        this.plugin = plugin;
        this.fields = fields;
        this.effects = effects;
    }

    @Override
    public void run() {
        tickCounter++;
        boolean playHumThisPass = tickCounter % 3 == 0;
        int particlesPerZone = Math.max(0, plugin.getConfig().getInt("ambient-particle-count", 60));

        for (ForceFieldZone zone : fields.getZones().values()) {
            if (!zone.isEnabled()) {
                continue;
            }
            World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
            if (world == null) {
                continue;
            }

            Cuboid c = zone.getCuboid();
            if (zone.isSpherical()) {
                int spherePasses = sphereParticleCount(zone);
                for (int i = 0; i < spherePasses; i++) {
                    effects.ambientPulse(world, randomSphereSurfacePoint(world, zone));
                }
            } else {
                for (int i = 0; i < particlesPerZone; i++) {
                    effects.ambientPulse(world, randomSurfacePoint(world, c));
                }
            }

            if (playHumThisPass) {
                Location center = new Location(world,
                        (c.getMinX() + c.getMaxX()) / 2.0 + 0.5,
                        (c.getMinY() + c.getMaxY()) / 2.0 + 0.5,
                        (c.getMinZ() + c.getMaxZ()) / 2.0 + 0.5);
                double radius = plugin.getConfig().getDouble("ambient-sound-radius", 16);
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) <= radius * radius) {
                        effects.ambientHumTo(player, center, 0.4f);
                    }
                }
            }
        }
    }

    /**
     * A bubble's surface area dwarfs a typical cuboid selection's, so reusing
     * the flat ambient-particle-count default would look almost invisible
     * once scattered across it - scale the per-pass particle count with the
     * shell's actual block count instead, within configurable floor/cap.
     * Uses the zone's combined shell, so a merged (multi-beacon) bubble's
     * count reflects its real (smaller, wall-removed) surface rather than
     * the sum of each component's stand-alone sphere.
     */
    private int sphereParticleCount(ForceFieldZone zone) {
        int shellBlocks = fields.getCombinedShell(zone).length;
        int blocksPerParticle = Math.max(1, plugin.getConfig().getInt("beacon-ambient-blocks-per-particle", 8));
        int cap = Math.max(1, plugin.getConfig().getInt("beacon-ambient-particle-cap", 20000));
        int scaled = shellBlocks / blocksPerParticle;
        return Math.max(200, Math.min(cap, scaled));
    }

    /** Picks a random point on a spherical zone's actual (combined) shell, reusing the same cached shape used to fill/restore it. */
    private Location randomSphereSurfacePoint(World world, ForceFieldZone zone) {
        int[][] shell = fields.getCombinedShell(zone);
        int[] p = shell[ThreadLocalRandom.current().nextInt(shell.length)];
        return new Location(world, p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
    }

    private Location randomSurfacePoint(World world, Cuboid c) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int face = rnd.nextInt(6);
        double x, y, z;

        double spanX = Math.max(c.getMaxX() - c.getMinX(), 0.001);
        double spanY = Math.max(c.getMaxY() - c.getMinY(), 0.001);
        double spanZ = Math.max(c.getMaxZ() - c.getMinZ(), 0.001);

        x = c.getMinX() + rnd.nextDouble(spanX + 1);
        y = c.getMinY() + rnd.nextDouble(spanY + 1);
        z = c.getMinZ() + rnd.nextDouble(spanZ + 1);

        switch (face) {
            case 0 -> x = c.getMinX();
            case 1 -> x = c.getMaxX() + 1;
            case 2 -> y = c.getMinY();
            case 3 -> y = c.getMaxY() + 1;
            case 4 -> z = c.getMinZ();
            default -> z = c.getMaxZ() + 1;
        }

        return new Location(world, x, y, z);
    }
}
