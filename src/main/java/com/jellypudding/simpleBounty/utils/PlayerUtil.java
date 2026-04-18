package com.jellypudding.simpleBounty.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class PlayerUtil {

    private static volatile Plugin cachedChromaTag;
    private static volatile Method cachedChromaTagMethod;

    public static UUID getPlayerUUID(String playerName) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                return offlinePlayer.getUniqueId();
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public static String getExactPlayerName(String playerName) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                String cachedName = offlinePlayer.getName();
                if (cachedName != null) {
                    return cachedName;
                }
            }
        } catch (Exception e) {
            return playerName;
        }

        return playerName;
    }

    public static String getNameFromUUID(UUID uuid) {
        if (uuid == null) return "Unknown";
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    public static Component getPlayerDisplayName(String playerName, UUID playerUuid) {
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.displayName();
        }

        TextColor colour = lookupChromaTagColour(playerUuid);
        if (colour != null) {
            return Component.text(playerName, colour);
        }
        return Component.text(playerName, NamedTextColor.WHITE);
    }

    private static TextColor lookupChromaTagColour(UUID playerUuid) {
        Plugin plugin = cachedChromaTag;
        Method method = cachedChromaTagMethod;

        if (plugin == null || method == null || !plugin.isEnabled()) {
            plugin = Bukkit.getPluginManager().getPlugin("ChromaTag");
            if (plugin == null || !plugin.isEnabled()) {
                cachedChromaTag = null;
                cachedChromaTagMethod = null;
                return null;
            }
            try {
                method = plugin.getClass().getMethod("getPlayerColor", UUID.class);
            } catch (NoSuchMethodException e) {
                cachedChromaTag = null;
                cachedChromaTagMethod = null;
                return null;
            }
            cachedChromaTag = plugin;
            cachedChromaTagMethod = method;
        }

        try {
            return (TextColor) method.invoke(plugin, playerUuid);
        } catch (Exception e) {
            return null;
        }
    }

    public static Component getPlayerDisplayName(UUID uuid) {
        return getPlayerDisplayName(getNameFromUUID(uuid), uuid);
    }
}
