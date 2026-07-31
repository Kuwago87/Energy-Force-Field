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
    private final Map<UUID, String> pendingRenameZone = new HashMap<>();
    private final Map<UUID, Long> pendingRenameAt = new HashMap<>();
    private final Map<UUID, String> pendingOwnerChangeZone = new HashMap<>();
    private final Map<UUID, Long> pendingOwnerChangeAt = new HashMap<>();

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

    /**
     * Marks that the player just clicked "Rename" for {@code zoneName} - their
     * next chat message should be captured as the new name.
     */
    public void startRename(Player player, String zoneName) {
        UUID id = player.getUniqueId();
        pendingRenameZone.put(id, zoneName);
        pendingRenameAt.put(id, System.currentTimeMillis());
    }

    public boolean hasPendingRename(Player player) {
        return pendingRenameZone.containsKey(player.getUniqueId());
    }

    /**
     * Consumes (removes) the pending rename request, one-shot - the very next
     * chat message always clears it, whether or not it's used. Returns the
     * zone name to rename, or null if there was no pending request or it had
     * already expired past {@code windowMs} (in which case the caller should
     * let the chat message through normally instead of treating it as a name).
     */
    public String consumePendingRename(Player player, long windowMs) {
        UUID id = player.getUniqueId();
        String zoneName = pendingRenameZone.remove(id);
        Long at = pendingRenameAt.remove(id);
        if (zoneName == null || at == null) {
            return null;
        }
        if (System.currentTimeMillis() - at > windowMs) {
            return null;
        }
        return zoneName;
    }

    /**
     * Marks that the player just clicked "Change Owner" for {@code zoneName}
     * (admin book only) - their next chat message should be captured as the
     * target player's name.
     */
    public void startOwnerChange(Player player, String zoneName) {
        UUID id = player.getUniqueId();
        pendingOwnerChangeZone.put(id, zoneName);
        pendingOwnerChangeAt.put(id, System.currentTimeMillis());
    }

    public boolean hasPendingOwnerChange(Player player) {
        return pendingOwnerChangeZone.containsKey(player.getUniqueId());
    }

    /**
     * Consumes (removes) the pending owner-change request, one-shot. Returns
     * the zone name to re-own, or null if there was no pending request or it
     * had already expired past {@code windowMs}.
     */
    public String consumePendingOwnerChange(Player player, long windowMs) {
        UUID id = player.getUniqueId();
        String zoneName = pendingOwnerChangeZone.remove(id);
        Long at = pendingOwnerChangeAt.remove(id);
        if (zoneName == null || at == null) {
            return null;
        }
        if (System.currentTimeMillis() - at > windowMs) {
            return null;
        }
        return zoneName;
    }
}
