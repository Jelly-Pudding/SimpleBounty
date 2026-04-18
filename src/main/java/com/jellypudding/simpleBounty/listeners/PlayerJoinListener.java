package com.jellypudding.simpleBounty.listeners;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final SimpleBounty plugin;

    public PlayerJoinListener(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            int pendingCount = plugin.getDatabaseManager().countPendingReturns(player.getUniqueId());
            if (pendingCount > 0 && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(MessageUtil.prefix()
                            .append(Component.text("You have ", NamedTextColor.GRAY))
                            .append(MessageUtil.countComponent(pendingCount, NamedTextColor.YELLOW, NamedTextColor.GRAY,
                                    "item", "items"))
                            .append(Component.text(" waiting from expired or cancelled bounties. Use ", NamedTextColor.GRAY))
                            .append(Component.text("/bounty claimreturns", NamedTextColor.GOLD))
                            .append(Component.text(" to collect ", NamedTextColor.GRAY))
                            .append(Component.text(pendingCount == 1 ? "it" : "them", NamedTextColor.GRAY))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                });
            }

            int bountiesOnMe = plugin.getBountyManager().getActiveForTarget(player.getUniqueId()).size();
            if (bountiesOnMe > 0 && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(MessageUtil.prefix()
                            .append(Component.text("There ", NamedTextColor.GRAY))
                            .append(Component.text(bountiesOnMe == 1 ? "is " : "are ", NamedTextColor.GRAY))
                            .append(MessageUtil.countComponent(bountiesOnMe, NamedTextColor.RED, NamedTextColor.GRAY,
                                    "active bounty", "active bounties"))
                            .append(Component.text(" on you! Use ", NamedTextColor.GRAY))
                            .append(Component.text("/bounty me", NamedTextColor.GOLD))
                            .append(Component.text(" to view ", NamedTextColor.GRAY))
                            .append(Component.text(bountiesOnMe == 1 ? "it" : "them", NamedTextColor.GRAY))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                });
            }
        }, 40L);
    }
}
