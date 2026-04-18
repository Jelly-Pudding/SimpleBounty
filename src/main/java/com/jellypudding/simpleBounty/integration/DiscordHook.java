package com.jellypudding.simpleBounty.integration;

import com.jellypudding.discordRelay.DiscordRelayAPI;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.List;

// See https://github.com/Jelly-Pudding/minecraft-discord-relay
public final class DiscordHook {

    private DiscordHook() {}

    private static boolean available() {
        try {
            return DiscordRelayAPI.isReady();
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
    }

    public static void announceBountyPlaced(Bounty bounty) {
        if (!available()) return;
        try {
            DiscordRelayAPI.sendFormattedMessage(
                    "Bounty Placed",
                    bounty.getPlacerName() + " placed a bounty on " + bounty.getTargetName()
                            + " (" + MessageUtil.count(bounty.getStackCount(), "item stack")
                            + ", " + bounty.getTotalItemCount() + " total).",
                    new Color(255, 170, 0)
            );
        } catch (Exception ignored) {
        }
    }

    public static void announceBountyClaimed(List<Bounty> bounties, Player killer) {
        if (!available() || bounties == null || bounties.isEmpty()) return;
        Bounty first = bounties.get(0);
        int count = bounties.size();
        try {
            String body;
            if (count == 1) {
                body = killer.getName() + " claimed a bounty on " + first.getTargetName() + "!";
            } else {
                body = killer.getName() + " claimed "
                        + MessageUtil.count(count, "bounty", "bounties")
                        + " on " + first.getTargetName() + "!";
            }
            DiscordRelayAPI.sendFormattedMessage("Bounty Claimed", body, new Color(85, 255, 85));
        } catch (Exception ignored) {
        }
    }

    public static void announceBountyExpired(Bounty bounty) {
        if (!available()) return;
        try {
            DiscordRelayAPI.sendFormattedMessage(
                    "Bounty Expired",
                    "The bounty on " + bounty.getTargetName() + " (#" + bounty.getId() + ") expired and items were returned.",
                    new Color(170, 170, 170)
            );
        } catch (Exception ignored) {
        }
    }
}
