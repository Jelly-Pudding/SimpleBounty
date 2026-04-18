package com.jellypudding.simpleBounty.listeners;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.manager.BountyManager;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

// Pays out bounties when a player kills a BattleLock combat-log NPC.
// See https://github.com/Jelly-Pudding/BattleLock
public class BattleLockNpcListener implements Listener {

    private final SimpleBounty plugin;
    private final NamespacedKey battleLockKey;

    public BattleLockNpcListener(SimpleBounty plugin) {
        this.plugin = plugin;
        this.battleLockKey = new NamespacedKey("battlelock", "combat_log_player_id");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager npc)) return;
        if (event.getFinalDamage() < npc.getHealth()) return;
        if (!npc.getPersistentDataContainer().has(battleLockKey, PersistentDataType.STRING)) return;

        Player killer = resolveKiller(event.getDamager());
        if (killer == null) return;

        String uuidString = npc.getPersistentDataContainer().get(battleLockKey, PersistentDataType.STRING);
        if (uuidString == null) return;

        UUID combatLoggerUuid;
        try {
            combatLoggerUuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid BattleLock NPC UUID: " + uuidString, e);
            return;
        }
        if (combatLoggerUuid.equals(killer.getUniqueId())) return;

        BountyManager manager = plugin.getBountyManager();
        List<Bounty> bounties = manager.getActiveForTarget(combatLoggerUuid);
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
            String loggerName = PlayerUtil.getNameFromUUID(combatLoggerUuid);
            Component loggerDisplayName = PlayerUtil.getPlayerDisplayName(loggerName, combatLoggerUuid);
            killer.sendMessage(MessageUtil.prefix()
                    .append(Component.text("You killed ", NamedTextColor.GREEN))
                    .append(loggerDisplayName)
                    .append(Component.text("'s combat-logged NPC and claimed ", NamedTextColor.GREEN))
                    .append(MessageUtil.countComponent(claimedBounties.size(), NamedTextColor.YELLOW, NamedTextColor.GREEN,
                            "bounty", "bounties"))
                    .append(Component.text("!", NamedTextColor.GREEN)));
        }
    }

    private Player resolveKiller(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
