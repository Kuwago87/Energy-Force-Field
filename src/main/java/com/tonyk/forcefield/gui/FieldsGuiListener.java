package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.manager.SelectionManager;
import com.tonyk.forcefield.model.FieldShape;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.AdminBookItem;
import com.tonyk.forcefield.util.BookItem;
import com.tonyk.forcefield.util.Cuboid;
import com.tonyk.forcefield.util.LecternItem;
import com.tonyk.forcefield.util.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Handles everything around the "My Energy Force Fields" book (own zones)
 * and the admin "All Energy Force Fields" book (every zone, forcefield.admin
 * only): right-clicking either opens its paginated list, navigating the list
 * and detail menus, and the chat-based Rename and Change Owner flows started
 * from the detail menu's buttons.
 */
public final class FieldsGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final FieldManager fields;
    private final SelectionManager selection;
    private final Messages messages;

    public FieldsGuiListener(JavaPlugin plugin, FieldManager fields, SelectionManager selection, Messages messages) {
        this.plugin = plugin;
        this.fields = fields;
        this.selection = selection;
        this.messages = messages;
    }

    private long renameWindowMs() {
        return plugin.getConfig().getLong("rename-window-ms", 30000L);
    }

    private long ownerChangeWindowMs() {
        return plugin.getConfig().getLong("owner-change-window-ms", 30000L);
    }

    @EventHandler
    public void onBookInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!BookItem.isBook(plugin, item)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("forcefield.tool.book")) {
            messages.send(player, "no-permission");
            return;
        }
        openList(player, 0);
    }

    @EventHandler
    public void onAdminBookInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!AdminBookItem.isAdminBook(plugin, item)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("forcefield.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        openAdminCategoryChooser(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof FieldsListHolder listHolder) {
            handleListClick(event, listHolder);
        } else if (holder instanceof FieldDetailHolder detailHolder) {
            handleDetailClick(event, detailHolder);
        } else if (holder instanceof AdminCategoryHolder) {
            handleCategoryClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof FieldsListHolder || holder instanceof FieldDetailHolder || holder instanceof AdminCategoryHolder) {
            event.setCancelled(true);
        }
    }

    private void handleCategoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("forcefield.admin")) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot == AdminCategoryMenu.ROD_SLOT) {
            openAdminList(player, 0, FieldShape.CUBOID);
        } else if (slot == AdminCategoryMenu.BEACON_SLOT) {
            openAdminList(player, 0, FieldShape.SPHERE);
        }
    }

    /**
     * Captures the player's next chat message as a field's new name, if they
     * just clicked Rename. Cancelled (so it never gets broadcast as normal
     * chat) only when there actually was a live pending request - an expired
     * one is silently dropped and the message goes through as usual.
     */
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (selection.hasPendingRename(player)) {
            String zoneName = selection.consumePendingRename(player, renameWindowMs());
            if (zoneName == null) {
                return;
            }
            event.setCancelled(true);
            String newName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> finishRename(player, zoneName, newName));
            return;
        }

        if (selection.hasPendingOwnerChange(player)) {
            String zoneName = selection.consumePendingOwnerChange(player, ownerChangeWindowMs());
            if (zoneName == null) {
                return;
            }
            event.setCancelled(true);
            String targetName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> finishOwnerChange(player, zoneName, targetName));
        }
    }

    private void handleListClick(InventoryClickEvent event, FieldsListHolder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean adminView = holder.isAdminView();
        if (adminView && !player.hasPermission("forcefield.admin")) {
            player.closeInventory();
            return;
        }
        FieldShape categoryFilter = holder.getCategoryFilter();

        int slot = event.getSlot();
        if (slot == FieldsListMenu.BACK_TO_CATEGORIES_SLOT && adminView && categoryFilter != null) {
            openAdminCategoryChooser(player);
            return;
        }
        if (slot == FieldsListMenu.PREV_SLOT) {
            openListLike(player, holder.getPage() - 1, adminView, categoryFilter);
            return;
        }
        if (slot == FieldsListMenu.NEXT_SLOT) {
            openListLike(player, holder.getPage() + 1, adminView, categoryFilter);
            return;
        }
        if (slot >= FieldsListMenu.PAGE_SIZE) {
            return;
        }

        List<String> names = holder.getZoneNames();
        int index = holder.getPage() * FieldsListMenu.PAGE_SIZE + slot;
        if (index < 0 || index >= names.size()) {
            return;
        }
        String zoneName = names.get(index);
        if (fields.getZone(zoneName) == null) {
            player.sendMessage(Component.text("That field no longer exists.", NamedTextColor.RED));
            openListLike(player, holder.getPage(), adminView, categoryFilter);
            return;
        }
        openDetail(player, zoneName, holder.getPage(), adminView, categoryFilter);
    }

    private void handleDetailClick(InventoryClickEvent event, FieldDetailHolder holder) {
        event.setCancelled(true);
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean adminView = holder.isAdminView();
        if (adminView && !player.hasPermission("forcefield.admin")) {
            player.closeInventory();
            return;
        }
        FieldShape categoryFilter = holder.getCategoryFilter();

        int slot = event.getSlot();
        if (slot == FieldDetailMenu.BACK_SLOT) {
            Location lecternLocation = holder.getLecternLocation();
            if (lecternLocation != null) {
                if (!player.hasPermission("forcefield.modify")) {
                    messages.send(player, "no-permission");
                    player.closeInventory();
                    return;
                }
                removeLectern(player, lecternLocation);
                player.closeInventory();
                return;
            }
            openListLike(player, holder.getReturnPage(), adminView, categoryFilter);
            return;
        }

        ForceFieldZone zone = fields.getZone(holder.getZoneName());
        if (zone == null) {
            player.sendMessage(Component.text("That field no longer exists.", NamedTextColor.RED));
            openListLike(player, holder.getReturnPage(), adminView, categoryFilter);
            return;
        }
        if (!zone.isOwnedBy(player.getUniqueId()) && !player.hasPermission("forcefield.admin")) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        if (slot == FieldDetailMenu.RENAME_SLOT) {
            if (!player.hasPermission("forcefield.rename")) {
                messages.send(player, "no-permission");
                return;
            }
            player.closeInventory();
            selection.startRename(player, zone.getName());
            player.sendMessage(Component.text("Type the new name for '" + zone.getName()
                    + "' in chat (or type 'cancel'). You have " + (renameWindowMs() / 1000) + " seconds.", NamedTextColor.YELLOW));
        } else if (slot == FieldDetailMenu.CHANGE_OWNER_SLOT && adminView) {
            player.closeInventory();
            selection.startOwnerChange(player, zone.getName());
            player.sendMessage(Component.text("Type the new owner's exact username in chat for '" + zone.getName()
                    + "' (or type 'cancel'). They must be online. You have "
                    + (ownerChangeWindowMs() / 1000) + " seconds.", NamedTextColor.YELLOW));
        } else if (slot == FieldDetailMenu.COMPASS_SLOT) {
            pointCompass(player, zone);
        } else if (slot == FieldDetailMenu.PUBLIC_SLOT) {
            if (!player.hasPermission("forcefield.modify")) {
                messages.send(player, "no-permission");
                return;
            }
            boolean makePublic = !zone.isPublic();
            fields.setPublic(zone, makePublic);
            player.sendMessage(Component.text("'" + zone.getName() + "' is now "
                    + (makePublic ? "public - anyone can toggle it from its lecterns." : "private - only you/admins can toggle it."),
                    makePublic ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            openDetail(player, zone.getName(), holder.getReturnPage(), adminView, categoryFilter);
        } else if (slot == FieldDetailMenu.LEVER_SLOT) {
            if (!player.hasPermission("forcefield.modify")) {
                messages.send(player, "no-permission");
                return;
            }
            boolean target = !zone.isEnabled();
            fields.setEnabled(zone, target);
            messages.send(player, target ? "zone-raised" : "zone-lowered", "name", zone.getName());
            openDetail(player, zone.getName(), holder.getReturnPage(), adminView, categoryFilter);
        } else if (slot == FieldDetailMenu.LECTERN_SLOT) {
            if (!player.hasPermission("forcefield.modify")) {
                messages.send(player, "no-permission");
                return;
            }
            int count = Math.max(0, plugin.getConfig().getInt("lecterns-per-field", 2));
            LecternItem.giveSet(plugin, player, zone, count);
            player.sendMessage(Component.text("Gave you " + count + " lectern(s) linked to '" + zone.getName() + "'.", NamedTextColor.AQUA));
        } else if (slot == FieldDetailMenu.BARRIER_SLOT) {
            if (!player.hasPermission("forcefield.delete")) {
                messages.send(player, "no-permission");
                return;
            }
            fields.removeZone(zone.getName());
            messages.send(player, "zone-removed", "name", zone.getName());
            openListLike(player, holder.getReturnPage(), adminView, categoryFilter);
        }
    }

    private void finishRename(Player player, String oldName, String newName) {
        if (newName.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Rename cancelled.", NamedTextColor.GRAY));
            return;
        }
        if (!player.hasPermission("forcefield.rename")) {
            player.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }
        ForceFieldZone zone = fields.getZone(oldName);
        if (zone == null) {
            player.sendMessage(Component.text("That field no longer exists.", NamedTextColor.RED));
            return;
        }
        if (!zone.isOwnedBy(player.getUniqueId()) && !player.hasPermission("forcefield.admin")) {
            player.sendMessage(Component.text("That Energy Force Field belongs to someone else.", NamedTextColor.RED));
            return;
        }
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Component.text("Names must be 1-32 characters. Nothing was renamed.", NamedTextColor.RED));
            return;
        }
        if (!newName.matches("[A-Za-z0-9_-]+")) {
            player.sendMessage(Component.text("Names can only use letters, numbers, - and _. Nothing was renamed.", NamedTextColor.RED));
            return;
        }

        if (fields.renameZone(oldName, newName)) {
            player.sendMessage(Component.text("Renamed '" + oldName + "' to '" + newName + "'.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("There's already a field named '" + newName + "'. Nothing was renamed.", NamedTextColor.RED));
        }
    }

    private void finishOwnerChange(Player player, String zoneName, String targetName) {
        if (targetName.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Ownership change cancelled.", NamedTextColor.GRAY));
            return;
        }
        if (!player.hasPermission("forcefield.admin")) {
            player.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }
        ForceFieldZone zone = fields.getZone(zoneName);
        if (zone == null) {
            player.sendMessage(Component.text("That field no longer exists.", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(Component.text("No online player named '" + targetName
                    + "' found - they need to be online to receive ownership. Nothing was changed.", NamedTextColor.RED));
            return;
        }
        fields.changeOwner(zoneName, target.getUniqueId(), target.getName());
        player.sendMessage(Component.text("'" + zoneName + "' is now owned by " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You are now the owner of the Energy Force Field '" + zoneName + "'.", NamedTextColor.AQUA));
    }

    private void removeLectern(Player player, Location location) {
        Block block = location.getBlock();
        if (block.getType() == Material.LECTERN) {
            block.setType(Material.AIR);
            player.sendMessage(Component.text("Lectern removed.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("That lectern is already gone.", NamedTextColor.GRAY));
        }
    }

    private void pointCompass(Player player, ForceFieldZone zone) {
        Cuboid c = zone.getCuboid();
        World world = Bukkit.getWorld(c.getWorldName());
        if (world == null) {
            player.sendMessage(Component.text("That field's world isn't loaded right now.", NamedTextColor.RED));
            return;
        }
        double x = (c.getMinX() + c.getMaxX()) / 2.0 + 0.5;
        double y = (c.getMinY() + c.getMaxY()) / 2.0 + 0.5;
        double z = (c.getMinZ() + c.getMaxZ()) / 2.0 + 0.5;
        Location center = new Location(world, x, y, z);

        player.setCompassTarget(center);
        player.sendMessage(Component.text(zone.getName() + " is at "
                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                + " in " + c.getWorldName() + " - your compass now points to it.", NamedTextColor.AQUA));
    }

    private void openList(Player player, int page) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.openInventory(FieldsListMenu.create(plugin, fields, player, page)));
    }

    private void openAdminCategoryChooser(Player player) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.openInventory(AdminCategoryMenu.create(fields)));
    }

    private void openAdminList(Player player, int page, FieldShape categoryFilter) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.openInventory(FieldsListMenu.createAll(plugin, fields, page, categoryFilter)));
    }

    /** Reopens whichever list ({@link #openList} or {@link #openAdminList}) the player came from. */
    private void openListLike(Player player, int page, boolean adminView, FieldShape categoryFilter) {
        if (adminView) {
            openAdminList(player, page, categoryFilter);
        } else {
            openList(player, page);
        }
    }

    private void openDetail(Player player, String zoneName, int returnPage, boolean adminView, FieldShape categoryFilter) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.openInventory(FieldDetailMenu.create(fields, zoneName, returnPage, adminView, categoryFilter)));
    }
}
