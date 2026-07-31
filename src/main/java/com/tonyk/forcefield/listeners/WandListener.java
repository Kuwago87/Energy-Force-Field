package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.manager.SelectionManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.LecternItem;
import com.tonyk.forcefield.util.Messages;
import com.tonyk.forcefield.util.WandItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the Create/Delete rod: left-click cycles between setting corner 1
 * and corner 2 (the first left-click sets corner 1, the next sets corner 2
 * and immediately creates the field - handing the player two linked
 * lecterns to place as its physical on/off switches - and the click after
 * that starts a brand new corner 1, so you never need to touch a second
 * button to build a field). Right-click deletes the nearest field (with a
 * confirmation click). It's also used for pointing an admin at a block to
 * link a redstone trigger.
 */
public final class WandListener implements Listener {

    private final JavaPlugin plugin;
    private final SelectionManager selection;
    private final FieldManager fields;
    private final Messages messages;

    public WandListener(JavaPlugin plugin, SelectionManager selection, FieldManager fields, Messages messages) {
        this.plugin = plugin;
        this.selection = selection;
        this.fields = fields;
        this.messages = messages;
    }

    private double toggleRange() {
        return plugin.getConfig().getDouble("toggle-tool-range", 8.0);
    }

    private long deleteConfirmWindowMs() {
        return plugin.getConfig().getLong("delete-confirm-window-ms", 5000L);
    }

    private long maxVolumeWithoutConfirm() {
        return plugin.getConfig().getLong("max-volume-without-confirm", 5000);
    }

    private int lecternsPerField() {
        return Math.max(0, plugin.getConfig().getInt("lecterns-per-field", 2));
    }

    /** True if the player may manage (toggle/delete) this zone. */
    private boolean canManage(Player player, ForceFieldZone zone) {
        return zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        ItemStack item = event.getItemInHand();
        if (WandItem.isWand(plugin, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        if (WandItem.isWand(plugin, item)) {
            handleRod(event, player, action);
        }
    }

    private void handleRod(PlayerInteractEvent event, Player player, Action action) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!player.hasPermission("forcefield.tool.rod")) {
            messages.send(player, "no-permission");
            return;
        }
        event.setCancelled(true);

        // Redstone link mode (started via /forcefield link <name>) takes priority
        // and needs an actual clicked block.
        if (selection.hasPendingLink(player)) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }
            String zoneName = selection.consumePendingLink(player);
            ForceFieldZone zone = fields.getZone(zoneName);
            if (zone == null) {
                messages.send(player, "zone-not-found", "name", zoneName);
                return;
            }
            zone.setRedstoneLink(clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
            fields.save();
            String loc = clicked.getX() + "," + clicked.getY() + "," + clicked.getZ();
            messages.send(player, "link-set", "name", zoneName, "location", loc);
            return;
        }

        // Right-click: delete the nearest field (with a confirmation click).
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            deleteNearest(player);
            return;
        }

        // Left-click needs a real block to anchor a corner to.
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        if (selection.getPos1(player) == null || selection.hasFullSelection(player)) {
            // Nothing selected yet, or the last selection already became a
            // field (or was left dangling) - this click starts a fresh corner 1.
            selection.clear(player);
            selection.setPos1(player, clicked.getLocation());
            player.sendActionBar(Component.text("Corner 1 set: "
                    + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ()
                    + " - left-click again for corner 2", NamedTextColor.GOLD));
            return;
        }

        // Corner 1 is already set - this click sets corner 2 and immediately
        // creates the field, then resets so the next left-click starts over.
        selection.setPos2(player, clicked.getLocation());
        createFromSelection(player);
    }

    private void createFromSelection(Player player) {
        if (!player.hasPermission("forcefield.create")) {
            messages.send(player, "no-permission");
            selection.clear(player);
            return;
        }
        if (!selection.hasFullSelection(player)) {
            messages.send(player, "need-selection");
            return;
        }
        Location pos1 = selection.getPos1(player);
        Location pos2 = selection.getPos2(player);
        Cuboid cuboid = Cuboid.fromLocations(pos1, pos2);

        String name = fields.generateZoneName(player);
        long maxVolume = maxVolumeWithoutConfirm();
        if (cuboid.volume() > maxVolume) {
            messages.send(player, "too-large",
                    "blocks", String.valueOf(cuboid.volume()),
                    "max", String.valueOf(maxVolume),
                    "name", name);
            player.sendMessage(Component.text("That's too big to quick-create - use /forcefield create "
                    + name + " confirm instead.", NamedTextColor.RED));
            return;
        }

        ForceFieldZone zone = fields.createZone(name, cuboid, player.getUniqueId(), player.getName());
        selection.clear(player);
        messages.send(player, "zone-created", "name", zone.getName(), "blocks", String.valueOf(cuboid.volume()));

        int lecternCount = lecternsPerField();
        if (lecternCount > 0) {
            LecternItem.giveSet(plugin, player, zone, lecternCount);
            player.sendMessage(Component.text("You've been given " + lecternCount
                    + " lectern(s) linked to '" + zone.getName()
                    + "' - place them and right-click to toggle it.", NamedTextColor.AQUA));
        }
    }

    private void deleteNearest(Player player) {
        if (!player.hasPermission("forcefield.delete")) {
            messages.send(player, "no-permission");
            return;
        }
        ForceFieldZone zone = fields.findNearestZone(player.getLocation(), toggleRange());
        if (zone == null) {
            player.sendMessage(Component.text("No Energy Force Field within range.", NamedTextColor.GRAY));
            return;
        }
        if (!canManage(player, zone)) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }
        if (selection.confirmPendingDelete(player, zone.getName(), deleteConfirmWindowMs())) {
            fields.removeZone(zone.getName());
            messages.send(player, "zone-removed", "name", zone.getName());
        } else {
            player.sendMessage(Component.text("Right-click again within "
                    + (deleteConfirmWindowMs() / 1000) + "s to confirm deleting '" + zone.getName() + "'.", NamedTextColor.YELLOW));
        }
    }

}
