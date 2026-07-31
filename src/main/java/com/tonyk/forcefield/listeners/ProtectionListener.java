package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.manager.ZoneLookup;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.EffectService;
import com.tonyk.forcefield.util.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protects raised force fields: their barrier blocks can't be broken, punched
 * through, or blown up while the field is active, and give the player a
 * "the field resists you" nudge (rate-limited) when they try.
 */
public final class ProtectionListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final EffectService effects;
    private final Messages messages;
    private final Map<UUID, Long> lastResistFeedback = new HashMap<>();

    public ProtectionListener(JavaPlugin plugin, FieldManager fields, EffectService effects, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.effects = effects;
        this.messages = messages;
    }

    private long cooldownMs() {
        return plugin.getConfig().getLong("resist-feedback-cooldown-ms", 800L);
    }

    private void giveFeedback(Player player, Location at) {
        long now = System.currentTimeMillis();
        Long last = lastResistFeedback.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs()) {
            return;
        }
        lastResistFeedback.put(player.getUniqueId(), now);
        effects.playResist(at.clone().add(0.5, 0.5, 0.5));
        messages.send(player, "resisted");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) {
            return;
        }
        ForceFieldZone zone = ZoneLookup.findEnabledZoneContaining(fields, block.getLocation());
        if (zone == null) {
            return;
        }
        event.setCancelled(true);
        giveFeedback(event.getPlayer(), block.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARRIER) {
            return;
        }
        ForceFieldZone zone = ZoneLookup.findEnabledZoneContaining(fields, block.getLocation());
        if (zone == null) {
            return;
        }
        giveFeedback(event.getPlayer(), block.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> b.getType() == Material.BARRIER
                && ZoneLookup.findEnabledZoneContaining(fields, b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> b.getType() == Material.BARRIER
                && ZoneLookup.findEnabledZoneContaining(fields, b.getLocation()) != null);
    }
}
