package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.gui.BeaconFieldMenu;
import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.BeaconItem;
import com.tonyk.forcefield.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Handles the Force Field Beacon: placing a tagged beacon creates a
 * spherical zone centered on it and tags the physical block's tile-entity
 * data with the new zone's id; right-clicking a placed one (if you own it or
 * are an admin) opens its controls. Unlike lecterns, a placed Force Field
 * Beacon is fully protected - it can only ever be removed through its own
 * control GUI's Delete button (which also breaks it), never by punching it
 * or an explosion, so a field can't be griefed away just by destroying its
 * generator.
 */
public final class BeaconListener implements Listener {

    private static final String ZONE_ID_KEY = "beacon_field_zone_id";

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final Messages messages;

    public BeaconListener(JavaPlugin plugin, FieldManager fields, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.messages = messages;
    }

    private NamespacedKey zoneIdKey() {
        return new NamespacedKey(plugin, ZONE_ID_KEY);
    }

    private int defaultRadius() {
        return Math.max(1, plugin.getConfig().getInt("beacon-field-radius-small", 50));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!BeaconItem.isBeaconTool(plugin, inHand)) {
            return;
        }
        Player player = event.getPlayer();

        if (!player.hasPermission("forcefield.tool.beacon")) {
            event.setCancelled(true);
            messages.send(player, "no-permission");
            return;
        }
        if (!player.hasPermission("forcefield.create")) {
            event.setCancelled(true);
            messages.send(player, "no-permission");
            return;
        }

        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof Beacon beacon)) {
            return;
        }

        int max = Math.max(0, plugin.getConfig().getInt("beacon-field-max-per-player", 2));
        if (fields.countPlayerBeacons(player.getUniqueId()) >= max) {
            event.setCancelled(true);
            messages.send(player, "beacon-limit-reached", "max", String.valueOf(max));
            return;
        }

        int radius = defaultRadius();
        Location placeLoc = block.getLocation();
        // If this beacon lands inside one of the player's own existing
        // bubbles, merge it into that zone as a second component instead of
        // creating a brand new one. The new beacon always starts off, exactly
        // like a brand new field does, and never disturbs the existing
        // bubble's own state - each beacon's lever only ever controls its own
        // bubble, so whatever's already raised stays raised. Once the player
        // raises this new one too, the shared wall between the two gets
        // removed automatically.
        ForceFieldZone existing = fields.findOwnedSphereZoneContaining(player.getUniqueId(), placeLoc);
        boolean merged = existing != null;

        ForceFieldZone zone;
        try {
            zone = merged
                    ? fields.addBeaconToZone(existing, placeLoc, radius)
                    : fields.createSphereZone(fields.generateZoneName(player), placeLoc, radius, player.getUniqueId(), player.getName());
        } catch (IllegalStateException ex) {
            event.setCancelled(true);
            return;
        }

        beacon.getPersistentDataContainer().set(zoneIdKey(), PersistentDataType.STRING, zone.getId().toString());
        beacon.update(true, false);

        messages.send(player, merged ? "beacon-field-merged" : "beacon-field-created",
                "name", zone.getName(), "radius", String.valueOf(radius));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Beacon beacon)) {
            return;
        }
        UUID zoneId = idOf(beacon);
        if (zoneId == null) {
            return;
        }
        ForceFieldZone zone = fields.getZoneById(zoneId);
        if (zone == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        // Same access rule as a lectern: a field marked Public can be opened
        // by anyone (its own GUI then only lets a non-owner use the lever -
        // resize/delete still require ownership or admin), a private one
        // only by its owner or an admin.
        if (!canManage(player, zone) && !zone.isPublic()) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }

        Location loc = clicked.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.openInventory(BeaconFieldMenu.create(plugin, fields, zone.getId(), loc)));
    }

    /**
     * A placed Force Field Beacon can never be broken directly - punching it,
     * blowing it up, anything - only its own control GUI's Delete button can
     * remove it. This is deliberate: the beacon is the field's generator, so
     * letting anyone break it directly would be a griefing shortcut around
     * every other permission/ownership check the field already has.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Beacon beacon)) {
            return;
        }
        if (idOf(beacon) == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(
                "This Force Field Generator can only be removed with its own Delete button - right-click it to open the controls.",
                NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTaggedBeacon);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTaggedBeacon);
    }

    private boolean isTaggedBeacon(Block block) {
        return block.getState() instanceof Beacon beacon && idOf(beacon) != null;
    }

    private UUID idOf(Beacon beacon) {
        String raw = beacon.getPersistentDataContainer().get(zoneIdKey(), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean canManage(Player player, ForceFieldZone zone) {
        return zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }
}
