package com.jellypudding.simpleBounty.listeners;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.manager.BountyManager;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;

public class PlayerDeathListener implements Listener {

    private final SimpleBounty plugin;

    public PlayerDeathListener(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;

        BountyManager manager = plugin.getBountyManager();
        List<Bounty> bounties = manager.getActiveForTarget(victim.getUniqueId());
        if (bounties.isEmpty()) return;

        boolean allowSelfClaim = plugin.getConfig().getBoolean("allow-placer-self-claim", true);

        List<Bounty> claimedBounties = new ArrayList<>();
        for (Bounty b : bounties) {
            if (!allowSelfClaim && b.getPlacerUuid().equals(killer.getUniqueId())) continue;
            manager.claimBounty(b, killer);
            claimedBounties.add(b);
        }
        if (!claimedBounties.isEmpty()) {
            manager.announceClaims(claimedBounties, killer);
            Component victimName = PlayerUtil.getPlayerDisplayName(victim.getName(), victim.getUniqueId());
            killer.sendMessage(MessageUtil.prefix()
                    .append(Component.text("You claimed ", NamedTextColor.GREEN))
                    .append(MessageUtil.countComponent(claimedBounties.size(), NamedTextColor.YELLOW, NamedTextColor.GREEN,
                            "bounty", "bounties"))
                    .append(Component.text(" on ", NamedTextColor.GREEN))
                    .append(victimName)
                    .append(Component.text("!", NamedTextColor.GREEN)));
        }
    }
}
