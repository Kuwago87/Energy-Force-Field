package com.tonyk.forcefield.commands;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.manager.SelectionManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.EffectService;
import com.tonyk.forcefield.util.LecternItem;
import com.tonyk.forcefield.util.Messages;
import com.tonyk.forcefield.util.WandItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ForceFieldCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "create", "remove", "toggle", "list", "info", "link", "unlink", "reload", "help");

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final SelectionManager selection;
    private final EffectService effects;
    private final Messages messages;

    public ForceFieldCommand(JavaPlugin plugin, FieldManager fields, SelectionManager selection,
                              EffectService effects, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.selection = selection;
        this.effects = effects;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> handleWand(sender);
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "link" -> handleLink(sender, args);
            case "unlink" -> handleUnlink(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This can only be used in-game.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private void handleWand(CommandSender sender) {
        if (!requirePlayer(sender) || !requirePermission(sender, "forcefield.tool.rod")) {
            return;
        }
        Player player = (Player) sender;
        player.getInventory().addItem(WandItem.create(plugin));
        player.sendMessage(Component.text("You've been given the Create/Delete rod. Try /eff_tools for the full GUI.", NamedTextColor.AQUA));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "forcefield.admin")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield create <name> [confirm]", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;
        String name = args[1];

        if (fields.exists(name)) {
            messages.send(sender, "zone-exists", "name", name);
            return;
        }

        if (!selection.hasFullSelection(player)) {
            messages.send(sender, "need-selection");
            return;
        }

        Location pos1 = selection.getPos1(player);
        Location pos2 = selection.getPos2(player);
        Cuboid cuboid = Cuboid.fromLocations(pos1, pos2);

        long maxVolume = plugin.getConfig().getLong("max-volume-without-confirm", 5000);
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (cuboid.volume() > maxVolume && !confirmed) {
            messages.send(sender, "too-large",
                    "blocks", String.valueOf(cuboid.volume()),
                    "max", String.valueOf(maxVolume),
                    "name", name);
            return;
        }

        ForceFieldZone zone = fields.createZone(name, cuboid, player.getUniqueId(), player.getName());
        selection.clear(player);
        messages.send(sender, "zone-created", "name", zone.getName(), "blocks", String.valueOf(cuboid.volume()));

        int lecternCount = Math.max(0, plugin.getConfig().getInt("lecterns-per-field", 2));
        if (lecternCount > 0) {
            LecternItem.giveSet(plugin, player, zone, lecternCount);
            player.sendMessage(Component.text("You've been given " + lecternCount
                    + " lectern(s) linked to '" + zone.getName()
                    + "' - place them and right-click to toggle it.", NamedTextColor.AQUA));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "forcefield.admin")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield remove <name>", NamedTextColor.RED));
            return;
        }
        String name = args[1];
        if (!fields.exists(name)) {
            messages.send(sender, "zone-not-found", "name", name);
            return;
        }
        fields.removeZone(name);
        messages.send(sender, "zone-removed", "name", name);
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "forcefield.modify")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield toggle <name> [on|off]", NamedTextColor.RED));
            return;
        }
        String name = args[1];
        ForceFieldZone zone = fields.getZone(name);
        if (zone == null) {
            messages.send(sender, "zone-not-found", "name", name);
            return;
        }
        if (sender instanceof Player player
                && !zone.isOwnedBy(player.getUniqueId()) && !player.hasPermission("forcefield.admin")) {
            sender.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }

        boolean target;
        if (args.length >= 3) {
            target = args[2].equalsIgnoreCase("on");
        } else {
            target = !zone.isEnabled();
        }

        fields.setEnabled(zone, target);
        messages.send(sender, target ? "zone-raised" : "zone-lowered", "name", zone.getName());
    }

    private void handleList(CommandSender sender) {
        if (!requirePermission(sender, "forcefield.use")) {
            return;
        }
        if (fields.getZones().isEmpty()) {
            sender.sendMessage(Component.text("There are no force fields defined yet.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Force fields:", NamedTextColor.AQUA));
        for (ForceFieldZone zone : fields.getZones().values()) {
            NamedTextColor color = zone.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            String state = zone.isEnabled() ? "RAISED" : "lowered";
            sender.sendMessage(Component.text(" - " + zone.getName() + " (" + state + ")", color));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "forcefield.use")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield info <name>", NamedTextColor.RED));
            return;
        }
        ForceFieldZone zone = fields.getZone(args[1]);
        if (zone == null) {
            messages.send(sender, "zone-not-found", "name", args[1]);
            return;
        }
        Cuboid c = zone.getCuboid();
        sender.sendMessage(Component.text("Force field '" + zone.getName() + "'", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  World: " + c.getWorldName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Corners: (" + c.getMinX() + "," + c.getMinY() + "," + c.getMinZ()
                + ") to (" + c.getMaxX() + "," + c.getMaxY() + "," + c.getMaxZ() + ")", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Volume: " + c.volume() + " blocks", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  State: " + (zone.isEnabled() ? "RAISED" : "lowered"), NamedTextColor.GRAY));
        if (zone.getOwnerName() != null) {
            sender.sendMessage(Component.text("  Owner: " + zone.getOwnerName(), NamedTextColor.GRAY));
        }
        if (zone.hasRedstoneLink()) {
            sender.sendMessage(Component.text("  Linked to redstone at (" + zone.getRedstoneX() + ","
                    + zone.getRedstoneY() + "," + zone.getRedstoneZ() + ") in " + zone.getRedstoneWorld(), NamedTextColor.GRAY));
        }
    }

    private void handleLink(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "forcefield.admin")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield link <name>", NamedTextColor.RED));
            return;
        }
        String name = args[1];
        if (!fields.exists(name)) {
            messages.send(sender, "zone-not-found", "name", name);
            return;
        }
        selection.startLink((Player) sender, name);
        messages.send(sender, "link-start", "name", name);
    }

    private void handleUnlink(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "forcefield.admin")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /forcefield unlink <name>", NamedTextColor.RED));
            return;
        }
        ForceFieldZone zone = fields.getZone(args[1]);
        if (zone == null) {
            messages.send(sender, "zone-not-found", "name", args[1]);
            return;
        }
        zone.clearRedstoneLink();
        fields.save();
        messages.send(sender, "link-cleared", "name", zone.getName());
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, "forcefield.admin")) {
            return;
        }
        plugin.reloadConfig();
        effects.reload();
        fields.load();
        messages.send(sender, "reloaded");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("ForceField commands:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/forcefield wand", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield create <name> [confirm]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield remove <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield toggle <name> [on|off]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield info <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield link <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield unlink <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/forcefield reload", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("remove", "toggle", "info", "link", "unlink").contains(sub)) {
                for (String name : fields.getZones().keySet()) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(name);
                    }
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            out.add("on");
            out.add("off");
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            out.add("confirm");
            return out;
        }
        return out;
    }
}
