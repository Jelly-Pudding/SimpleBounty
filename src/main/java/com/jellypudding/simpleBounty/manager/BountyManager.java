package com.jellypudding.simpleBounty.manager;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.database.DatabaseManager;
import com.jellypudding.simpleBounty.integration.DiscordHook;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BountyManager {

    private final SimpleBounty plugin;
    private final DatabaseManager db;

    private final Map<Long, Bounty> activeById = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> byTarget = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> byPlacer = new ConcurrentHashMap<>();

    private final Map<UUID, Deque<Long>> recentPlacements = new ConcurrentHashMap<>();

    public BountyManager(SimpleBounty plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void loadAll() {
        activeById.clear();
        byTarget.clear();
        byPlacer.clear();
        List<Bounty> loaded = db.loadAllBounties();
        for (Bounty b : loaded) {
            indexBounty(b);
        }
        plugin.getLogger().info("Loaded " + MessageUtil.count(loaded.size(), "active bounty", "active bounties") + ".");
    }

    private void indexBounty(Bounty b) {
        activeById.put(b.getId(), b);
        byTarget.computeIfAbsent(b.getTargetUuid(), k -> new ArrayList<>()).add(b.getId());
        byPlacer.computeIfAbsent(b.getPlacerUuid(), k -> new ArrayList<>()).add(b.getId());
    }

    private void deindexBounty(Bounty b) {
        activeById.remove(b.getId());
        List<Long> t = byTarget.get(b.getTargetUuid());
        if (t != null) {
            t.remove(b.getId());
            if (t.isEmpty()) byTarget.remove(b.getTargetUuid());
        }
        List<Long> p = byPlacer.get(b.getPlacerUuid());
        if (p != null) {
            p.remove(b.getId());
            if (p.isEmpty()) byPlacer.remove(b.getPlacerUuid());
        }
    }

    public Bounty getActive(long id) {
        return activeById.get(id);
    }

    public Collection<Bounty> getAllActive() {
        return Collections.unmodifiableCollection(activeById.values());
    }

    public List<Bounty> getActiveForTarget(UUID targetUuid) {
        List<Long> ids = byTarget.get(targetUuid);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Bounty> result = new ArrayList<>();
        for (Long id : new ArrayList<>(ids)) {
            Bounty b = activeById.get(id);
            if (b != null) result.add(b);
        }
        return result;
    }

    public List<Bounty> getActiveByPlacer(UUID placerUuid) {
        List<Long> ids = byPlacer.get(placerUuid);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Bounty> result = new ArrayList<>();
        for (Long id : new ArrayList<>(ids)) {
            Bounty b = activeById.get(id);
            if (b != null) result.add(b);
        }
        return result;
    }

    public List<Map.Entry<UUID, List<Bounty>>> getActiveGroupedByTarget() {
        Map<UUID, List<Bounty>> grouped = new HashMap<>();
        for (Bounty b : activeById.values()) {
            grouped.computeIfAbsent(b.getTargetUuid(), k -> new ArrayList<>()).add(b);
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<UUID, List<Bounty>> e) -> -totalValue(e.getValue())))
                .collect(Collectors.toList());
    }

    private int totalValue(List<Bounty> list) {
        int total = 0;
        for (Bounty b : list) total += b.getTotalItemCount();
        return total;
    }

    public int countActiveByPlacer(UUID placerUuid) {
        List<Long> ids = byPlacer.get(placerUuid);
        return ids == null ? 0 : ids.size();
    }

    private boolean rateLimitEnabled() {
        return plugin.getConfig().getBoolean("placement-rate-limit.enabled", true);
    }

    private int rateLimitMax() {
        return Math.max(1, plugin.getConfig().getInt("placement-rate-limit.max-placements", 20));
    }

    private long rateLimitWindowMillis() {
        int minutes = Math.max(1, plugin.getConfig().getInt("placement-rate-limit.window-minutes", 60));
        return minutes * 60_000L;
    }

    public int placementsInWindow(UUID placerUuid) {
        Deque<Long> times = recentPlacements.get(placerUuid);
        if (times == null || times.isEmpty()) return 0;
        pruneOld(times, System.currentTimeMillis() - rateLimitWindowMillis());
        return times.size();
    }

    public boolean canPlaceBounty(UUID placerUuid) {
        if (!rateLimitEnabled()) return true;
        return placementsInWindow(placerUuid) < rateLimitMax();
    }

    public long millisUntilNextPlacement(UUID placerUuid) {
        if (!rateLimitEnabled()) return 0;
        Deque<Long> times = recentPlacements.get(placerUuid);
        if (times == null || times.isEmpty()) return 0;
        long windowMs = rateLimitWindowMillis();
        long cutoff = System.currentTimeMillis() - windowMs;
        pruneOld(times, cutoff);
        if (times.size() < rateLimitMax()) return 0;
        Long oldest = times.peekFirst();
        if (oldest == null) return 0;
        return Math.max(0, oldest + windowMs - System.currentTimeMillis());
    }

    private void recordPlacement(UUID placerUuid) {
        Deque<Long> times = recentPlacements.computeIfAbsent(placerUuid, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(System.currentTimeMillis());
            pruneOld(times, System.currentTimeMillis() - rateLimitWindowMillis());
        }
    }

    private void pruneOld(Deque<Long> times, long cutoff) {
        synchronized (times) {
            Iterator<Long> it = times.iterator();
            while (it.hasNext()) {
                if (it.next() < cutoff) it.remove();
                else break;
            }
        }
    }

    public Bounty createBounty(UUID placerUuid, String placerName, UUID targetUuid, String targetName,
                               List<ItemStack> items, long durationMillis) {
        long now = System.currentTimeMillis();
        long expires = now + durationMillis;
        long id = db.insertBounty(targetUuid, targetName, placerUuid, placerName, now, expires, items);
        if (id < 0) return null;
        Bounty bounty = new Bounty(id, targetUuid, targetName, placerUuid, placerName, now, expires, items);
        indexBounty(bounty);
        recordPlacement(placerUuid);
        announcePlacement(bounty);
        return bounty;
    }

    public boolean cancelBounty(Bounty bounty, String reason) {
        if (bounty == null || !activeById.containsKey(bounty.getId())) return false;
        return resolveForPlacer(bounty, reason);
    }

    public boolean expireBounty(Bounty bounty) {
        if (bounty == null || !activeById.containsKey(bounty.getId())) return false;
        if (resolveForPlacer(bounty, "expired")) {
            announceExpiration(bounty);
            return true;
        }
        return false;
    }

    public boolean claimBounty(Bounty bounty, Player killer) {
        if (bounty == null || !activeById.containsKey(bounty.getId())) return false;

        List<ItemStack> items = new ArrayList<>(bounty.getItems());

        boolean persisted = db.resolveBountyAtomic(bounty.getId(), killer.getUniqueId(), items, "claim");
        if (!persisted) {
            killer.sendMessage(MessageUtil.error("Could not finalise the bounty claim right now. Please try again."));
            return false;
        }
        deindexBounty(bounty);
        return true;
    }

    public void extendBounty(Bounty bounty, long extraMillis) {
        if (bounty == null || !activeById.containsKey(bounty.getId())) return;
        long newExpiry = bounty.getExpiresAt() + extraMillis;
        bounty.setExpiresAt(newExpiry);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> db.updateBountyExpiry(bounty.getId(), newExpiry));
    }

    private boolean resolveForPlacer(Bounty bounty, String reason) {
        List<ItemStack> items = new ArrayList<>(bounty.getItems());

        boolean persisted = db.resolveBountyAtomic(bounty.getId(), bounty.getPlacerUuid(), items, reason);
        if (!persisted) {
            Player placer = Bukkit.getPlayer(bounty.getPlacerUuid());
            if (placer != null && placer.isOnline()) {
                placer.sendMessage(MessageUtil.error("Could not finalise bounty return right now. Your items will be retried automatically."));
            }
            return false;
        }
        deindexBounty(bounty);
        return true;
    }

    public int deliverPendingReturnsIfOnline(UUID playerUuid, String label) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return 0;
        return deliverPendingReturns(player, label);
    }

    public int deliverPendingReturns(Player player, String label) {
        List<ItemStack> pending = db.drainPendingReturns(player.getUniqueId());
        if (pending.isEmpty()) return 0;

        boolean dropOverflow = plugin.getConfig().getBoolean("drop-on-full-inventory", false);
        Map<Integer, ItemStack> leftovers = MessageUtil.giveItems(player, pending, dropOverflow);

        if (!leftovers.isEmpty()) {
            List<ItemStack> overflow = new ArrayList<>(leftovers.values());
            try {
                db.addPendingReturns(player.getUniqueId(), overflow, "overflow");
                player.sendMessage(MessageUtil.info("Some " + label + " did not fit. They have been saved. Use /bounty claimreturns."));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to re-queue overflow for " + player.getName() + ". Dropping at feet to prevent loss.");
                for (ItemStack item : overflow) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        } else {
            player.sendMessage(MessageUtil.success("Received your " + label + "."));
        }
        return pending.size() - leftovers.size();
    }

    private void announcePlacement(Bounty bounty) {
        if (plugin.getConfig().getBoolean("announce-placement", true)) {
            Component targetName = PlayerUtil.getPlayerDisplayName(bounty.getTargetName(), bounty.getTargetUuid());
            Component placerName = PlayerUtil.getPlayerDisplayName(bounty.getPlacerName(), bounty.getPlacerUuid());
            Component msg = MessageUtil.prefix()
                    .append(placerName)
                    .append(Component.text(" placed a bounty on ", NamedTextColor.GRAY))
                    .append(targetName)
                    .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(MessageUtil.countComponent(bounty.getStackCount(), NamedTextColor.YELLOW, NamedTextColor.GRAY,
                            "stack", "stacks"))
                    .append(Component.text(").", NamedTextColor.DARK_GRAY));
            Bukkit.broadcast(msg);
        }

        if (plugin.isDiscordRelayEnabled()
                && plugin.getConfig().getBoolean("discord.enabled", true)
                && plugin.getConfig().getBoolean("discord.announce-placement", true)) {
            DiscordHook.announceBountyPlaced(bounty);
        }
    }

    private void announceExpiration(Bounty bounty) {
        if (plugin.getConfig().getBoolean("announce-expiration", false)) {
            Component targetName = PlayerUtil.getPlayerDisplayName(bounty.getTargetName(), bounty.getTargetUuid());
            Component msg = MessageUtil.prefix()
                    .append(Component.text("The bounty on ", NamedTextColor.GRAY))
                    .append(targetName)
                    .append(Component.text(" (#" + bounty.getId() + ") has expired.", NamedTextColor.GRAY));
            Bukkit.broadcast(msg);
        }
        if (plugin.isDiscordRelayEnabled()
                && plugin.getConfig().getBoolean("discord.enabled", true)
                && plugin.getConfig().getBoolean("discord.announce-expiration", false)) {
            DiscordHook.announceBountyExpired(bounty);
        }
    }

    public void announceClaims(List<Bounty> bounties, Player killer) {
        if (bounties == null || bounties.isEmpty()) return;
        Bounty first = bounties.get(0);
        int count = bounties.size();

        if (plugin.getConfig().getBoolean("announce-claim", true)) {
            Component targetName = PlayerUtil.getPlayerDisplayName(first.getTargetName(), first.getTargetUuid());
            Component killerName = PlayerUtil.getPlayerDisplayName(killer.getName(), killer.getUniqueId());
            Component msg;
            if (count == 1) {
                msg = MessageUtil.prefix()
                        .append(killerName)
                        .append(Component.text(" claimed a bounty on ", NamedTextColor.GRAY))
                        .append(targetName)
                        .append(Component.text("!", NamedTextColor.GRAY));
            } else {
                msg = MessageUtil.prefix()
                        .append(killerName)
                        .append(Component.text(" claimed ", NamedTextColor.GRAY))
                        .append(MessageUtil.countComponent(count, NamedTextColor.YELLOW, NamedTextColor.GRAY,
                                "bounty", "bounties"))
                        .append(Component.text(" on ", NamedTextColor.GRAY))
                        .append(targetName)
                        .append(Component.text("!", NamedTextColor.GRAY));
            }
            Bukkit.broadcast(msg);
        }

        if (plugin.isDiscordRelayEnabled()
                && plugin.getConfig().getBoolean("discord.enabled", true)
                && plugin.getConfig().getBoolean("discord.announce-claim", true)) {
            DiscordHook.announceBountyClaimed(bounties, killer);
        }
    }
}
