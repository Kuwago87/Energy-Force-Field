package com.tonyk.forcefield.manager;

import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
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
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public void removeZone(String name) {
        ForceFieldZone zone = zones.get(name);
        if (zone == null) {
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

    private Location centerOf(World world, Cuboid c) {
        double x = (c.getMinX() + c.getMaxX()) / 2.0 + 0.5;
        double y = (c.getMinY() + c.getMaxY()) / 2.0 + 0.5;
        double z = (c.getMinZ() + c.getMaxZ()) / 2.0 + 0.5;
        return new Location(world, x, y, z);
    }

    public void load() {
        zones.clear();
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

                Cuboid cuboid = new Cuboid(world, x1, y1, z1, x2, y2, z2);
                ForceFieldZone zone = new ForceFieldZone(id, name, cuboid, enabled, baseline);

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
