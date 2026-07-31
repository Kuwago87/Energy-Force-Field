package com.tonyk.forcefield.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple axis-aligned block region between two corners, inclusive.
 */
public final class Cuboid {

    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid fromLocations(Location a, Location b) {
        return new Cuboid(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String getWorldName() {
        return world;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public long volume() {
        long dx = (long) (maxX - minX + 1);
        long dy = (long) (maxY - minY + 1);
        long dz = (long) (maxZ - minZ + 1);
        return dx * dy * dz;
    }

    public boolean containsBlock(World w, int x, int y, int z) {
        if (!w.getName().equals(world)) {
            return false;
        }
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        return containsBlock(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Squared distance from a location to the nearest point on this cuboid
     * (0 if the location is inside it). Returns Double.MAX_VALUE if the
     * location isn't even in the same world, so it never wins a "nearest"
     * comparison.
     */
    public double distanceSquaredFrom(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double cx = clamp(loc.getX(), minX, maxX + 1.0);
        double cy = clamp(loc.getY(), minY, maxY + 1.0);
        double cz = clamp(loc.getZ(), minZ, maxZ + 1.0);
        double dx = loc.getX() - cx;
        double dy = loc.getY() - cy;
        double dz = loc.getZ() - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Distance along {@code direction} from {@code origin} to where a ray
     * first enters this cuboid (the classic "slab method" ray/AABB test),
     * or -1 if the ray never enters it within {@code maxDistance} (including
     * if it's simply pointed the wrong way, or is in a different world).
     * Used by the On/Off remote to find the field the player is actually
     * looking at, not just the nearest one.
     */
    public double raycastDistance(Location origin, Vector direction, double maxDistance) {
        if (origin.getWorld() == null || !origin.getWorld().getName().equals(world)) {
            return -1;
        }

        double tMin = 0.0;
        double tMax = maxDistance;

        double[] originCoords = {origin.getX(), origin.getY(), origin.getZ()};
        double[] dirCoords = {direction.getX(), direction.getY(), direction.getZ()};
        double[] boundsMin = {minX, minY, minZ};
        double[] boundsMax = {maxX + 1.0, maxY + 1.0, maxZ + 1.0};

        for (int axis = 0; axis < 3; axis++) {
            double o = originCoords[axis];
            double d = dirCoords[axis];
            double lo = boundsMin[axis];
            double hi = boundsMax[axis];

            if (Math.abs(d) < 1e-9) {
                if (o < lo || o > hi) {
                    return -1;
                }
                continue;
            }

            double t1 = (lo - o) / d;
            double t2 = (hi - o) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return -1;
            }
        }

        return tMin;
    }

    /**
     * Returns every block in the cuboid for the given world. Caller is responsible
     * for making sure the world matches {@link #getWorldName()}.
     */
    public List<Block> getBlocks(World w) {
        List<Block> blocks = new ArrayList<>((int) Math.min(volume(), Integer.MAX_VALUE - 8));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(w.getBlockAt(x, y, z));
                }
            }
        }
        return blocks;
    }
}
