package com.jellypudding.simpleBounty.gui;

import com.jellypudding.simpleBounty.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class GuiItems {

    private GuiItems() {}

    public static ItemStack button(Material material, String name, NamedTextColor colour, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MessageUtil.clean(Component.text(name, colour)));
        if (loreLines.length > 0) {
            meta.lore(Arrays.stream(loreLines)
                    .map(line -> MessageUtil.clean(Component.text(line, NamedTextColor.GRAY)))
                    .toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MessageUtil.clean(Component.text(" ")));
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack playerHead(UUID uuid, Component displayName, List<Component> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (uuid != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
        }
        if (displayName != null) {
            meta.displayName(MessageUtil.clean(displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream().map(MessageUtil::clean).toList());
        }
        skull.setItemMeta(meta);
        return skull;
    }

    public static ItemStack confirm() {
        return button(Material.LIME_CONCRETE, "Confirm Bounty", NamedTextColor.GREEN,
                "Click to confirm and place your bounty.",
                "Items will be taken from this menu.");
    }

    public static ItemStack cancel() {
        return button(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED,
                "Items will be returned to you.");
    }

    public static ItemStack previousPage() {
        return button(Material.ARROW, "Previous Page", NamedTextColor.YELLOW);
    }

    public static ItemStack nextPage() {
        return button(Material.ARROW, "Next Page", NamedTextColor.YELLOW);
    }

    public static ItemStack close() {
        return button(Material.BARRIER, "Close", NamedTextColor.RED);
    }

    public static ItemStack back() {
        return button(Material.FEATHER, "Back", NamedTextColor.YELLOW, "Return to the bounty list.");
    }

    public static ItemStack infoBook(String title, String... loreLines) {
        return button(Material.BOOK, title, NamedTextColor.GOLD, loreLines);
    }
}
