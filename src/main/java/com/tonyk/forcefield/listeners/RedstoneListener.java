package com.tonyk.forcefield.listeners;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets a lever, button, or any other redstone source raise/lower a linked
 * force field automatically - like a sci-fi control panel.
 */
public final class RedstoneListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;

    public RedstoneListener(JavaPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        ForceFieldZone zone = fields.findByRedstoneLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (zone == null) {
            return;
        }

        boolean wasPowered = event.getOldCurrent() > 0;
        boolean isPowered = event.getNewCurrent() > 0;
        if (wasPowered == isPowered) {
            return;
        }

        // Defer the actual block changes to the next tick: BlockRedstoneEvent
        // fires mid-physics-update, and mass block edits are safest scheduled
        // just after.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isPowered != zone.isEnabled()) {
                    fields.setEnabled(zone, isPowered);
                }
            }
        }.runTask(plugin);
    }
}
