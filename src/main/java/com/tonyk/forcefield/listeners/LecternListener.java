package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.gui.FieldDetailMenu;
import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.LecternItem;
import com.tonyk.forcefield.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the physical lectern on/off toggles: transferring a placed
 * lectern item's zone link onto the block's tile-entity data (so it
 * survives past the initial item), right-click to toggle, and
 * double-left-click to open an edit menu for the linked field.
 * <p>
 * Public zones can be toggled by anyone; private ones only by the owner or
 * an admin. Opening the edit menu always requires owner/admin, regardless
 * of the public flag - toggling and managing are different privileges.
 * Lecterns aren't otherwise protected - if one is broken (or a player wants
 * to move it), the field detail GUI's "Get Replacement Lecterns" button (or
 * this lectern's own edit menu) hands out fresh ones.
 */
public final class LecternListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final Messages messages;

    // Per-player last left-click, used to detect a "double left-click" on the
    // same lectern within the configured window.
    private final Map<UUID, Location> lastLeftClickBlock = new HashMap<>();
    private final Map<UUID, Long> lastLeftClickAt = new HashMap<>();

    public LecternListener(JavaPlugin plugin, FieldManager fields, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.messages = messages;
    }

    private long doubleClickWindowMs() {
        return plugin.getConfig().getLong("lectern-double-click-window-ms", 400L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        UUID zoneId = LecternItem.idOf(plugin, inHand);
        String legacyName = zoneId == null ? LecternItem.legacyNameOf(plugin, inHand) : null;
        if (zoneId == null && legacyName == null) {
            return;
        }

        ForceFieldZone zone = zoneId != null ? fields.getZoneById(zoneId) : fields.getZone(legacyName);
        if (zone == null) {
            return;
        }

        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof Lectern lectern)) {
            return;
        }
        lectern.getPersistentDataContainer().set(LecternItem.idKey(plugin), PersistentDataType.STRING, zone.getId().toString());
        lectern.update(true, false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.LECTERN) {
            return;
        }
        if (!(clicked.getState() instanceof Lectern lectern)) {
            return;
        }

        ForceFieldZone zone = resolveAndMaybeHeal(lectern);
        if (zone == null) {
            // Not one of ours (or its field is gone/unresolvable) - leave
            // vanilla lectern behaviour alone.
            return;
        }

        // One of ours from here on - never let it act like a normal
        // book-reading lectern.
        event.setCancelled(true);
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            toggle(player, zone);
            return;
        }
        if (action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Location loc = clicked.getLocation();
        long now = System.currentTimeMillis();
        Location lastLoc = lastLeftClickBlock.get(playerId);
        Long lastAt = lastLeftClickAt.get(playerId);
        boolean isDoubleClick = lastLoc != null && lastAt != null
                && lastLoc.equals(loc) && (now - lastAt) <= doubleClickWindowMs();

        if (isDoubleClick) {
            lastLeftClickBlock.remove(playerId);
            lastLeftClickAt.remove(playerId);
            openEditMenu(player, zone, loc);
        } else {
            lastLeftClickBlock.put(playerId, loc);
            lastLeftClickAt.put(playerId, now);
        }
    }

    private void toggle(Player player, ForceFieldZone zone) {
        if (!canToggle(player, zone)) {
            player.sendMessage(Component.text("This Energy Force Field is private - only its owner or an admin can toggle it.", NamedTextColor.RED));
            return;
        }
        boolean target = !zone.isEnabled();
        fields.setEnabled(zone, target);
        messages.send(player, target ? "zone-raised" : "zone-lowered", "name", zone.getName());
    }

    private void openEditMenu(Player player, ForceFieldZone zone, Location lecternLocation) {
        if (!canManage(player, zone)) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }
        boolean adminView = player.hasPermission("forcefield.admin");
        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.openInventory(FieldDetailMenu.createForLectern(fields, zone.getName(), adminView, lecternLocation)));
    }

    private boolean canToggle(Player player, ForceFieldZone zone) {
        return zone.isPublic() || zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }

    private boolean canManage(Player player, ForceFieldZone zone) {
        return zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }

    /**
     * Resolves the zone a placed lectern is linked to, preferring the
     * current id-based tag. Falls back to the legacy name tag for lecterns
     * placed before ids existed - if the name still resolves to a real
     * zone, the block is upgraded in place to the id-based tag so it never
     * needs healing again (including surviving a future rename). If the
     * field has already been renamed since this lectern was placed, the old
     * name won't resolve to anything and there's no way to recover the
     * link - it needs replacing (see "Get Replacement Lecterns").
     */
    private ForceFieldZone resolveAndMaybeHeal(Lectern lectern) {
        String idString = lectern.getPersistentDataContainer().get(LecternItem.idKey(plugin), PersistentDataType.STRING);
        if (idString != null) {
            try {
                return fields.getZoneById(UUID.fromString(idString));
            } catch (IllegalArgumentException ignored) {
                // Corrupt id - fall through and try the legacy tag instead.
            }
        }

        String legacyName = lectern.getPersistentDataContainer().get(LecternItem.legacyNameKey(plugin), PersistentDataType.STRING);
        if (legacyName == null) {
            return null;
        }
        ForceFieldZone zone = fields.getZone(legacyName);
        if (zone == null) {
            return null;
        }
        lectern.getPersistentDataContainer().set(LecternItem.idKey(plugin), PersistentDataType.STRING, zone.getId().toString());
        lectern.update(true, false);
        return zone;
    }
}
