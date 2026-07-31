package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Messages;
import com.tonyk.forcefield.util.OnOffCrystal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the On/Off remote (End Crystal): right-click toggles whichever
 * Energy Force Field the player is actually looking at, within range - a
 * ray cast from their eyes, not just "nearest field regardless of where
 * you're aiming". Restricted to the field's owner or an admin, same as the
 * rod's delete action; unlike a lectern, the remote isn't affected by a
 * field's public/private setting.
 */
public final class CrystalListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final Messages messages;

    public CrystalListener(JavaPlugin plugin, FieldManager fields, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.messages = messages;
    }

    private double range() {
        return plugin.getConfig().getDouble("crystal-remote-range", 10.0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!OnOffCrystal.isCrystal(plugin, item)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("forcefield.tool.crystal")) {
            messages.send(player, "no-permission");
            return;
        }
        event.setCancelled(true);

        if (!player.hasPermission("forcefield.modify")) {
            messages.send(player, "no-permission");
            return;
        }

        ForceFieldZone zone = fields.findFacingZone(player.getEyeLocation(), player.getEyeLocation().getDirection(), range());
        if (zone == null) {
            player.sendMessage(Component.text("No Energy Force Field in view within range.", NamedTextColor.GRAY));
            return;
        }
        if (!canManage(player, zone)) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }

        boolean target = !zone.isEnabled();
        fields.setEnabled(zone, target);
        messages.send(player, target ? "zone-raised" : "zone-lowered", "name", zone.getName());
    }

    private boolean canManage(Player player, ForceFieldZone zone) {
        return zone.isOwnedBy(player.getUniqueId()) || player.hasPermission("forcefield.admin");
    }
}
