package com.tonyk.forcefield;

import com.tonyk.forcefield.commands.ForceFieldCommand;
import com.tonyk.forcefield.commands.ToolsCommand;
import com.tonyk.forcefield.gui.ToolsGuiListener;
import com.tonyk.forcefield.listeners.ProtectionListener;
import com.tonyk.forcefield.listeners.RedstoneListener;
import com.tonyk.forcefield.listeners.WandListener;
import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.manager.SelectionManager;
import com.tonyk.forcefield.tasks.AmbientEffectTask;
import com.tonyk.forcefield.tasks.EdgeOutlineTask;
import com.tonyk.forcefield.util.EffectService;
import com.tonyk.forcefield.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ForceFieldPlugin extends JavaPlugin {

    private EffectService effects;
    private Messages messages;
    private FieldManager fieldManager;
    private SelectionManager selectionManager;
    private BukkitTask ambientTask;
    private BukkitTask edgeOutlineTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.effects = new EffectService(this);
        this.messages = new Messages(this);
        this.selectionManager = new SelectionManager();
        this.fieldManager = new FieldManager(this, effects);

        getServer().getPluginManager().registerEvents(
                new WandListener(this, selectionManager, fieldManager, messages), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(this, fieldManager, effects, messages), this);
        getServer().getPluginManager().registerEvents(
                new RedstoneListener(this, fieldManager), this);
        getServer().getPluginManager().registerEvents(
                new ToolsGuiListener(this, fieldManager), this);

        PluginCommand forcefieldCommand = getCommand("forcefield");
        if (forcefieldCommand != null) {
            ForceFieldCommand executor = new ForceFieldCommand(this, fieldManager, selectionManager, effects, messages);
            forcefieldCommand.setExecutor(executor);
            forcefieldCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("Could not register the /forcefield command - is plugin.yml intact?");
        }

        PluginCommand toolsCommand = getCommand("eff_tools");
        if (toolsCommand != null) {
            toolsCommand.setExecutor(new ToolsCommand(this, messages));
        } else {
            getLogger().severe("Could not register the /eff_tools command - is plugin.yml intact?");
        }

        long interval = Math.max(1, getConfig().getLong("ambient-interval-ticks", 40));
        ambientTask = new AmbientEffectTask(this, fieldManager, effects).runTaskTimer(this, interval, interval);

        long edgeInterval = Math.max(1, getConfig().getLong("edge-outline-interval-ticks", 4));
        edgeOutlineTask = new EdgeOutlineTask(fieldManager, effects).runTaskTimer(this, edgeInterval, edgeInterval);

        getLogger().info("EFF enabled - " + fieldManager.getZones().size() + " zone(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (ambientTask != null) {
            ambientTask.cancel();
        }
        if (edgeOutlineTask != null) {
            edgeOutlineTask.cancel();
        }
        if (fieldManager != null) {
            fieldManager.save();
        }
    }
}
