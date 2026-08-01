package com.tonyk.forcefield.gui;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.FieldShape;
import com.tonyk.forcefield.model.ForceFieldZone;
import com.tonyk.forcefield.util.Cuboid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the single-field management GUI: a name tag to rename it, a compass
 * showing its location, an oak door to toggle public/private access, a
 * lever to remotely raise/lower it, a lectern to get replacement lecterns,
 * and a barrier to delete it. The last slot is either a paper "back to
 * list" button (opened from a book) or a "remove this lectern" button
 * (opened by double-left-clicking a lectern). The admin variant (reached
 * from the admin book, or by an admin double-left-clicking any lectern)
 * adds a player head button to transfer ownership.
 */
public final class FieldDetailMenu {

    public static final int SIZE = 9;
    public static final int RENAME_SLOT = 0;
    public static final int CHANGE_OWNER_SLOT = 1;
    public static final int COMPASS_SLOT = 2;
    public static final int PUBLIC_SLOT = 3;
    public static final int LEVER_SLOT = 4;
    public static final int LECTERN_SLOT = 5;
    public static final int BARRIER_SLOT = 6;
    public static final int BACK_SLOT = 8;

    private FieldDetailMenu() {
    }

    public static Inventory create(FieldManager fields, String zoneName, int returnPage) {
        return create(fields, zoneName, returnPage, false, null);
    }

    public static Inventory create(FieldManager fields, String zoneName, int returnPage, boolean adminView, FieldShape categoryFilter) {
        FieldDetailHolder holder = new FieldDetailHolder(zoneName, returnPage, adminView, categoryFilter);
        return build(fields, zoneName, holder, adminView, backIcon());
    }

    /** Opened by double-left-clicking a lectern - the last slot removes that specific lectern instead of navigating back. */
    public static Inventory createForLectern(FieldManager fields, String zoneName, boolean adminView, Location lecternLocation) {
        FieldDetailHolder holder = new FieldDetailHolder(zoneName, adminView, lecternLocation);
        return build(fields, zoneName, holder, adminView, removeLecternIcon());
    }

    private static Inventory build(FieldManager fields, String zoneName, FieldDetailHolder holder,
                                    boolean adminView, ItemStack lastSlotIcon) {
        ForceFieldZone zone = fields.getZone(zoneName);
        String title = zone != null ? zone.getName() : zoneName;

        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(title, NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        ItemStack filler = fillerPane();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(RENAME_SLOT, renameIcon());
        if (adminView) {
            inventory.setItem(CHANGE_OWNER_SLOT, changeOwnerIcon(zone));
        }
        inventory.setItem(COMPASS_SLOT, compassIcon(zone));
        inventory.setItem(PUBLIC_SLOT, publicIcon(zone));
        inventory.setItem(LEVER_SLOT, leverIcon(zone));
        inventory.setItem(LECTERN_SLOT, lecternIcon());
        inventory.setItem(BARRIER_SLOT, deleteIcon());
        inventory.setItem(BACK_SLOT, lastSlotIcon);

        return inventory;
    }

    private static ItemStack renameIcon() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Rename", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click, then type the new name in chat", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack changeOwnerIcon(ForceFieldZone zone) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Change Owner", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (zone != null) {
            String owner = zone.getOwnerName() != null ? zone.getOwnerName() : "nobody";
            lore.add(Component.text("Currently: " + owner, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("Click, then type a player's name in chat", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack compassIcon(ForceFieldZone zone) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Location", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (zone != null) {
            Cuboid c = zone.getCuboid();
            int cx = (c.getMinX() + c.getMaxX()) / 2;
            int cy = (c.getMinY() + c.getMaxY()) / 2;
            int cz = (c.getMinZ() + c.getMaxZ()) / 2;
            lore.add(Component.text("World: " + c.getWorldName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("X: " + cx + "  Y: " + cy + "  Z: " + cz, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("Click to point your compass at it", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack publicIcon(ForceFieldZone zone) {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        boolean isPublic = zone != null && zone.isPublic();
        meta.displayName(Component.text("Access: " + (isPublic ? "Public" : "Private"),
                        isPublic ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (isPublic) {
            lore.add(Component.text("Anyone can toggle this field from its", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("lecterns, not just you/admins.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Only you (or an admin) can toggle", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("this field, including from its lecterns.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack lecternIcon() {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Get Replacement Lecterns", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Gives you 2 more lecterns linked to", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("this field - handy if one was destroyed", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("or you want to move it.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to receive them", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack leverIcon(ForceFieldZone zone) {
        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Remote On / Off", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (zone != null) {
            lore.add(Component.text("Currently: ", NamedTextColor.GRAY)
                    .append(Component.text(zone.isEnabled() ? "RAISED" : "lowered",
                            zone.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack deleteIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Delete", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click to permanently delete this field", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backIcon() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("« Back to list", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack removeLecternIcon() {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Remove This Lectern", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Breaks the lectern you double-clicked", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(only this one - the field is unaffected)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack fillerPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty().decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
