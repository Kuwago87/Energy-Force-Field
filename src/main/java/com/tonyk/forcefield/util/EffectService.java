package com.tonyk.forcefield.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Resolves the configurable particle/sound names from config.yml into real
 * enum values, falling back to sane defaults (and logging a warning once)
 * if a server's Paper version doesn't recognize a name. Also centralizes the
 * actual particle/sound playback used across the plugin.
 */
public final class EffectService {

    private final JavaPlugin plugin;

    private Sound activateSound;
    private Sound deactivateSound;
    private Sound ambientSound;
    private Sound resistSound;
    private Particle wallParticle;
    private Particle ambientParticle;
    private Particle resistParticle;
    private Particle beaconBeamParticle;

    public EffectService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        activateSound = resolveSound(cfg.getString("effects.activate-sound", "BLOCK_BEACON_ACTIVATE"), Sound.BLOCK_BEACON_ACTIVATE);
        deactivateSound = resolveSound(cfg.getString("effects.deactivate-sound", "BLOCK_BEACON_DEACTIVATE"), Sound.BLOCK_BEACON_DEACTIVATE);
        ambientSound = resolveSound(cfg.getString("effects.ambient-sound", "BLOCK_BEACON_AMBIENT"), Sound.BLOCK_BEACON_AMBIENT);
        resistSound = resolveSound(cfg.getString("effects.resist-sound", "ENTITY_ENDERMAN_TELEPORT"), Sound.ENTITY_ENDERMAN_TELEPORT);
        wallParticle = resolveParticle(cfg.getString("effects.wall-particle", "SCULK_CHARGE_POP"), Particle.SCULK_CHARGE_POP);
        ambientParticle = resolveParticle(cfg.getString("effects.ambient-particle", "SCULK_CHARGE_POP"), Particle.SCULK_CHARGE_POP);
        resistParticle = resolveParticle(cfg.getString("effects.resist-particle", "SCULK_CHARGE_POP"), Particle.SCULK_CHARGE_POP);
        beaconBeamParticle = resolveParticle(cfg.getString("beacon-beam-particle", "END_ROD"), Particle.END_ROD);
    }

    private Sound resolveSound(String name, Sound fallback) {
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Unknown sound '" + name + "' in config.yml, falling back to " + fallback.name());
            return fallback;
        }
    }

    private Particle resolveParticle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Unknown particle '" + name + "' in config.yml, falling back to " + fallback.name());
            return fallback;
        }
    }

    public void playActivate(World world, Location center, double radius) {
        world.playSound(center, activateSound, 1.0f, 1.4f);
        world.spawnParticle(wallParticle, center, (int) Math.max(60, radius * 20), radius / 2, radius / 2, radius / 2, 0.01);
    }

    public void playDeactivate(World world, Location center, double radius) {
        world.playSound(center, deactivateSound, 1.0f, 0.8f);
        world.spawnParticle(Particle.SMOKE, center, (int) Math.max(10, radius * 4), radius / 2, radius / 2, radius / 2, 0.005);
    }

    public void ambientPulse(World world, Location point) {
        world.spawnParticle(ambientParticle, point, 1, 0.15, 0.15, 0.15, 0.0);
    }

    /**
     * A tight, un-jittered particle used to trace the field's fixed edges
     * (the top and bottom outline) so they read as a crisp line rather than
     * a diffuse cloud.
     */
    public void edgePulse(World world, Location point) {
        world.spawnParticle(ambientParticle, point, 1, 0.0, 0.0, 0.0, 0.0);
    }

    public void ambientHum(World world, Location point, float volume) {
        world.playSound(point, ambientSound, volume, 1.6f);
    }

    /**
     * Plays the ambient hum only to a specific player (used to respect the
     * configurable ambient-sound-radius instead of Minecraft's default
     * sound falloff).
     */
    public void ambientHumTo(Player player, Location point, float volume) {
        player.playSound(point, ambientSound, volume, 1.6f);
    }

    /**
     * A tight, un-jittered particle used to draw a beacon field's vertical
     * "power on" beam - same style as {@link #edgePulse}, just spawned in a
     * straight line going up instead of tracing a box's edges.
     */
    public void beaconBeamPulse(World world, Location point) {
        world.spawnParticle(beaconBeamParticle, point, 1, 0.0, 0.0, 0.0, 0.0);
    }

    public void playResist(Location point) {
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(point, resistSound, 0.6f, 1.8f);
        world.spawnParticle(resistParticle, point, 8, 0.2, 0.2, 0.2, 0.02);
    }
}
