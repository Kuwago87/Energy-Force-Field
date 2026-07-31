package com.tonyk.forcefield.model;

import com.tonyk.forcefield.util.Cuboid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A named force field zone: a cuboid region plus its "shields down" baseline
 * block state, its current enabled (raised) flag, and an optional redstone
 * block that controls it automatically.
 */
public final class ForceFieldZone {

    private final String name;
    private final Cuboid cuboid;
    private boolean enabled;

    // Relative offset (x,y,z packed as "x,y,z") -> block data string, captured
    // at creation time. This is the state blocks are restored to when the
    // field is lowered.
    private final Map<String, String> baseline;

    private String redstoneWorld;
    private Integer redstoneX;
    private Integer redstoneY;
    private Integer redstoneZ;

    private UUID ownerUuid;
    private String ownerName;

    public ForceFieldZone(String name, Cuboid cuboid, boolean enabled, Map<String, String> baseline) {
        this.name = name;
        this.cuboid = cuboid;
        this.enabled = enabled;
        this.baseline = new LinkedHashMap<>(baseline);
    }

    public String getName() {
        return name;
    }

    public Cuboid getCuboid() {
        return cuboid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getBaseline() {
        return baseline;
    }

    public boolean hasRedstoneLink() {
        return redstoneWorld != null && redstoneX != null && redstoneY != null && redstoneZ != null;
    }

    public String getRedstoneWorld() {
        return redstoneWorld;
    }

    public Integer getRedstoneX() {
        return redstoneX;
    }

    public Integer getRedstoneY() {
        return redstoneY;
    }

    public Integer getRedstoneZ() {
        return redstoneZ;
    }

    public void setRedstoneLink(String world, int x, int y, int z) {
        this.redstoneWorld = world;
        this.redstoneX = x;
        this.redstoneY = y;
        this.redstoneZ = z;
    }

    public void clearRedstoneLink() {
        this.redstoneWorld = null;
        this.redstoneX = null;
        this.redstoneY = null;
        this.redstoneZ = null;
    }

    public static String key(int relX, int relY, int relZ) {
        return relX + "," + relY + "," + relZ;
    }

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwnedBy(UUID uuid) {
        return ownerUuid != null && uuid != null && ownerUuid.equals(uuid);
    }
}
