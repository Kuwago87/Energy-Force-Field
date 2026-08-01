package com.tonyk.forcefield.util;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Processes a sphere shell's block offsets a batch at a time, one batch per
 * tick, instead of all at once - a radius-250 bubble is ~785,000 blocks, and
 * touching that many in a single tick would freeze the server for a
 * noticeable moment. Used for both raising (place barriers) and lowering
 * (restore baseline) a spherical zone; the shield visibly grows/shrinks over
 * a couple of seconds rather than popping instantly, which reads as "powering
 * up/down" instead of lag.
 */
public final class SphereFillTask extends BukkitRunnable {

    /** One unit of work against a single block in the shell. */
    public interface OffsetAction {
        void apply(Block block);
    }

    private final World world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int[][] offsets;
    private final OffsetAction action;
    private final Runnable onComplete;
    private final int perTick;
    private int index = 0;

    public SphereFillTask(World world, int centerX, int centerY, int centerZ,
                           int[][] offsets, int perTick, OffsetAction action, Runnable onComplete) {
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.offsets = offsets;
        this.perTick = Math.max(1, perTick);
        this.action = action;
        this.onComplete = onComplete;
    }

    /** Starts the task, running every tick until every offset has been processed. */
    public BukkitTask start(JavaPlugin plugin) {
        return runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        int end = Math.min(offsets.length, index + perTick);
        for (; index < end; index++) {
            int[] o = offsets[index];
            Block block = world.getBlockAt(centerX + o[0], centerY + o[1], centerZ + o[2]);
            action.apply(block);
        }
        if (index >= offsets.length) {
            cancel();
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}
