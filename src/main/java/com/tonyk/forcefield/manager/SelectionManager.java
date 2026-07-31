package com.tonyk.forcefield.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's two wand-selected corners, plus any pending "link this
 * field to a redstone block" request.
 */
public final class SelectionManager {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, String> pendingLink = new HashMap<>();
    private final Map<UUID, String> pendingDeleteZone = new HashMap<>();
    private final Map<UUID, Long> pendingDeleteAt = new HashMap<>();

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc);
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc);
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    public boolean hasFullSelection(Player player) {
        return getPos1(player) != null && getPos2(player) != null
                && getPos1(player).getWorld().equals(getPos2(player).getWorld());
    }

    public void clear(Player player) {
        pos1.remove(player.getUniqueId());
        pos2.remove(player.getUniqueId());
    }

    public void startLink(Player player, String zoneName) {
        pendingLink.put(player.getUniqueId(), zoneName);
    }

    public String consumePendingLink(Player player) {
        return pendingLink.remove(player.getUniqueId());
    }

    public boolean hasPendingLink(Player player) {
        return pendingLink.containsKey(player.getUniqueId());
    }

    /**
     * Records that the player just asked to delete {@code zoneName}, returning
     * true if this is a *confirming* second request for the same zone within
     * {@code windowMs} of the first. Any request for a different zone (or one
     * that arrives after the window closes) resets the pending state instead
     * of confirming, so a stray click on the wrong field can't be mistaken
     * for a "yes, delete it" follow-up.
     */
    public boolean confirmPendingDelete(Player player, String zoneName, long windowMs) {
        UUID id = player.getUniqueId();
        String pendingName = pendingDeleteZone.get(id);
        Long pendingAt = pendingDeleteAt.get(id);
        long now = System.currentTimeMillis();

        if (pendingName != null && pendingName.equalsIgnoreCase(zoneName)
                && pendingAt != null && now - pendingAt <= windowMs) {
            pendingDeleteZone.remove(id);
            pendingDeleteAt.remove(id);
            return true;
        }

        pendingDeleteZone.put(id, zoneName);
        pendingDeleteAt.put(id, now);
        return false;
    }
}
