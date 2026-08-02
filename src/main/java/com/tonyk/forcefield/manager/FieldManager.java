package com.tonyk.forcefield.manager;

import com.tonyk.forcefield.model.FieldShape;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
import com.tonyk.forcefield.util.SphereFillTask;
import com.tonyk.forcefield.util.SphereGeometry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns the set of force field zones: persistence to fields.yml, creation
 * (with baseline block-state capture), and raising/lowering the barrier.
 */
public final class FieldManager {

    private final JavaPlugin plugin;
    private final EffectService effects;
    private final File file;
    private final Map<String, ForceFieldZone> zones = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // Zone ids with an in-flight ticked sphere raise/lower/resize/toggle -
    // guards against overlapping SphereFillTasks on the same zone if a
    // player mashes GUI buttons (its own or another merged beacon's) while
    // one is still running.
    private final Set<UUID> busySphereZones = new HashSet<>();

    // Zone ids currently mid-"beam charge-up" (see raiseSphere, the bulk
    // whole-zone raise) mapped to how far the beam currently extends, in
    // blocks. Removed once charging finishes; absence means "not charging"
    // (getBeamLength then reports Integer.MAX_VALUE, so every component's
    // beam draws at its own full length). Individual per-beacon toggles
    // (setComponentEnabled) don't use this - only a bulk raise() does.
    private final Map<UUID, Integer> beamProgress = new HashMap<>();

    // A merged (multi-beacon) zone's shell has no simple per-radius formula,
    // so it's computed from the zone's current component list and cached
    // here, keyed by zone id - invalidated any time that zone's components
    // (or any component's enabled state) change. "All" = every component
    // regardless of on/off, used for baseline capture and bulk raise/lower.
    // "Enabled" = only the components currently raised, i.e. the shape
    // that's actually built and needs protecting right now.
    private final Map<UUID, int[][]> shellCacheAll = new HashMap<>();
    private final Map<UUID, int[][]> shellCacheAllTopDown = new HashMap<>();
    private final Map<UUID, int[][]> shellCacheEnabled = new HashMap<>();

    public FieldManager(JavaPlugin plugin, EffectService effects) {
        this.plugin = plugin;
        this.effects = effects;
        this.file = new File(plugin.getDataFolder(), "fields.yml");
        load();
    }

    public Map<String, ForceFieldZone> getZones() {
        return zones;
    }

    public ForceFieldZone getZone(String name) {
        return zones.get(name);
    }

    /**
     * Looks up a zone by its stable internal id, rather than its (renameable)
     * name. Used to resolve physical lecterns, which are linked by id so a
     * rename never breaks an already-placed one.
     */
    public ForceFieldZone getZoneById(UUID id) {
        if (id == null) {
            return null;
        }
        for (ForceFieldZone zone : zones.values()) {
            if (id.equals(zone.getId())) {
                return zone;
            }
        }
        return null;
    }

    public boolean exists(String name) {
        return zones.containsKey(name);
    }

    /**
     * Finds the zone (if any) linked to redstone at the given block location.
     */
    public ForceFieldZone findByRedstoneLocation(String world, int x, int y, int z) {
        for (ForceFieldZone zone : zones.values()) {
            if (zone.hasRedstoneLink()
                    && zone.getRedstoneWorld().equals(world)
                    && zone.getRedstoneX() == x
                    && zone.getRedstoneY() == y
                    && zone.getRedstoneZ() == z) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Finds the closest zone to a location, within maxRange blocks. Used by
     * the On/Off Crystal and the Create/Delete Rod's delete action so players
     * don't have to click an exact block.
     */
    public ForceFieldZone findNearestZone(Location loc, double maxRange) {
        ForceFieldZone nearest = null;
        double nearestDistSq = maxRange * maxRange;
        for (ForceFieldZone zone : zones.values()) {
            double d = zone.getCuboid().distanceSquaredFrom(loc);
            if (d <= nearestDistSq) {
                nearest = zone;
                nearestDistSq = d;
            }
        }
        return nearest;
    }

    /**
     * Finds the zone the player is actually looking at, within maxRange
     * blocks - a ray cast from {@code origin} along {@code direction},
     * picking whichever zone it enters first. Unlike {@link #findNearestZone},
     * this ignores fields that are merely nearby but not in view, so standing
     * between two fields and aiming at one won't accidentally hit the other.
     * Used by the On/Off remote.
     */
    public ForceFieldZone findFacingZone(Location origin, Vector direction, double maxRange) {
        ForceFieldZone closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (ForceFieldZone zone : zones.values()) {
            double distance = zone.getCuboid().raycastDistance(origin, direction, maxRange);
            if (distance >= 0 && distance < closestDistance) {
                closest = zone;
                closestDistance = distance;
            }
        }
        return closest;
    }

    public List<ForceFieldZone> getZonesOwnedBy(UUID uuid) {
        List<ForceFieldZone> result = new ArrayList<>();
        for (ForceFieldZone zone : zones.values()) {
            if (zone.isOwnedBy(uuid)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Total number of Force Field Beacons a player currently has placed,
     * across every spherical zone they own (a merged zone with two beacons
     * counts as two, regardless of whether either is currently raised).
     * Used to enforce beacon-field-max-per-player.
     */
    public int countPlayerBeacons(UUID uuid) {
        int count = 0;
        for (ForceFieldZone zone : zones.values()) {
            if (zone.isSpherical() && zone.isOwnedBy(uuid)) {
                count += zone.getSphereComponents().size();
            }
        }
        return count;
    }

    /**
     * Finds a spherical zone owned by {@code uuid} whose bubble currently
     * contains {@code loc} - i.e. the location is within some component's
     * radius of its center, regardless of whether that component is
     * currently raised. Used when placing a new beacon: if it lands inside
     * one of the player's own existing bubbles, it merges into that zone
     * (adding a new component) instead of creating a brand new one. Only
     * ever matches the placer's own zones, never another player's.
     */
    public ForceFieldZone findOwnedSphereZoneContaining(UUID uuid, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        for (ForceFieldZone zone : zones.values()) {
            if (!zone.isSpherical() || !zone.isOwnedBy(uuid)) {
                continue;
            }
            if (!world.getName().equals(zone.getCuboid().getWorldName())) {
                continue;
            }
            for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
                double dx = loc.getBlockX() - c.getX();
                double dy = loc.getBlockY() - c.getY();
                double dz = loc.getBlockZ() - c.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= (double) c.getRadius() * c.getRadius()) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * Generates a free zone name based on the player's name, e.g. "tony-1",
     * "tony-2", ... for the Create/Delete Rod's quick-create action.
     */
    public String generateZoneName(Player player) {
        String base = player.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "field";
        }
        int n = 1;
        String candidate;
        do {
            candidate = base + "-" + n;
            n++;
        } while (exists(candidate));
        return candidate;
    }

    /**
     * Renames a zone, keeping the name-to-zone map key in sync. Returns false
     * (and changes nothing) if oldName doesn't exist or newName is already
     * taken by a different zone.
     */
    public boolean renameZone(String oldName, String newName) {
        ForceFieldZone zone = zones.get(oldName);
        if (zone == null) {
            return false;
        }
        if (!oldName.equalsIgnoreCase(newName) && exists(newName)) {
            return false;
        }
        zones.remove(oldName);
        zone.setName(newName);
        zones.put(newName, zone);
        save();
        return true;
    }

    /**
     * Sets whether anyone can toggle this zone via its lecterns (true), or
     * only the owner/an admin (false, the default).
     */
    public void setPublic(ForceFieldZone zone, boolean isPublic) {
        zone.setPublic(isPublic);
        save();
    }

    /**
     * Transfers ownership of a zone to a different player. Used by the admin
     * book's Change Owner button. Returns false if the zone doesn't exist.
     */
    public boolean changeOwner(String zoneName, UUID newOwnerUuid, String newOwnerName) {
        ForceFieldZone zone = zones.get(zoneName);
        if (zone == null) {
            return false;
        }
        zone.setOwner(newOwnerUuid, newOwnerName);
        save();
        return true;
    }

    /**
     * Creates a new (initially lowered) zone, capturing the current block
     * states in the cuboid as the "shields down" baseline.
     */
    public ForceFieldZone createZone(String name, Cuboid cuboid, UUID ownerUuid, String ownerName) {
        World world = Bukkit.getWorld(cuboid.getWorldName());
        if (world == null) {
            throw new IllegalStateException("World '" + cuboid.getWorldName() + "' is not loaded.");
        }

        Map<String, String> baseline = new LinkedHashMap<>();
        for (int x = cuboid.getMinX(); x <= cuboid.getMaxX(); x++) {
            for (int y = cuboid.getMinY(); y <= cuboid.getMaxY(); y++) {
                for (int z = cuboid.getMinZ(); z <= cuboid.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    String key = ForceFieldZone.key(x - cuboid.getMinX(), y - cuboid.getMinY(), z - cuboid.getMinZ());
                    baseline.put(key, block.getBlockData().getAsString());
                }
            }
        }

        ForceFieldZone zone = new ForceFieldZone(UUID.randomUUID(), name, cuboid, false, baseline);
        zone.setOwner(ownerUuid, ownerName);
        zones.put(name, zone);
        save();
        return zone;
    }

    /**
     * Creates a new (initially lowered) spherical "bubble" zone with a
     * single beacon component centered on {@code center} - used by the Force
     * Field Beacon when it's not placed inside one of the player's existing
     * bubbles. Unlike a cuboid zone, only the hollow shell's blocks are
     * captured/touched, never the solid interior (a filled ball at a 250
     * radius would be tens of millions of blocks).
     */
    public ForceFieldZone createSphereZone(String name, Location center, int radius, UUID ownerUuid, String ownerName) {
        World world = center.getWorld();
        if (world == null) {
            throw new IllegalStateException("Beacon location has no world.");
        }

        ForceFieldZone zone = new ForceFieldZone(UUID.randomUUID(), name, world.getName(), false, new LinkedHashMap<>());
        ForceFieldZone.SphereComponent component = new ForceFieldZone.SphereComponent(
                UUID.randomUUID(), center.getBlockX(), center.getBlockY(), center.getBlockZ(), radius, false);
        zone.addSphereComponent(component);
        zone.setOwner(ownerUuid, ownerName);

        Map<String, String> baseline = captureSphereBaseline(world, zone.getSphereComponents());
        zone.getBaseline().putAll(baseline);

        zones.put(name, zone);
        save();
        return zone;
    }

    /**
     * Merges a newly-placed beacon into an existing spherical zone as an
     * extra component, instead of creating a separate zone - used when the
     * new beacon lands inside one of the player's own existing bubbles. The
     * new component always starts <b>disabled</b>, exactly like a brand new
     * zone does - it never touches any other component's live blocks, raised
     * or not. The player picks this beacon's own size and then explicitly
     * raises it via its own lever whenever they're ready; at that point
     * {@link #setComponentEnabled} works out the correct wall-removal
     * against whatever else in the group happens to already be up. This is
     * why merging never needs to disturb an already-raised bubble.
     */
    public ForceFieldZone addBeaconToZone(ForceFieldZone zone, Location loc, int radius) {
        World world = loc.getWorld();
        if (world == null) {
            throw new IllegalStateException("Beacon location has no world.");
        }

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Capture the new component's own local shell area now, purely as
        // natural terrain (it starts disabled, so nothing's built there yet).
        // Any point that's already tracked (part of another component's own
        // shell area already) keeps its original baseline untouched.
        for (int[] o : SphereGeometry.hollowShellOffsets(radius)) {
            int wx = x + o[0];
            int wy = y + o[1];
            int wz = z + o[2];
            String key = ForceFieldZone.key(wx, wy, wz);
            if (!zone.getBaseline().containsKey(key)) {
                Block block = world.getBlockAt(wx, wy, wz);
                zone.getBaseline().put(key, block.getBlockData().getAsString());
            }
        }

        ForceFieldZone.SphereComponent component = new ForceFieldZone.SphereComponent(
                UUID.randomUUID(), x, y, z, radius, false);
        zone.addSphereComponent(component);
        invalidateShellCaches(zone.getId());
        save();
        return zone;
    }

    /**
     * The material a beacon field's shell is made of when raised - visible
     * (stained glass by default) rather than the invisible BARRIER a rod
     * field uses, since a bubble is meant to be seen. Still fully solid and
     * fully protected while up (see ZoneLookup/ProtectionListener) - the
     * only difference from BARRIER is that it renders.
     */
    private Material sphereShellMaterial() {
        String name = plugin.getConfig().getString("beacon-field-shell-material", "BLUE_STAINED_GLASS");
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown beacon-field-shell-material '" + name + "' in config.yml, falling back to BLUE_STAINED_GLASS");
            return Material.BLUE_STAINED_GLASS;
        }
    }

    /** True while a sphere zone has a ticked raise/lower/resize/toggle in progress. */
    public boolean isSphereBusy(ForceFieldZone zone) {
        return busySphereZones.contains(zone.getId());
    }

    /**
     * The shell (in absolute world coordinates) actually built and protected
     * right now - the union of every <b>currently-raised</b> component in a
     * spherical zone, wall-removed wherever two of them overlap. A component
     * that's individually turned off contributes nothing here even if the
     * zone has other components raised. Exposed for effect tasks (ambient
     * shimmer) that need to sample the real, currently-visible shape.
     */
    public int[][] getCombinedShell(ForceFieldZone zone) {
        return combinedShellOfEnabled(zone);
    }

    /** Every component's shell, merged, regardless of which ones are actually raised - used for baseline capture and the bulk whole-zone raise/lower. */
    private int[][] combinedShellAll(ForceFieldZone zone) {
        int[][] cached = shellCacheAll.get(zone.getId());
        if (cached != null) {
            return cached;
        }
        int[][] generated = SphereGeometry.combinedShellWorldPoints(toComponentArray(zone.getSphereComponents()));
        shellCacheAll.put(zone.getId(), generated);
        return generated;
    }

    private int[][] combinedShellAllTopDown(ForceFieldZone zone) {
        int[][] cached = shellCacheAllTopDown.get(zone.getId());
        if (cached != null) {
            return cached;
        }
        int[][] sorted = sortTopDown(combinedShellAll(zone));
        shellCacheAllTopDown.put(zone.getId(), sorted);
        return sorted;
    }

    /** Only the currently-enabled components' merged shell - the shape that's actually built right now. */
    private int[][] combinedShellOfEnabled(ForceFieldZone zone) {
        int[][] cached = shellCacheEnabled.get(zone.getId());
        if (cached != null) {
            return cached;
        }
        List<ForceFieldZone.SphereComponent> enabledOnly = new ArrayList<>();
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            if (c.isEnabled()) {
                enabledOnly.add(c);
            }
        }
        int[][] generated = SphereGeometry.combinedShellWorldPoints(toComponentArray(enabledOnly));
        shellCacheEnabled.put(zone.getId(), generated);
        return generated;
    }

    private static int[][] sortTopDown(int[][] points) {
        int[][] sorted = points.clone();
        Arrays.sort(sorted, Comparator.<int[]>comparingInt(o -> o[1]).reversed());
        return sorted;
    }

    private void invalidateShellCaches(UUID zoneId) {
        shellCacheAll.remove(zoneId);
        shellCacheAllTopDown.remove(zoneId);
        shellCacheEnabled.remove(zoneId);
    }

    private static int[][] toComponentArray(List<ForceFieldZone.SphereComponent> components) {
        int[][] raw = new int[components.size()][];
        for (int i = 0; i < components.size(); i++) {
            ForceFieldZone.SphereComponent c = components.get(i);
            raw[i] = new int[]{c.getX(), c.getY(), c.getZ(), c.getRadius()};
        }
        return raw;
    }

    private static Set<String> toKeySet(int[][] points) {
        Set<String> set = new HashSet<>(Math.max(16, points.length * 2));
        for (int[] p : points) {
            set.add(ForceFieldZone.key(p[0], p[1], p[2]));
        }
        return set;
    }

    /**
     * Every point this (spherical) zone has ever captured a baseline entry
     * for, parsed back into absolute world coordinates - the authoritative
     * "could possibly still be live shell" set, independent of any
     * recomputed merge geometry or shell cache. A SPHERE zone's baseline
     * keys are already absolute coordinates (unlike a CUBOID zone's, which
     * are relative to its corner), so no translation is needed. Used as a
     * safety net whenever a spherical zone is being brought fully to "every
     * component off": restoring a block that's already at its baseline is a
     * harmless no-op, so sourcing the restore set from here instead of a
     * diff between two computed shells guarantees nothing gets left behind,
     * even in edge cases (e.g. a smaller beacon fully engulfed by a larger
     * one, or one beacon resealing into a complete sphere while another was
     * already off) where the old/new shell diff could otherwise disagree
     * with what's actually rendered.
     */
    private static int[][] baselinePoints(ForceFieldZone zone) {
        Set<String> keys = zone.getBaseline().keySet();
        int[][] points = new int[keys.size()][];
        int count = 0;
        for (String key : keys) {
            String[] parts = key.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                points[count] = new int[]{
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
                };
                count++;
            } catch (NumberFormatException ignored) {
                // Malformed key - skip it rather than fail the whole restore.
            }
        }
        return count == points.length ? points : Arrays.copyOf(points, count);
    }

    /** Restores every point in {@code oldShell} that isn't part of {@code newKeys} back to its baseline. */
    private void restoreDroppedShellPoints(ForceFieldZone zone, World world, int[][] oldShell, Set<String> newKeys) {
        if (oldShell == null) {
            return;
        }
        Map<String, String> baseline = zone.getBaseline();
        for (int[] p : oldShell) {
            String key = ForceFieldZone.key(p[0], p[1], p[2]);
            if (newKeys.contains(key)) {
                continue;
            }
            Block block = world.getBlockAt(p[0], p[1], p[2]);
            String dataString = baseline.get(key);
            if (dataString != null) {
                try {
                    block.setBlockData(Bukkit.createBlockData(dataString), false);
                } catch (IllegalArgumentException ex) {
                    block.setType(Material.AIR, false);
                }
            } else {
                block.setType(Material.AIR, false);
            }
        }
    }

    private Map<String, String> captureSphereBaseline(World world, List<ForceFieldZone.SphereComponent> components) {
        Map<String, String> baseline = new LinkedHashMap<>();
        for (int[] p : SphereGeometry.combinedShellWorldPoints(toComponentArray(components))) {
            Block block = world.getBlockAt(p[0], p[1], p[2]);
            baseline.put(ForceFieldZone.key(p[0], p[1], p[2]), block.getBlockData().getAsString());
        }
        return baseline;
    }

    /**
     * Flips one beacon's own component on or off, independent of every other
     * component in the same (possibly merged) zone - this is what each
     * beacon's own lever calls. Works out exactly which blocks need to
     * change by diffing the enabled-shell before and after: anything that
     * drops out (because it's now inside another still-enabled component, or
     * because nothing's enabled there anymore) is restored to baseline at
     * the normal (fast) lowering pace, and anything new (because it's no
     * longer suppressed by a neighbor turning off, or because this component
     * itself just turned on) is filled in at the normal (slow, tactical-
     * window) raising pace. Turning one beacon off while another stays on
     * simply reseals whatever gap was open between them - the remaining
     * bubble becomes a complete sphere again on its own.
     */
    public void setComponentEnabled(ForceFieldZone zone, ForceFieldZone.SphereComponent component, boolean targetEnabled) {
        if (!zone.isSpherical() || component.isEnabled() == targetEnabled || busySphereZones.contains(zone.getId())) {
            return;
        }
        World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
        if (world == null) {
            return;
        }
        setComponentEnabledInternal(zone, world, component, targetEnabled, null);
    }

    private void setComponentEnabledInternal(ForceFieldZone zone, World world, ForceFieldZone.SphereComponent component,
                                              boolean targetEnabled, Runnable extraOnComplete) {
        if (!busySphereZones.add(zone.getId())) {
            return;
        }

        int[][] oldShell = combinedShellOfEnabled(zone);
        zone.setComponentEnabled(component, targetEnabled);
        invalidateShellCaches(zone.getId());
        boolean anyEnabled = false;
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            if (c.isEnabled()) {
                anyEnabled = true;
                break;
            }
        }
        zone.setEnabled(anyEnabled);
        save();

        int[][] toRestore;
        int[][] toFill;
        if (!anyEnabled) {
            // Every component in the zone just turned off - rather than trust
            // a diff between the old and new *computed* shells (which relies
            // on the merge geometry exactly matching whatever's physically
            // live - something a resealed or fully-engulfed component can
            // throw off), restore literally every point this zone has ever
            // captured a baseline for. Re-restoring an already-baseline block
            // is a harmless no-op, so this unconditionally leaves nothing
            // behind once the whole (possibly merged) bubble is fully down.
            toRestore = baselinePoints(zone);
            toFill = new int[0][];
        } else {
            int[][][] diff = diffShells(oldShell, combinedShellOfEnabled(zone));
            toRestore = diff[0];
            toFill = diff[1];
        }

        Runnable finish = () -> {
            busySphereZones.remove(zone.getId());
            save();
            if (targetEnabled) {
                effects.playActivate(world, new Location(world, component.getX() + 0.5, component.getY() + 0.5, component.getZ() + 0.5), component.getRadius());
            } else {
                effects.playDeactivate(world, new Location(world, component.getX() + 0.5, component.getY() + 0.5, component.getZ() + 0.5), component.getRadius());
            }
            if (extraOnComplete != null) {
                extraOnComplete.run();
            }
        };

        runShellTransition(world, zone, toRestore, toFill, finish);
    }

    /**
     * Diffs two computed shells into "what needs to drop back to baseline"
     * and "what needs to be newly filled" - shared by every operation that
     * transitions a spherical zone's live shell from one shape to another
     * (a single component toggling on/off, or now resizing live while
     * raised). Returns a two-element array: {@code [toRestore, toFill]}.
     */
    private static int[][][] diffShells(int[][] oldShell, int[][] newShell) {
        Set<String> oldKeys = toKeySet(oldShell);
        Set<String> newKeys = toKeySet(newShell);

        List<int[]> toRestoreList = new ArrayList<>();
        for (int[] p : oldShell) {
            if (!newKeys.contains(ForceFieldZone.key(p[0], p[1], p[2]))) {
                toRestoreList.add(p);
            }
        }
        List<int[]> toFillList = new ArrayList<>();
        for (int[] p : newShell) {
            if (!oldKeys.contains(ForceFieldZone.key(p[0], p[1], p[2]))) {
                toFillList.add(p);
            }
        }
        return new int[][][]{
                toRestoreList.toArray(new int[0][]),
                sortTopDown(toFillList.toArray(new int[0][]))
        };
    }

    /**
     * Runs the actual two-stage ticked transition shared by every spherical
     * zone shape change: first restores {@code toRestore} to baseline (fast
     * pace), then fills {@code toFill} with the field's own material (slower,
     * tactical-window pace), then calls {@code onComplete}. Either list can
     * be empty, in which case that stage is skipped.
     */
    private void runShellTransition(World world, ForceFieldZone zone, int[][] toRestore, int[][] toFill, Runnable onComplete) {
        Runnable doFill = () -> {
            if (toFill.length == 0) {
                onComplete.run();
                return;
            }
            BlockData shell = sphereShellMaterial().createBlockData();
            int fillPerTick = Math.max(1, plugin.getConfig().getInt("sphere-raise-blocks-per-tick", 333));
            new SphereFillTask(world, 0, 0, 0, toFill, fillPerTick,
                    block -> {
                        if (block.isPassable()) {
                            block.setBlockData(shell, false);
                        }
                    },
                    onComplete
            ).start(plugin);
        };

        if (toRestore.length == 0) {
            doFill.run();
            return;
        }
        Map<String, String> baseline = zone.getBaseline();
        int restorePerTick = Math.max(1, plugin.getConfig().getInt("sphere-lower-blocks-per-tick", 8000));
        new SphereFillTask(world, 0, 0, 0, toRestore, restorePerTick,
                block -> {
                    String dataString = baseline.get(ForceFieldZone.key(block.getX(), block.getY(), block.getZ()));
                    if (dataString != null) {
                        try {
                            block.setBlockData(Bukkit.createBlockData(dataString), false);
                        } catch (IllegalArgumentException ex) {
                            block.setType(Material.AIR, false);
                        }
                    } else {
                        block.setType(Material.AIR, false);
                    }
                },
                doFill
        ).start(plugin);
    }

    /**
     * Resizes one beacon's own component within a (possibly merged)
     * spherical zone to a new radius. If it's currently raised, the change
     * applies live - the shell transitions straight from the old radius to
     * the new one (restoring whatever the old shape no longer needs and
     * filling in the new shape, exactly like a merged neighbor's wall
     * reseals when one side toggles) without ever fully coming down, so
     * there's no need to manually raise it again afterward. A merged
     * neighbor's own state is never touched either way. Does nothing if a
     * sphere operation is already in progress for this zone, or if it isn't
     * a spherical zone at all.
     */
    public void setComponentRadius(ForceFieldZone zone, ForceFieldZone.SphereComponent component, int newRadius) {
        if (!zone.isSpherical() || busySphereZones.contains(zone.getId()) || component.getRadius() == newRadius) {
            return;
        }
        World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
        if (world == null) {
            return;
        }
        if (component.isEnabled()) {
            resizeComponentLive(zone, world, component, newRadius);
        } else {
            applyComponentRadius(zone, world, component, newRadius);
        }
    }

    /** Resizes a currently-lowered component - nothing's live, so this just updates the shape and its baseline coverage. Nothing in the zone is currently rendered as shell, so every untracked point is safely real terrain. */
    private void applyComponentRadius(ForceFieldZone zone, World world, ForceFieldZone.SphereComponent component, int newRadius) {
        zone.resizeSphereComponent(component, newRadius);
        invalidateShellCaches(zone.getId());
        captureNewBaselinePoints(zone, world, component, Set.of());
    }

    /**
     * The live-resize path: captures the current shell, applies the new
     * radius, additively captures baseline for whatever of the new shape
     * isn't already tracked (see applyComponentRadius's own note on why this
     * must never clear-and-recapture), then transitions the live shell from
     * old to new via the same diff/two-stage-fill machinery every other
     * shape change uses. The component's enabled state never changes here -
     * it stays raised throughout.
     */
    private void resizeComponentLive(ForceFieldZone zone, World world, ForceFieldZone.SphereComponent component, int newRadius) {
        if (!busySphereZones.add(zone.getId())) {
            return;
        }
        int[][] oldShell = combinedShellOfEnabled(zone);
        zone.resizeSphereComponent(component, newRadius);
        invalidateShellCaches(zone.getId());
        // Skip capturing baseline for any point that's still part of the
        // *pre-resize* live shell - it hasn't been restored yet, so its
        // current block is whatever's actually raised right now (this
        // component's own old shell, or even a merged neighbor's), never
        // genuine terrain. Capturing it here would permanently bake "shell
        // material" into the baseline for that point - which no later
        // restore, however thorough, could ever see past, since it would
        // just be putting the block back to what baseline says is correct.
        // That's exactly what could leave an untouchable leftover behind
        // after a resize.
        captureNewBaselinePoints(zone, world, component, toKeySet(oldShell));
        save();

        int[][][] diff = diffShells(oldShell, combinedShellOfEnabled(zone));
        runShellTransition(world, zone, diff[0], diff[1], () -> {
            busySphereZones.remove(zone.getId());
            save();
            effects.playActivate(world, new Location(world, component.getX() + 0.5, component.getY() + 0.5, component.getZ() + 0.5), component.getRadius());
        });
    }

    /**
     * Captures baseline for one component's own full hollow shell at its
     * (already-updated) radius - only for points not already tracked, same
     * as merging a new beacon into a zone (see addBeaconToZone). Deliberately
     * <b>not</b> a clear-and-recapture via the zone's current merged/
     * wall-stripped shape: that shape excludes every overlap "wall" point by
     * design, so wiping the baseline down to just it would silently drop
     * baseline coverage for that overlap region - and a merged neighbor
     * turning off later needs exactly those points to reseal into (or fully
     * restore back out of) a complete sphere. That's precisely what once
     * left a leftover chunk of shell behind after a resize. {@code skipKeys}
     * additionally excludes any point that's still part of a not-yet-restored
     * live shell, so a currently-rendered block never gets mistaken for
     * terrain (see resizeComponentLive).
     */
    private void captureNewBaselinePoints(ForceFieldZone zone, World world, ForceFieldZone.SphereComponent component, Set<String> skipKeys) {
        for (int[] o : SphereGeometry.hollowShellOffsets(component.getRadius())) {
            int wx = component.getX() + o[0];
            int wy = component.getY() + o[1];
            int wz = component.getZ() + o[2];
            String key = ForceFieldZone.key(wx, wy, wz);
            if (skipKeys.contains(key) || zone.getBaseline().containsKey(key)) {
                continue;
            }
            Block block = world.getBlockAt(wx, wy, wz);
            zone.getBaseline().put(key, block.getBlockData().getAsString());
        }
        save();
    }

    /**
     * Removes one beacon's component from a (possibly merged) spherical
     * zone - used by its Delete button. If it's the zone's only component,
     * the whole zone is removed instead (same as before beacons could
     * merge). If that component was currently raised, whatever's left of its
     * own shell (minus whatever's still shared with a remaining raised
     * component) is restored to baseline immediately - a still-raised
     * neighbor is left completely alone.
     */
    public void removeBeaconComponent(ForceFieldZone zone, ForceFieldZone.SphereComponent component) {
        if (!zone.isSpherical()) {
            return;
        }
        if (zone.getSphereComponents().size() <= 1) {
            removeZone(zone.getName());
            return;
        }

        World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
        boolean liveUpdate = component.isEnabled() && world != null && !busySphereZones.contains(zone.getId());
        int[][] oldShell = liveUpdate ? combinedShellOfEnabled(zone) : null;

        zone.removeSphereComponent(component.getId());
        invalidateShellCaches(zone.getId());
        boolean anyEnabled = false;
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            if (c.isEnabled()) {
                anyEnabled = true;
                break;
            }
        }
        zone.setEnabled(anyEnabled);

        if (liveUpdate) {
            int[][] newShell = combinedShellOfEnabled(zone);
            Set<String> newKeys = toKeySet(newShell);
            restoreDroppedShellPoints(zone, world, oldShell, newKeys);
            // Drop the now-unused baseline entries for whatever just got
            // restored, so a stale entry can't confuse a future merge back
            // into this same area.
            Map<String, String> baseline = zone.getBaseline();
            for (int[] p : oldShell) {
                String key = ForceFieldZone.key(p[0], p[1], p[2]);
                if (!newKeys.contains(key)) {
                    baseline.remove(key);
                }
            }
        }

        save();
    }

    public void removeZone(String name) {
        ForceFieldZone zone = zones.get(name);
        if (zone == null) {
            return;
        }
        if (zone.isSpherical()) {
            World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
            if (zone.isEnabled() && world != null && !busySphereZones.contains(zone.getId())) {
                lowerSphere(zone, world, () -> {
                    zones.remove(name);
                    invalidateShellCaches(zone.getId());
                    save();
                });
            } else {
                zones.remove(name);
                invalidateShellCaches(zone.getId());
                save();
            }
            return;
        }
        if (zone.isEnabled()) {
            lower(zone);
        }
        zones.remove(name);
        save();
    }

    /**
     * Raises the shield: only the currently empty/passable blocks in the
     * cuboid become barriers - solid blocks (a door frame, the floor, the
     * ceiling, etc.) that happen to sit inside the selection are left
     * completely alone. This is checked against the live block, which at
     * raise-time always reflects the "shields down" baseline (raise/lower
     * only ever toggle between barrier and that baseline), so it's an
     * accurate read of what was originally there.
     */
    public void raise(ForceFieldZone zone) {
        World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
        if (world == null) {
            return;
        }
        if (zone.isSpherical()) {
            raiseSphere(zone, world, null);
            return;
        }
        Cuboid c = zone.getCuboid();
        BlockData barrier = Material.BARRIER.createBlockData();
        for (Block block : c.getBlocks(world)) {
            if (block.isPassable()) {
                block.setBlockData(barrier, false);
            }
        }
        zone.setEnabled(true);
        save();

        Location center = centerOf(world, c);
        double radius = Math.max(c.getMaxX() - c.getMinX(), Math.max(c.getMaxY() - c.getMinY(), c.getMaxZ() - c.getMinZ())) / 2.0 + 1;
        effects.playActivate(world, center, radius);
    }

    /**
     * Lowers the shield: every block in the cuboid is restored to its
     * captured baseline state.
     */
    public void lower(ForceFieldZone zone) {
        World world = Bukkit.getWorld(zone.getCuboid().getWorldName());
        if (world == null) {
            return;
        }
        if (zone.isSpherical()) {
            lowerSphere(zone, world, null);
            return;
        }
        Cuboid c = zone.getCuboid();
        Map<String, String> baseline = zone.getBaseline();
        for (int x = c.getMinX(); x <= c.getMaxX(); x++) {
            for (int y = c.getMinY(); y <= c.getMaxY(); y++) {
                for (int z = c.getMinZ(); z <= c.getMaxZ(); z++) {
                    String key = ForceFieldZone.key(x - c.getMinX(), y - c.getMinY(), z - c.getMinZ());
                    String dataString = baseline.get(key);
                    Block block = world.getBlockAt(x, y, z);
                    if (dataString != null) {
                        try {
                            block.setBlockData(Bukkit.createBlockData(dataString), false);
                        } catch (IllegalArgumentException ex) {
                            block.setType(Material.AIR, false);
                        }
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        zone.setEnabled(false);
        save();

        Location center = centerOf(world, c);
        double radius = Math.max(c.getMaxX() - c.getMinX(), Math.max(c.getMaxY() - c.getMinY(), c.getMaxZ() - c.getMinZ())) / 2.0 + 1;
        effects.playDeactivate(world, center, radius);
    }

    /**
     * Bulk zone-wide toggle - raises/lowers <b>every</b> component of a
     * spherical zone together (used by /forcefield toggle, redstone links,
     * and the regular field list/detail GUI, none of which know about
     * individual beacons). Each beacon's own control menu uses
     * {@link #setComponentEnabled} instead, which only ever touches that one
     * component.
     */
    public void setEnabled(ForceFieldZone zone, boolean enabled) {
        if (enabled == zone.isEnabled()) {
            return;
        }
        if (enabled) {
            raise(zone);
        } else {
            lower(zone);
        }
    }

    /**
     * Bulk-raises every component of a spherical zone together, in two
     * stages: first every component's beam "charges up" together, visibly
     * extending from each beacon to the top of where its own bubble will be
     * (a smaller component simply finishes charging first and holds there
     * while a larger one keeps growing), then only once the largest
     * component is fully extended does the shell actually start forming
     * (top-down, a batch of blocks per tick, across the whole merged shape
     * at once). The "enabled" flag (and therefore ProtectionListener's
     * coverage) flips on immediately, at the very start of the charge-up -
     * so every barrier block is protected the instant it's placed, with no
     * window where an already-formed piece of shell is breakable while the
     * rest is still filling in. The shell fill itself deliberately defaults
     * to a much slower pace than lowering (see sphere-raise-blocks-per-tick
     * vs sphere-lower-blocks-per-tick in config.yml) - it's genuinely open
     * partway through, a real window for anyone (friend or foe) to get in or
     * out before it seals, not just a cosmetic animation.
     */
    private void raiseSphere(ForceFieldZone zone, World world, Runnable extraOnComplete) {
        if (!busySphereZones.add(zone.getId())) {
            return;
        }
        zone.setEnabled(true);
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            zone.setComponentEnabled(c, true);
        }
        invalidateShellCaches(zone.getId());
        save();

        UUID zoneId = zone.getId();
        int maxRadius = 1;
        for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
            maxRadius = Math.max(maxRadius, c.getRadius());
        }
        final int chargeTarget = maxRadius;
        beamProgress.put(zoneId, 0);
        int chargePerTick = Math.max(1, plugin.getConfig().getInt("beacon-beam-charge-blocks-per-tick", 5));

        new BukkitRunnable() {
            @Override
            public void run() {
                int current = beamProgress.getOrDefault(zoneId, 0);
                int next = Math.min(chargeTarget, current + chargePerTick);
                beamProgress.put(zoneId, next);
                if (next >= chargeTarget) {
                    cancel();
                    beamProgress.remove(zoneId);
                    startSphereShellFill(zone, world, extraOnComplete);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** The actual top-down shell fill, run once a beacon field's beam(s) have finished charging up to full length. */
    private void startSphereShellFill(ForceFieldZone zone, World world, Runnable extraOnComplete) {
        // Top-down order (not the plain cache) so the shell visibly forms
        // one horizontal band at a time from the top, instead of appearing
        // in whatever scattered order the shape happens to be generated in.
        int[][] offsets = combinedShellAllTopDown(zone);
        BlockData shell = sphereShellMaterial().createBlockData();
        int perTick = Math.max(1, plugin.getConfig().getInt("sphere-raise-blocks-per-tick", 333));

        new SphereFillTask(world, 0, 0, 0, offsets, perTick,
                block -> {
                    if (block.isPassable()) {
                        block.setBlockData(shell, false);
                    }
                },
                () -> {
                    busySphereZones.remove(zone.getId());
                    save();
                    for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
                        effects.playActivate(world, new Location(world, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5), c.getRadius());
                    }
                    if (extraOnComplete != null) {
                        extraOnComplete.run();
                    }
                }
        ).start(plugin);
    }

    /**
     * How far a beacon field's beam(s) currently extend, in blocks from each
     * beacon, during a bulk whole-zone raise - grows from 0 up to the
     * largest component's radius while mid-charge-up, then reports
     * Integer.MAX_VALUE once charging finishes (or for any zone that isn't
     * currently in that charge-up phase at all - which includes every
     * individual per-beacon toggle, see setComponentEnabled) so every
     * component's beam simply draws at its own full length. Used by
     * EdgeOutlineTask, which clips each component's own beam to
     * {@code Math.min(getBeamLength(zone), component.getRadius())}.
     */
    public int getBeamLength(ForceFieldZone zone) {
        Integer progress = beamProgress.get(zone.getId());
        return progress != null ? progress : Integer.MAX_VALUE;
    }

    /**
     * Bulk-lowers every component of a spherical zone together, a batch of
     * blocks per tick, restoring each to its captured baseline. The zone
     * stays "enabled" (and therefore protected) until every block has been
     * restored, only flipping every component to disabled once the whole
     * shell is gone.
     */
    private void lowerSphere(ForceFieldZone zone, World world, Runnable extraOnComplete) {
        if (!busySphereZones.add(zone.getId())) {
            return;
        }
        // Every component ends up off after this, so - same reasoning as the
        // per-component toggle's own "last one off" case - restore from the
        // zone's full baseline record rather than the idealized merged-all
        // geometry, which can under-restore a component that had resealed
        // (or was already independently on/off) before this bulk lower ran.
        int[][] offsets = baselinePoints(zone);
        Map<String, String> baseline = zone.getBaseline();
        int perTick = Math.max(1, plugin.getConfig().getInt("sphere-lower-blocks-per-tick", 8000));

        new SphereFillTask(world, 0, 0, 0, offsets, perTick,
                block -> {
                    String dataString = baseline.get(ForceFieldZone.key(block.getX(), block.getY(), block.getZ()));
                    if (dataString != null) {
                        try {
                            block.setBlockData(Bukkit.createBlockData(dataString), false);
                        } catch (IllegalArgumentException ex) {
                            block.setType(Material.AIR, false);
                        }
                    } else {
                        block.setType(Material.AIR, false);
                    }
                },
                () -> {
                    busySphereZones.remove(zone.getId());
                    zone.setEnabled(false);
                    for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
                        zone.setComponentEnabled(c, false);
                    }
                    invalidateShellCaches(zone.getId());
                    save();
                    for (ForceFieldZone.SphereComponent c : zone.getSphereComponents()) {
                        effects.playDeactivate(world, new Location(world, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5), c.getRadius());
                    }
                    if (extraOnComplete != null) {
                        extraOnComplete.run();
                    }
                }
        ).start(plugin);
    }

    private Location centerOf(World world, Cuboid c) {
        double x = (c.getMinX() + c.getMaxX()) / 2.0 + 0.5;
        double y = (c.getMinY() + c.getMaxY()) / 2.0 + 0.5;
        double z = (c.getMinZ() + c.getMaxZ()) / 2.0 + 0.5;
        return new Location(world, x, y, z);
    }

    public void load() {
        zones.clear();
        shellCacheAll.clear();
        shellCacheAllTopDown.clear();
        shellCacheEnabled.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection zonesSection = yaml.getConfigurationSection("zones");
        if (zonesSection == null) {
            return;
        }
        boolean assignedMissingId = false;
        for (String name : zonesSection.getKeys(false)) {
            ConfigurationSection z = zonesSection.getConfigurationSection(name);
            if (z == null) {
                continue;
            }
            try {
                String world = z.getString("world");
                int x1 = z.getInt("x1");
                int y1 = z.getInt("y1");
                int z1 = z.getInt("z1");
                int x2 = z.getInt("x2");
                int y2 = z.getInt("y2");
                int z2 = z.getInt("z2");
                boolean enabled = z.getBoolean("enabled", false);

                Map<String, String> baseline = new LinkedHashMap<>();
                ConfigurationSection baselineSection = z.getConfigurationSection("baseline");
                if (baselineSection != null) {
                    for (String key : baselineSection.getKeys(false)) {
                        baseline.put(key, baselineSection.getString(key));
                    }
                }

                // Zones saved before ids existed won't have one yet - assign a
                // fresh one now and make sure it gets written back to disk.
                UUID id;
                String idString = z.getString("id");
                if (idString != null) {
                    UUID parsed;
                    try {
                        parsed = UUID.fromString(idString);
                    } catch (IllegalArgumentException ex) {
                        parsed = UUID.randomUUID();
                        assignedMissingId = true;
                    }
                    id = parsed;
                } else {
                    id = UUID.randomUUID();
                    assignedMissingId = true;
                }

                FieldShape shape;
                try {
                    shape = FieldShape.valueOf(z.getString("shape", "CUBOID"));
                } catch (IllegalArgumentException ex) {
                    shape = FieldShape.CUBOID;
                }

                ForceFieldZone zone;
                if (shape == FieldShape.SPHERE) {
                    ConfigurationSection spheresSection = z.getConfigurationSection("spheres");
                    if (spheresSection != null && !spheresSection.getKeys(false).isEmpty()) {
                        zone = new ForceFieldZone(id, name, world, enabled, baseline);
                        for (String compKey : spheresSection.getKeys(false)) {
                            ConfigurationSection cs = spheresSection.getConfigurationSection(compKey);
                            if (cs == null) {
                                continue;
                            }
                            UUID compId;
                            try {
                                compId = UUID.fromString(compKey);
                            } catch (IllegalArgumentException ex) {
                                compId = UUID.randomUUID();
                            }
                            int cx = cs.getInt("x");
                            int cy = cs.getInt("y");
                            int cz = cs.getInt("z");
                            int cRadius = Math.max(1, cs.getInt("radius", 50));
                            // Zones saved before beacons could be toggled
                            // independently (v29 and earlier) won't have a
                            // per-component "enabled" key - default to the
                            // zone's overall flag, which is accurate for
                            // those since every component was always raised
                            // or lowered in lockstep back then.
                            boolean compEnabled = cs.getBoolean("enabled", enabled);
                            zone.addSphereComponent(new ForceFieldZone.SphereComponent(compId, cx, cy, cz, cRadius, compEnabled));
                        }
                    } else {
                        // Legacy single-sphere format from before beacons
                        // could merge: a top-level "radius" plus a bounding
                        // cuboid the center was derived from. Baseline keys
                        // in this format were relative to that center rather
                        // than absolute, so they need translating too.
                        int legacyRadius = Math.max(1, z.getInt("radius", 50));
                        int cx = x1 + legacyRadius;
                        int cy = y1 + legacyRadius;
                        int cz = z1 + legacyRadius;

                        Map<String, String> migratedBaseline = new LinkedHashMap<>();
                        for (Map.Entry<String, String> entry : baseline.entrySet()) {
                            String[] parts = entry.getKey().split(",");
                            if (parts.length != 3) {
                                continue;
                            }
                            try {
                                int rx = Integer.parseInt(parts[0]);
                                int ry = Integer.parseInt(parts[1]);
                                int rz = Integer.parseInt(parts[2]);
                                migratedBaseline.put(ForceFieldZone.key(cx + rx, cy + ry, cz + rz), entry.getValue());
                            } catch (NumberFormatException ignored) {
                                // Already-migrated (absolute) key from a
                                // partially-upgraded file - keep as is.
                                migratedBaseline.put(entry.getKey(), entry.getValue());
                            }
                        }

                        zone = new ForceFieldZone(id, name, world, enabled, migratedBaseline);
                        zone.addSphereComponent(new ForceFieldZone.SphereComponent(UUID.randomUUID(), cx, cy, cz, legacyRadius, enabled));
                        assignedMissingId = true; // force a re-save so the migrated format is written back
                    }
                } else {
                    Cuboid cuboid = new Cuboid(world, x1, y1, z1, x2, y2, z2);
                    zone = new ForceFieldZone(id, name, FieldShape.CUBOID, cuboid, 0, enabled, baseline);
                }

                ConfigurationSection redstone = z.getConfigurationSection("redstone");
                if (redstone != null) {
                    zone.setRedstoneLink(redstone.getString("world"), redstone.getInt("x"), redstone.getInt("y"), redstone.getInt("z"));
                }

                String ownerUuidString = z.getString("owner-uuid");
                if (ownerUuidString != null) {
                    try {
                        zone.setOwner(UUID.fromString(ownerUuidString), z.getString("owner-name"));
                    } catch (IllegalArgumentException ignored) {
                        // Corrupt UUID string - treat the zone as unowned rather than failing to load it.
                    }
                }

                zone.setPublic(z.getBoolean("public", false));

                zones.put(name, zone);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load force field zone '" + name + "' from fields.yml", ex);
            }
        }
        if (assignedMissingId) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection zonesSection = yaml.createSection("zones");
        for (ForceFieldZone zone : zones.values()) {
            ConfigurationSection z = zonesSection.createSection(zone.getName());
            z.set("id", zone.getId().toString());
            Cuboid c = zone.getCuboid();
            z.set("world", c.getWorldName());
            z.set("x1", c.getMinX());
            z.set("y1", c.getMinY());
            z.set("z1", c.getMinZ());
            z.set("x2", c.getMaxX());
            z.set("y2", c.getMaxY());
            z.set("z2", c.getMaxZ());
            z.set("enabled", zone.isEnabled());
            z.set("shape", zone.getShape().name());
            if (zone.isSpherical()) {
                ConfigurationSection spheres = z.createSection("spheres");
                for (ForceFieldZone.SphereComponent comp : zone.getSphereComponents()) {
                    ConfigurationSection cs = spheres.createSection(comp.getId().toString());
                    cs.set("x", comp.getX());
                    cs.set("y", comp.getY());
                    cs.set("z", comp.getZ());
                    cs.set("radius", comp.getRadius());
                    cs.set("enabled", comp.isEnabled());
                }
            }

            ConfigurationSection baselineSection = z.createSection("baseline");
            for (Map.Entry<String, String> entry : zone.getBaseline().entrySet()) {
                baselineSection.set(entry.getKey(), entry.getValue());
            }

            if (zone.hasRedstoneLink()) {
                ConfigurationSection redstone = z.createSection("redstone");
                redstone.set("world", zone.getRedstoneWorld());
                redstone.set("x", zone.getRedstoneX());
                redstone.set("y", zone.getRedstoneY());
                redstone.set("z", zone.getRedstoneZ());
            }

            if (zone.getOwnerUuid() != null) {
                z.set("owner-uuid", zone.getOwnerUuid().toString());
                z.set("owner-name", zone.getOwnerName());
            }

            z.set("public", zone.isPublic());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save fields.yml", ex);
        }
    }
}
