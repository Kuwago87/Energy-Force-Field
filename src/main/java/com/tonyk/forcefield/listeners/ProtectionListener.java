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
 * "the field resists you" nudge (rate-limited) when they try. This also
 * covers already-solid blocks that raise() deliberately left untouched in
 * the field's path (a door frame, terrain, decorations, ...) - those were
 * never converted to the field's own material, but breaking one would open
 * a real, permanent gap straight through the field, so they're protected
 * exactly like the field's own barrier/shell blocks for as long as it stays
 * raised (see ZoneLookup#findEnabledZoneBlockingAt).
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

    /** The material a beacon (spherical) field's shell is made of - see FieldManager. */
    private Material sphereShellMaterial() {
        String name = plugin.getConfig().getString("beacon-field-shell-material", "BLUE_STAINED_GLASS");
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Material.BLUE_STAINED_GLASS;
        }
    }

    /** True for either a rod field's BARRIER blocks or a beacon field's (configurable) shell material. */
    private boolean isForceFieldMaterial(Material type) {
        return type == Material.BARRIER || type == sphereShellMaterial();
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

    /**
     * Finds the enabled zone (if any) actually protecting this block, whether
     * it's the field's own barrier/shell material or an already-solid block
     * raise() left alone in the field's path (a passable block in the
     * footprint would always have been converted, so it's never a concern
     * here).
     */
    private ForceFieldZone findProtectingZone(Block block) {
        if (isForceFieldMaterial(block.getType())) {
            return ZoneLookup.findEnabledZoneContaining(fields, block.getLocation(), block.getType(), sphereShellMaterial());
        }
        if (block.isPassable()) {
            return null;
        }
        return ZoneLookup.findEnabledZoneBlockingAt(fields, block.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ForceFieldZone zone = findProtectingZone(block);
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
        if (block == null) {
            return;
        }
        ForceFieldZone zone = findProtectingZone(block);
        if (zone == null) {
            return;
        }
        giveFeedback(event.getPlayer(), block.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> findProtectingZone(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> findProtectingZone(b) != null);
    }
}
