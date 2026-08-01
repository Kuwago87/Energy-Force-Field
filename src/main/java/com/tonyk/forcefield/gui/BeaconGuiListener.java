package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles clicks in the Force Field Beacon's control GUI. Every action
 * closes the menu afterward instead of refreshing it in place - raising,
 * lowering, and resizing a sphere are all ticked operations that finish over
 * the following seconds (see FieldManager), so re-showing the menu
 * immediately would just display stale on/off or radius info until the
 * player reopened it anyway.
 */
public final class BeaconGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final Messages messages;

    public BeaconGuiListener(JavaPlugin plugin, FieldManager fields, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof BeaconFieldHolder beaconHolder)) {
            return;
        }
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ForceFieldZone zone = fields.getZoneById(beaconHolder.getZoneId());
        if (zone == null) {
            player.sendMessage(Component.text("That field no longer exists.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        // Same split as a lectern: a Public field can be opened by anyone
        // (only the lever below is actually usable without ownership/admin),
        // a private one only by its owner or an admin.
        boolean manage = canManage(player, zone);
        if (!manage && !zone.isPublic()) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        if (fields.isSphereBusy(zone)) {
            player.sendMessage(Component.text("This field is still adjusting - try again in a moment.", NamedTextColor.YELLOW));
            return;
        }

        Location beaconLoc = beaconHolder.getBeaconLocation();
        ForceFieldZone.SphereComponent component = zone.findComponentAt(
                beaconLoc.getBlockX(), beaconLoc.getBlockY(), beaconLoc.getBlockZ());
        if (component == null) {
            player.sendMessage(Component.text("This beacon's bubble no longer exists.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        if (slot == BeaconFieldMenu.ON_OFF_SLOT) {
            handleToggle(player, zone, component, manage);
            return;
        }
        if (!manage) {
            player.sendMessage(Component.text("Only the owner or an admin can resize or delete this field.", NamedTextColor.RED));
            return;
        }
        if (slot == BeaconFieldMenu.DELETE_SLOT) {
            handleDelete(player, zone, component, beaconHolder);
        } else if (slot == BeaconFieldMenu.SMALL_SLOT) {
            handleResize(player, zone, component, radiusConfig("beacon-field-radius-small", 50));
        } else if (slot == BeaconFieldMenu.MEDIUM_SLOT) {
            handleResize(player, zone, component, radiusConfig("beacon-field-radius-medium", 150));
        } else if (slot == BeaconFieldMenu.LARGE_SLOT) {
            handleResize(player, zone, component, radiusConfig("beacon-field-radius-large", 250));
        }
    }

    /**
     * Toggles just this one beacon's own bubble - a merged neighbor's own
     * on/off state is never touched. An owner/admin still needs
     * forcefield.modify, same as the shared field detail GUI's lever; a
     * non-owner reaching this via a Public field needs nothing beyond that -
     * same as toggling a Public field from its lectern, which requires no
     * permission node at all once the owner has opted it into public access.
     */
    private void handleToggle(Player player, ForceFieldZone zone, ForceFieldZone.SphereComponent component, boolean manage) {
        if (manage && !player.hasPermission("forcefield.modify")) {
            messages.send(player, "no-permission");
            return;
        }
        player.closeInventory();
        boolean target = !component.isEnabled();
        fields.setComponentEnabled(zone, component, target);
        messages.send(player, target ? "zone-raised" : "zone-lowered", "name", zone.getName());
    }

    private void handleDelete(Player player, ForceFieldZone zone, ForceFieldZone.SphereComponent component, BeaconFieldHolder holder) {
        if (!player.hasPermission("forcefield.delete")) {
            messages.send(player, "no-permission");
            return;
        }
        player.closeInventory();
        String name = zone.getName();
        boolean lastBeacon = zone.getSphereComponents().size() <= 1;
        Block beaconBlock = holder.getBeaconLocation().getBlock();
        fields.removeBeaconComponent(zone, component);
        if (beaconBlock.getType() == Material.BEACON) {
            beaconBlock.setType(Material.AIR);
        }
        if (lastBeacon) {
            messages.send(player, "zone-removed", "name", name);
        } else {
            player.sendMessage(Component.text("This beacon's bubble has been removed - '" + name + "' stays up with its remaining beacon(s).", NamedTextColor.YELLOW));
        }
    }

    private void handleResize(Player player, ForceFieldZone zone, ForceFieldZone.SphereComponent component, int radius) {
        if (!player.hasPermission("forcefield.modify")) {
            messages.send(player, "no-permission");
            return;
        }
        if (component.getRadius() == radius) {
            player.closeInventory();
            return;
        }
        player.closeInventory();
        boolean wasEnabled = component.isEnabled();
        fields.setComponentRadius(zone, component, radius);
        messages.send(player, "beacon-field-resized", "name", zone.getName(), "radius", String.valueOf(radius));
        if (wasEnabled) {
            player.sendMessage(Component.text("This beacon is lowering now - right-click it and click On/Off again to raise it at the new size.", NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BeaconFieldHolder) {
            event.setCancelled(true);
        }
    }

    private int radiusConfig(String key, int def) {
        return Math.max(1, plugin.getConfig().getInt(key, def));
    }

    private boolean canManage(Player player, ForceFieldZone zone) {
        return zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }
}
