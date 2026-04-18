package com.jellypudding.simpleBounty.commands;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
            "list", "place", "view", "mine", "me", "cancel", "claimreturns", "help"
    );

    private final SimpleBounty plugin;

    public BountyCommand(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be run by a player.");
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().openActiveList(player, 0);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> plugin.getGuiManager().openActiveList(player, 0);
            case "mine" -> plugin.getGuiManager().openPlacedByMe(player, 0);
            case "me" -> plugin.getGuiManager().openTargetedAtMe(player, 0);
            case "place" -> handlePlace(player, args);
            case "view" -> handleView(player, args);
            case "cancel" -> handleCancel(player, args);
            case "claimreturns" -> handleClaimReturns(player);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handlePlace(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /bounty place <player>"));
            return;
        }
        if (!player.hasPermission("simplebounty.command.bounty.place")) {
            player.sendMessage(MessageUtil.error("You do not have permission to place bounties."));
            return;
        }
        String requestedName = args[1];
        UUID targetUuid = PlayerUtil.getPlayerUUID(requestedName);
        if (targetUuid == null) {
            player.sendMessage(MessageUtil.error("Could not find player '" + requestedName + "'."));
            return;
        }
        String exactName = PlayerUtil.getExactPlayerName(requestedName);
        if (!plugin.getConfig().getBoolean("allow-self-bounty", false)
                && targetUuid.equals(player.getUniqueId())) {
            player.sendMessage(MessageUtil.error("You cannot place a bounty on yourself."));
            return;
        }
        int currentActive = plugin.getBountyManager().countActiveByPlacer(player.getUniqueId());
        int maxActive = plugin.getConfig().getInt("max-active-bounties-per-placer", 10);
        if (currentActive >= maxActive) {
            player.sendMessage(MessageUtil.error("You already have the maximum of "
                    + MessageUtil.count(maxActive, "active bounty", "active bounties") + "."));
            return;
        }
        if (!plugin.getBountyManager().canPlaceBounty(player.getUniqueId())) {
            long wait = plugin.getBountyManager().millisUntilNextPlacement(player.getUniqueId());
            player.sendMessage(MessageUtil.error("You are placing bounties too quickly. Try again in "
                    + MessageUtil.formatDuration(wait) + "."));
            return;
        }
        plugin.getGuiManager().openPlace(player, targetUuid, exactName);
    }

    private void handleView(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /bounty view <player>"));
            return;
        }
        String requestedName = args[1];
        UUID targetUuid = PlayerUtil.getPlayerUUID(requestedName);
        if (targetUuid == null) {
            player.sendMessage(MessageUtil.error("Could not find player '" + requestedName + "'."));
            return;
        }
        String exactName = PlayerUtil.getExactPlayerName(requestedName);
        Component targetDisplay = PlayerUtil.getPlayerDisplayName(exactName, targetUuid);
        List<Bounty> bounties = plugin.getBountyManager().getActiveForTarget(targetUuid);
        if (bounties.isEmpty()) {
            player.sendMessage(MessageUtil.prefix()
                    .append(Component.text("There are no active bounties on ", NamedTextColor.GRAY))
                    .append(targetDisplay)
                    .append(Component.text(".", NamedTextColor.GRAY)));
            return;
        }
        plugin.getGuiManager().openBountiesOnTarget(player, targetUuid, exactName, 0);
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /bounty cancel <id>"));
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtil.error("Invalid bounty id."));
            return;
        }
        Bounty b = plugin.getBountyManager().getActive(id);
        if (b == null) {
            player.sendMessage(MessageUtil.error("Bounty #" + id + " is not active."));
            return;
        }
        if (!b.getPlacerUuid().equals(player.getUniqueId())) {
            player.sendMessage(MessageUtil.error("You can only cancel bounties you placed."));
            return;
        }
        Component targetDisplay = PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid());
        if (plugin.getBountyManager().cancelBounty(b, "cancelled")) {
            player.sendMessage(MessageUtil.prefix()
                    .append(Component.text("Bounty #" + id + " on ", NamedTextColor.GREEN))
                    .append(targetDisplay)
                    .append(Component.text(" was cancelled.", NamedTextColor.GREEN)));
            plugin.getBountyManager().deliverPendingReturns(player, "bounty items (cancelled)");
        }
    }

    private void handleClaimReturns(Player player) {
        if (plugin.getDatabaseManager().countPendingReturns(player.getUniqueId()) == 0) {
            player.sendMessage(MessageUtil.info("You have no pending returns to claim."));
            return;
        }
        plugin.getBountyManager().deliverPendingReturns(player, "pending returns");
    }

    private void sendHelp(Player player) {
        player.sendMessage(MessageUtil.prefix().append(Component.text("Commands:", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("  /bounty list", NamedTextColor.YELLOW)
                .append(Component.text(" - View all active bounties", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty place <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Place a bounty", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty view <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - View bounties on a player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty mine", NamedTextColor.YELLOW)
                .append(Component.text(" - Bounties you have placed", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty me", NamedTextColor.YELLOW)
                .append(Component.text(" - Bounties placed on you", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty cancel <id>", NamedTextColor.YELLOW)
                .append(Component.text(" - Cancel one of your bounties", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /bounty claimreturns", NamedTextColor.YELLOW)
                .append(Component.text(" - Collect pending item returns", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("view"))) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel") && sender instanceof Player p) {
            String prefix = args[1];
            return plugin.getBountyManager().getActiveByPlacer(p.getUniqueId()).stream()
                    .map(b -> String.valueOf(b.getId()))
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
