package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.gui.BeaconFieldMenu;
import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.BeaconItem;
import com.tonyk.forcefield.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Force Field Beacon: placing a tagged beacon creates a
 * spherical zone centered on it and tags the physical block's tile-entity
 * data with the new zone's id; right-clicking a placed one (if you own it or
 * are an admin) opens its controls. The owner (or an admin) can now also
 * punch a placed beacon down directly - same cleanup as the GUI's own
 * Delete button, item drops normally. Anyone else punching it is only let
 * through if the field is currently raised, and it's a trap when they are:
 * the beacon (and the field with it) still comes down, but they get an
 * explosion animation and die on the spot instead of walking off with a very
 * expensive block. A non-owner punching an already-lowered field stays
 * fully protected, same as before.
 */
public final class BeaconListener implements Listener {

    private static final String ZONE_ID_KEY = "beacon_field_zone_id";

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final Messages messages;
    private final Set<UUID> trapDeaths = new HashSet<>();

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

    /**
     * True if the beacon block itself, or any of its 6 face-adjacent
     * neighbors, is water - a simple, cheap stand-in for "was this placed
     * underwater" that catches a beacon sitting on the seafloor (water
     * above and to the sides) without needing to flood-fill anything.
     * Determined once, at placement time, and permanent for that beacon's
     * lifetime (see ForceFieldZone.SphereComponent#isUnderwater).
     */
    private boolean isUnderwater(Block block) {
        if (block.getType() == Material.WATER) {
            return true;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).getType() == Material.WATER) {
                return true;
            }
        }
        return false;
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

        boolean underwater = isUnderwater(block);
        int radius = underwater ? Math.min(defaultRadius(), fields.underwaterMaxRadius()) : defaultRadius();
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
                    ? fields.addBeaconToZone(existing, placeLoc, radius, underwater)
                    : fields.createSphereZone(fields.generateZoneName(player), placeLoc, radius, player.getUniqueId(), player.getName(), underwater);
        } catch (IllegalStateException ex) {
            event.setCancelled(true);
            return;
        }

        beacon.getPersistentDataContainer().set(zoneIdKey(), PersistentDataType.STRING, zone.getId().toString());
        beacon.update(true, false);

        messages.send(player, merged ? "beacon-field-merged" : "beacon-field-created",
                "name", zone.getName(), "radius", String.valueOf(radius));
        if (underwater) {
            messages.send(player, "beacon-underwater-capped", "max", String.valueOf(fields.underwaterMaxRadius()));
        }
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
     * The owner (or an admin) can punch a placed Force Field Beacon down
     * directly now, same cleanup as the GUI's own Delete button, item drops
     * normally - no need to right-click in just to delete it.
     * <p>
     * Anyone else is only let through if that beacon's own bubble is
     * currently raised, and it's a trap when they are: the block still
     * breaks and the field still comes down with it, but they don't get to
     * walk off with a very expensive block - {@link #springTrap} gives them
     * an explosion animation and kills them on the spot instead, and the
     * beacon drops nothing.
     * <p>
     * A non-owner punching an already-lowered beacon is still fully
     * protected, exactly like before this feature existed - there's no
     * trap to spring on an inactive field, so there's no reason to let a
     * stranger walk off with someone else's generator either.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Beacon beacon)) {
            return;
        }
        UUID zoneId = idOf(beacon);
        if (zoneId == null) {
            return;
        }
        Player player = event.getPlayer();
        ForceFieldZone zone = fields.getZoneById(zoneId);
        if (zone == null) {
            event.setCancelled(true);
            return;
        }
        Location loc = block.getLocation();
        ForceFieldZone.SphereComponent component = zone.findComponentAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (component == null) {
            event.setCancelled(true);
            return;
        }

        if (canManage(player, zone)) {
            // Same permission the GUI's own Delete button requires - an
            // owner/admin without it is still blocked, not treated as an
            // outsider (no trap on your own account for a missing node).
            if (!player.hasPermission("forcefield.delete")) {
                event.setCancelled(true);
                messages.send(player, "no-permission");
                return;
            }
            String name = zone.getName();
            boolean lastBeacon = zone.getSphereComponents().size() <= 1;
            fields.removeBeaconComponent(zone, component);
            if (lastBeacon) {
                messages.send(player, "zone-removed", "name", name);
            } else {
                player.sendMessage(Component.text(
                        "This beacon's bubble has been removed - '" + name + "' stays up with its remaining beacon(s).",
                        NamedTextColor.YELLOW));
            }
            return;
        }

        if (!component.isEnabled()) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "This Force Field Generator can only be removed with its own Delete button - right-click it to open the controls.",
                    NamedTextColor.RED));
            return;
        }

        event.setDropItems(false);
        fields.removeBeaconComponent(zone, component);
        springTrap(player, loc);
    }

    /**
     * The punishment for breaking someone else's live field: a cosmetic
     * explosion (particle + sound only, no block damage and nothing hurt
     * except the one player) followed by an instant kill, bypassing the
     * normal damage pipeline entirely (so no totem, no armor, no
     * invulnerability frames save them) via a direct health set. The death
     * message is swapped for a themed one in {@link #onTrapDeath}.
     */
    private void springTrap(Player player, Location loc) {
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0.5, 0.5, 0.5), 1);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
        }
        trapDeaths.add(player.getUniqueId());
        player.setHealth(0.0);
    }

    /**
     * Swaps in a themed death message for a trap kill from {@link
     * #springTrap} - everything else about the death (drops, respawn,
     * keepInventory, etc.) is untouched, only the broadcast message changes.
     */
    @EventHandler
    public void onTrapDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!trapDeaths.remove(player.getUniqueId())) {
            return;
        }
        event.deathMessage(messages.rawComponent("beacon-trap-death", "player", player.getName()));
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
