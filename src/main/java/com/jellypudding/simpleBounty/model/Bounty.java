package com.jellypudding.simpleBounty.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Bounty {

    private final long id;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID placerUuid;
    private final String placerName;
    private final long createdAt;
    private long expiresAt;
    private final List<ItemStack> items;

    public Bounty(long id, UUID targetUuid, String targetName, UUID placerUuid,
                  String placerName, long createdAt, long expiresAt, List<ItemStack> items) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.placerUuid = placerUuid;
        this.placerName = placerName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.items = new ArrayList<>(items);
    }

    public long getId() { return id; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public UUID getPlacerUuid() { return placerUuid; }
    public String getPlacerName() { return placerName; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public List<ItemStack> getItems() { return Collections.unmodifiableList(items); }

    public int getTotalItemCount() {
        int total = 0;
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir()) total += it.getAmount();
        }
        return total;
    }

    public int getStackCount() {
        int total = 0;
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir()) total++;
        }
        return total;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getMillisUntilExpiry() {
        return expiresAt - System.currentTimeMillis();
    }
}
