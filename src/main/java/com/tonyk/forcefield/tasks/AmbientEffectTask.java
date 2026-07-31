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
            for (int i = 0; i < particlesPerZone; i++) {
                effects.ambientPulse(world, randomSurfacePoint(world, c));
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
