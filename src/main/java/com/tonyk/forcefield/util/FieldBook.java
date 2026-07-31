package com.tonyk.forcefield.util;

import com.tonyk.forcefield.manager.FieldManager;
import com.tonyk.forcefield.model.ForceFieldZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a written book listing everything a player owns, for the "book"
 * item in the EFF Tools GUI. Generated fresh every time it's opened, so it's
 * always up to date - it's a viewer, not a saved record.
 */
public final class FieldBook {

    private FieldBook() {
    }

    public static ItemStack build(FieldManager fields, Player player) {
        List<ForceFieldZone> owned = fields.getZonesOwnedBy(player.getUniqueId());

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Energy Force Fields"));
        meta.author(Component.text(player.getName()));

        List<Component> pages = new ArrayList<>();
        if (owned.isEmpty()) {
            pages.add(Component.text("You haven't created any Energy Force Fields yet.", NamedTextColor.BLACK)
                    .append(Component.newline()).append(Component.newline())
                    .append(Component.text("Grab the Create/Delete rod from ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("/eff_tools", NamedTextColor.DARK_BLUE))
                    .append(Component.text(" to make one.", NamedTextColor.DARK_GRAY)));
        } else {
            for (ForceFieldZone zone : owned) {
                pages.add(buildPage(zone));
            }
        }
        meta.pages(pages);

        book.setItemMeta(meta);
        return book;
    }

    private static Component buildPage(ForceFieldZone zone) {
        Cuboid c = zone.getCuboid();
        Component page = Component.text(zone.getName(), NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("World: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(c.getWorldName(), NamedTextColor.BLACK))
                .append(Component.newline())
                .append(Component.text("From: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(c.getMinX() + ", " + c.getMinY() + ", " + c.getMinZ(), NamedTextColor.BLACK))
                .append(Component.newline())
                .append(Component.text("To: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(c.getMaxX() + ", " + c.getMaxY() + ", " + c.getMaxZ(), NamedTextColor.BLACK))
                .append(Component.newline())
                .append(Component.text("Volume: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(c.volume() + " blocks", NamedTextColor.BLACK))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("State: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(zone.isEnabled() ? "RAISED" : "lowered",
                        zone.isEnabled() ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY, TextDecoration.BOLD));

        if (zone.hasRedstoneLink()) {
            page = page.append(Component.newline()).append(Component.newline())
                    .append(Component.text("Linked to a redstone trigger", NamedTextColor.DARK_PURPLE));
        }

        return page;
    }
}
