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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BountyAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
            "reload", "cancel", "cancelall", "clearplacer", "extend", "info", "list"
    );

    private final SimpleBounty plugin;

    public BountyAdminCommand(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("simplebounty.command.admin")) {
            sender.sendMessage(MessageUtil.error("You do not have permission to use this command."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "cancel" -> handleCancel(sender, args);
            case "cancelall" -> handleCancelAll(sender, args);
            case "clearplacer" -> handleClearPlacer(sender, args);
            case "extend" -> handleExtend(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        if (plugin.getExpirationManager() != null) {
            plugin.getExpirationManager().restart();
        }
        sender.sendMessage(MessageUtil.success("Configuration reloaded."));
    }

    private void handleCancel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /bountyadmin cancel <id>"));
            return;
        }
        long id;
        try { id = Long.parseLong(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.error("Invalid bounty id."));
            return;
        }
        Bounty b = plugin.getBountyManager().getActive(id);
        if (b == null) {
            sender.sendMessage(MessageUtil.error("Bounty #" + id + " is not active."));
            return;
        }
        Component placerDisplay = PlayerUtil.getPlayerDisplayName(b.getPlacerName(), b.getPlacerUuid());
        plugin.getBountyManager().cancelBounty(b, "admin cancel");
        sender.sendMessage(MessageUtil.prefix()
                .append(Component.text("Bounty #" + id + " cancelled. Items returned to ", NamedTextColor.GREEN))
                .append(placerDisplay)
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleCancelAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /bountyadmin cancelall <target-player>"));
            return;
        }
        UUID targetUuid = PlayerUtil.getPlayerUUID(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(MessageUtil.error("Unknown player: " + args[1]));
            return;
        }
        List<Bounty> bounties = new ArrayList<>(plugin.getBountyManager().getActiveForTarget(targetUuid));
        if (bounties.isEmpty()) {
            sender.sendMessage(MessageUtil.info("No active bounties on that player."));
            return;
        }
        for (Bounty b : bounties) {
            plugin.getBountyManager().cancelBounty(b, "admin cancel");
        }
        Component targetDisplay = PlayerUtil.getPlayerDisplayName(PlayerUtil.getExactPlayerName(args[1]), targetUuid);
        sender.sendMessage(MessageUtil.prefix()
                .append(Component.text("Cancelled ", NamedTextColor.GREEN))
                .append(MessageUtil.countComponent(bounties.size(), NamedTextColor.YELLOW, NamedTextColor.GREEN,
                        "bounty", "bounties"))
                .append(Component.text(" on ", NamedTextColor.GREEN))
                .append(targetDisplay)
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleClearPlacer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /bountyadmin clearplacer <placer-player>"));
            return;
        }
        UUID placerUuid = PlayerUtil.getPlayerUUID(args[1]);
        if (placerUuid == null) {
            sender.sendMessage(MessageUtil.error("Unknown player: " + args[1]));
            return;
        }
        List<Bounty> bounties = new ArrayList<>(plugin.getBountyManager().getActiveByPlacer(placerUuid));
        if (bounties.isEmpty()) {
            sender.sendMessage(MessageUtil.info("That player has no active bounties."));
            return;
        }
        for (Bounty b : bounties) {
            plugin.getBountyManager().cancelBounty(b, "admin cancel");
        }
        Component placerDisplay = PlayerUtil.getPlayerDisplayName(PlayerUtil.getExactPlayerName(args[1]), placerUuid);
        sender.sendMessage(MessageUtil.prefix()
                .append(Component.text("Cancelled ", NamedTextColor.GREEN))
                .append(MessageUtil.countComponent(bounties.size(), NamedTextColor.YELLOW, NamedTextColor.GREEN,
                        "bounty", "bounties"))
                .append(Component.text(" placed by ", NamedTextColor.GREEN))
                .append(placerDisplay)
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleExtend(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.error("Usage: /bountyadmin extend <id> <hours>"));
            return;
        }
        long id;
        double hours;
        try {
            id = Long.parseLong(args[1]);
            hours = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.error("Invalid id or hours value."));
            return;
        }
        Bounty b = plugin.getBountyManager().getActive(id);
        if (b == null) {
            sender.sendMessage(MessageUtil.error("Bounty #" + id + " is not active."));
            return;
        }
        long extraMs = (long) (hours * 3600_000L);
        plugin.getBountyManager().extendBounty(b, extraMs);
        sender.sendMessage(MessageUtil.success("Bounty #" + id + " extended by " + hours + " "
                + (hours == 1.0 ? "hour" : "hours") + "."));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("Usage: /bountyadmin info <id>"));
            return;
        }
        long id;
        try { id = Long.parseLong(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.error("Invalid bounty id."));
            return;
        }
        Bounty b = plugin.getBountyManager().getActive(id);
        if (b == null) {
            b = plugin.getDatabaseManager().getBountyById(id);
        }
        if (b == null) {
            sender.sendMessage(MessageUtil.error("No bounty with id " + id + "."));
            return;
        }
        Component targetDisplay = PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid());
        Component placerDisplay = PlayerUtil.getPlayerDisplayName(b.getPlacerName(), b.getPlacerUuid());

        sender.sendMessage(MessageUtil.prefix().append(Component.text("Bounty #" + b.getId(), NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("Target: ", NamedTextColor.GRAY).append(targetDisplay)
                .append(Component.text(" (" + b.getTargetUuid() + ")", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Placer: ", NamedTextColor.GRAY).append(placerDisplay)
                .append(Component.text(" (" + b.getPlacerUuid() + ")", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Items: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.count(b.getStackCount(), "stack"), NamedTextColor.YELLOW))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(Component.text(MessageUtil.count(b.getTotalItemCount(), "item"), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Expires in: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatDuration(b.getMillisUntilExpiry()), NamedTextColor.YELLOW)));
    }

    private void handleList(CommandSender sender) {
        var all = plugin.getBountyManager().getAllActive();
        sender.sendMessage(MessageUtil.prefix()
                .append(Component.text("Active bounties: ", NamedTextColor.GOLD))
                .append(Component.text(all.size(), NamedTextColor.YELLOW)));
        for (Bounty b : all) {
            Component targetDisplay = PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid());
            Component placerDisplay = PlayerUtil.getPlayerDisplayName(b.getPlacerName(), b.getPlacerUuid());
            sender.sendMessage(Component.text("#" + b.getId() + " ", NamedTextColor.YELLOW)
                    .append(targetDisplay)
                    .append(Component.text(" by ", NamedTextColor.GRAY))
                    .append(placerDisplay)
                    .append(Component.text(" - " + MessageUtil.count(b.getStackCount(), "stack")
                            + " - " + MessageUtil.formatDuration(b.getMillisUntilExpiry()) + " left", NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.prefix().append(Component.text("Admin commands:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  /bountyadmin reload", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin info <id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin cancel <id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin cancelall <target>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin clearplacer <placer>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /bountyadmin extend <id> <hours>", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("simplebounty.command.admin")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUB_COMMANDS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            if (sub.equals("cancel") || sub.equals("extend") || sub.equals("info")) {
                return plugin.getBountyManager().getAllActive().stream()
                        .map(b -> String.valueOf(b.getId()))
                        .filter(s -> s.startsWith(prefix))
                        .collect(Collectors.toList());
            }
            if (sub.equals("cancelall") || sub.equals("clearplacer")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
