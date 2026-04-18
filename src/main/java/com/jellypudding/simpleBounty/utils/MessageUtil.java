package com.jellypudding.simpleBounty.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MessageUtil {

    public static final NamedTextColor ACCENT = NamedTextColor.GOLD;
    public static final NamedTextColor TEXT = NamedTextColor.GRAY;
    public static final NamedTextColor EMPHASIS = NamedTextColor.YELLOW;
    public static final NamedTextColor ERROR = NamedTextColor.RED;
    public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
    public static final NamedTextColor MUTED = NamedTextColor.DARK_GRAY;

    private MessageUtil() {}

    public static Component prefix() {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Bounty", ACCENT, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .build();
    }

    public static Component error(String msg) {
        return prefix().append(Component.text(msg, ERROR));
    }

    public static Component error(Component msg) {
        return prefix().append(msg.colorIfAbsent(ERROR));
    }

    public static Component info(String msg) {
        return prefix().append(Component.text(msg, TEXT));
    }

    public static Component info(Component msg) {
        return prefix().append(msg.colorIfAbsent(TEXT));
    }

    public static Component success(String msg) {
        return prefix().append(Component.text(msg, SUCCESS));
    }

    public static Component success(Component msg) {
        return prefix().append(msg.colorIfAbsent(SUCCESS));
    }

    public static String pluralise(long count, String singular, String pluralForm) {
        return count == 1 ? singular : pluralForm;
    }

    public static String count(long count, String singular, String pluralForm) {
        return count + " " + pluralise(count, singular, pluralForm);
    }

    public static String count(long count, String singular) {
        return count + " " + (count == 1 ? singular : singular + "s");
    }

    public static Component countComponent(long count, NamedTextColor numberColour,
                                           NamedTextColor wordColour,
                                           String singular, String pluralForm) {
        return Component.text(count, numberColour)
                .append(Component.text(" " + pluralise(count, singular, pluralForm), wordColour));
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "expired";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
                - TimeUnit.DAYS.toMinutes(days)
                - TimeUnit.HOURS.toMinutes(hours);
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes);
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public static Component clean(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    // Adds items to a player's inventory and drops overflow at their location if dropOverflow is true.
    // Returns a map of items that couldn't be placed when dropOverflow is false (used for pending returns).
    public static Map<Integer, ItemStack> giveItems(Player player, Iterable<ItemStack> items, boolean dropOverflow) {
        Map<Integer, ItemStack> leftovers = new HashMap<>();
        int idx = 0;
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack.clone());
            if (!left.isEmpty()) {
                ItemStack remaining = left.values().iterator().next();
                if (dropOverflow) {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world != null) {
                        world.dropItemNaturally(loc, remaining);
                    }
                } else {
                    leftovers.put(idx, remaining);
                }
            }
            idx++;
        }
        return leftovers;
    }
}
