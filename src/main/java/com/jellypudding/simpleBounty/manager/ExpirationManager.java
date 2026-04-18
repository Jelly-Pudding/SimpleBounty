package com.jellypudding.simpleBounty.manager;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.model.Bounty;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class ExpirationManager {

    private final SimpleBounty plugin;
    private BukkitTask task;

    public ExpirationManager(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long intervalSeconds = Math.max(5, plugin.getConfig().getLong("expiration-check-interval-seconds", 60));
        long intervalTicks = intervalSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpired, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    private void checkExpired() {
        BountyManager manager = plugin.getBountyManager();
        if (manager == null) return;
        List<Bounty> expired = new ArrayList<>();
        for (Bounty b : manager.getAllActive()) {
            if (b.isExpired()) expired.add(b);
        }
        for (Bounty b : expired) {
            manager.expireBounty(b);
        }
    }
}
