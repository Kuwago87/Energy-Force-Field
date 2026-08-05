package com.tonyk.forcefield.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the relative block offsets forming a hollow, roughly 1-block-thick
 * sphere shell of a given integer radius, centered on (0,0,0) - i.e. a
 * "bubble", not a solid ball. Cost stays proportional to surface area
 * (~4*pi*r^2) rather than volume - the difference between a few hundred
 * thousand blocks and tens of millions at radius 250.
 * <p>
 * Uses a three-projection sweep: for each pair of axes, every integer point
 * in the bounding disk gets a voxel placed on the sphere surface along the
 * third axis (both the + and - solution). Sweeping only one axis pair (e.g.
 * walking rings by Y height) leaves real gaps near that axis's own
 * "silhouette edge" - the poles, for a Y-height sweep, where the surface
 * runs nearly parallel to the sweep direction and consecutive rings drift
 * apart faster than the voxel grid can track - which a player could
 * literally fly through. At any given point on the sphere, at least one of
 * the three projections is far from its own silhouette edge, so the union of
 * all three has no such gaps anywhere, poles included.
 * <p>
 * Results are cached per radius since the plugin only ever offers a handful
 * of fixed presets, so the same shape gets reused across every beacon field
 * of that size on the server.
 */
public final class SphereGeometry {

    private static final Map<Integer, int[][]> CACHE = new HashMap<>();
    private static final Map<Integer, int[][]> CACHE_TOP_DOWN = new HashMap<>();
    private static final Map<Integer, int[][]> BALL_CACHE = new HashMap<>();

    private SphereGeometry() {
    }

    public static synchronized int[][] hollowShellOffsets(int radius) {
        int[][] cached = CACHE.get(radius);
        if (cached != null) {
            return cached;
        }
        int[][] generated = generate(Math.max(1, radius));
        CACHE.put(radius, generated);
        return generated;
    }

    /**
     * Every integer point on or inside a solid ball of the given radius,
     * centered on (0,0,0) - unlike {@link #hollowShellOffsets}'s thin
     * surface-only shell, this is the full interior volume. Cost scales with
     * volume (~4/3*pi*r^3) rather than surface area, so it's only ever used
     * for underwater beacon fields, which are deliberately capped to a small
     * radius (see beacon-field-underwater-max-radius in config.yml) to keep
     * this cheap - it's what lets an underwater bubble drain the water out
     * of its interior when raised, and let it flow back in when lowered.
     * Results are cached per radius, same as the shell offsets.
     */
    public static synchronized int[][] solidBallOffsets(int radius) {
        int[][] cached = BALL_CACHE.get(radius);
        if (cached != null) {
            return cached;
        }
        int[][] generated = generateBall(Math.max(0, radius));
        BALL_CACHE.put(radius, generated);
        return generated;
    }

    private static int[][] generateBall(int radius) {
        List<int[]> points = new ArrayList<>();
        long r2 = (long) radius * radius;
        for (int x = -radius; x <= radius; x++) {
            long remX = r2 - (long) x * x;
            if (remX < 0) {
                continue;
            }
            for (int y = -radius; y <= radius; y++) {
                long remY = remX - (long) y * y;
                if (remY < 0) {
                    continue;
                }
                int zMax = (int) Math.sqrt((double) remY);
                for (int z = -zMax; z <= zMax; z++) {
                    points.add(new int[]{x, y, z});
                }
            }
        }
        return points.toArray(new int[0][]);
    }

    /**
     * Same offsets as {@link #hollowShellOffsets}, sorted highest-Y-first.
     * Used when raising a beacon field so the shell visibly forms from the
     * top down, one horizontal band at a time, instead of in whatever
     * incidental order the shape was generated in.
     */
    public static synchronized int[][] hollowShellOffsetsTopDown(int radius) {
        int[][] cached = CACHE_TOP_DOWN.get(radius);
        if (cached != null) {
            return cached;
        }
        int[][] sorted = hollowShellOffsets(radius).clone();
        Arrays.sort(sorted, Comparator.<int[]>comparingInt(o -> o[1]).reversed());
        CACHE_TOP_DOWN.put(radius, sorted);
        return sorted;
    }

    private static int[][] generate(int radius) {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> points = new ArrayList<>();
        long r2 = (long) radius * radius;

        for (int a = -radius; a <= radius; a++) {
            long aRem = r2 - (long) a * a;
            if (aRem < 0) {
                continue;
            }
            for (int b = -radius; b <= radius; b++) {
                long rem = aRem - (long) b * b;
                if (rem < 0) {
                    continue;
                }
                int c = (int) Math.round(Math.sqrt(rem));
                // sweep X,Y -> solve Z
                addPoint(seen, points, a, b, c);
                addPoint(seen, points, a, b, -c);
                // sweep X,Z -> solve Y
                addPoint(seen, points, a, c, b);
                addPoint(seen, points, a, -c, b);
                // sweep Y,Z -> solve X
                addPoint(seen, points, c, a, b);
                addPoint(seen, points, -c, a, b);
            }
        }

        return points.toArray(new int[0][]);
    }

    /**
     * Builds the shell of one-or-more spheres merged into a single "bubble
     * cluster", in absolute world coordinates. Each entry of {@code components}
     * is {@code {centerX, centerY, centerZ, radius}}. Any shell voxel of one
     * component that falls inside another component gets dropped entirely -
     * that's the "wall" between two overlapping bubbles, so removing it is
     * what makes two merged Force Field Beacons read as one continuous space
     * instead of two separate sealed spheres touching each other. With a
     * single component this reduces to exactly the same shape as translating
     * {@link #hollowShellOffsets} by that component's center.
     * <p>
     * The interior test deliberately errs conservative (only strips a point
     * once it's a half-block or more inside the other sphere, not merely
     * near its surface) - an earlier version used a tolerance that leaned
     * the other way (stripping anything within half a block of the other's
     * surface, including points genuinely outside it) and that consistently
     * carved a real, player-passable gap in the outer dome right along the
     * seam where the two spheres actually meet, verified and fixed by a
     * Python BFS flood-fill sweep across dozens of radius/offset
     * combinations (see the delivery notes) the same way the original
     * pole-gap bug was tracked down.
     */
    public static int[][] combinedShellWorldPoints(int[][] components) {
        List<int[]> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < components.length; i++) {
            int[] self = components[i];
            int[][] local = hollowShellOffsets(self[3]);

            outer:
            for (int[] off : local) {
                int wx = self[0] + off[0];
                int wy = self[1] + off[1];
                int wz = self[2] + off[2];

                for (int j = 0; j < components.length; j++) {
                    if (j == i) {
                        continue;
                    }
                    int[] other = components[j];
                    double dx = wx - other[0];
                    double dy = wy - other[1];
                    double dz = wz - other[2];
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance <= other[3] - 0.5) {
                        // Clearly inside another component (not just near its
                        // surface) - this voxel is part of the removed wall,
                        // not the outer shell of the merged cluster.
                        continue outer;
                    }
                }

                String key = wx + "," + wy + "," + wz;
                if (seen.add(key)) {
                    result.add(new int[]{wx, wy, wz});
                }
            }
        }

        return result.toArray(new int[0][]);
    }

    private static void addPoint(Set<Long> seen, List<int[]> points, int dx, int dy, int dz) {
        if (seen.add(encode(dx, dy, dz))) {
            points.add(new int[]{dx, dy, dz});
        }
    }

    /** Packs a signed (dx,dy,dz) triple (each comfortably within +-1,048,575) into one long for cheap deduping. */
    private static long encode(int dx, int dy, int dz) {
        long ex = (dx + 1_048_576L) & 0x1FFFFFL;
        long ey = (dy + 1_048_576L) & 0x1FFFFFL;
        long ez = (dz + 1_048_576L) & 0x1FFFFFL;
        return (ex << 42) | (ey << 21) | ez;
    }
}
