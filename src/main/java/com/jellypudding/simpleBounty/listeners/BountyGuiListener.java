package com.jellypudding.simpleBounty.listeners;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.gui.BountyGuiHolder;
import com.jellypudding.simpleBounty.gui.GuiManager;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BountyGuiListener implements Listener {

    private final SimpleBounty plugin;

    public BountyGuiListener(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BountyGuiHolder bh)) return;

        Player player = (Player) event.getWhoClicked();
        switch (bh.getType()) {
            case LIST, ON_ME, MINE -> handleListClick(event, bh, player);
            case DETAIL -> event.setCancelled(true);
            case PLACE -> handlePlaceClick(event, bh, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BountyGuiHolder bh)) return;
        if (bh.getType() == BountyGuiHolder.Type.PLACE) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize() && !GuiManager.isPlaceItemSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        } else {
            event.setCancelled(true);
        }
    }

    private void handleListClick(InventoryClickEvent event, BountyGuiHolder bh, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        int page = bh.getInt("page", 0);
        int totalPages = bh.getInt("totalPages", 1);

        if (slot == 45 && page > 0) {
            reopenList(bh, player, page - 1);
            return;
        }
        if (slot == 53 && page < totalPages - 1) {
            reopenList(bh, player, page + 1);
            return;
        }
        if (slot == 48) {
            player.closeInventory();
            return;
        }
        if (slot >= 0 && slot < GuiManager.LIST_ITEMS_PER_PAGE) {
            long[] ids = bh.get("ids");
            if (ids == null) return;
            long bountyId = ids[slot];
            if (bountyId < 0) return;

            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                if (bh.getType() == BountyGuiHolder.Type.MINE) {
                    Bounty b = plugin.getBountyManager().getActive(bountyId);
                    if (b != null && b.getPlacerUuid().equals(player.getUniqueId())) {
                        Component targetName = PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid());
                        if (plugin.getBountyManager().cancelBounty(b, "cancelled")) {
                            player.sendMessage(MessageUtil.prefix()
                                    .append(Component.text("Bounty #" + bountyId + " on ", NamedTextColor.GREEN))
                                    .append(targetName)
                                    .append(Component.text(" was cancelled.", NamedTextColor.GREEN)));
                            plugin.getBountyManager().deliverPendingReturns(player, "bounty items (cancelled)");
                        }
                        reopenList(bh, player, page);
                    }
                    return;
                }
            }
            plugin.getGuiManager().openDetail(player, bountyId);
        }
    }

    private void reopenList(BountyGuiHolder bh, Player player, int page) {
        switch (bh.getType()) {
            case LIST -> plugin.getGuiManager().openActiveList(player, page);
            case ON_ME -> plugin.getGuiManager().openTargetedAtMe(player, page);
            case MINE -> plugin.getGuiManager().openPlacedByMe(player, page);
            default -> {}
        }
    }

    private void handlePlaceClick(InventoryClickEvent event, BountyGuiHolder bh, Player player) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= top.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack moving = event.getCurrentItem();
                if (moving == null || moving.getType().isAir()) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                Map<Integer, ItemStack> leftover = addToPlaceArea(top, moving);
                if (leftover.isEmpty()) {
                    event.setCurrentItem(null);
                } else {
                    event.setCurrentItem(leftover.get(0));
                }
            }
            return;
        }

        if (GuiManager.isPlaceItemSlot(rawSlot)) {
            return;
        }

        event.setCancelled(true);

        if (rawSlot == 50) {
            confirmPlacement(top, bh, player);
        } else if (rawSlot == 53) {
            player.closeInventory();
        }
    }

    private Map<Integer, ItemStack> addToPlaceArea(Inventory top, ItemStack moving) {
        Inventory fake = org.bukkit.Bukkit.createInventory(null, 45);
        for (int i = 0; i < GuiManager.PLACE_CAPACITY; i++) {
            fake.setItem(i, top.getItem(i));
        }
        Map<Integer, ItemStack> leftovers = fake.addItem(moving.clone());
        for (int i = 0; i < GuiManager.PLACE_CAPACITY; i++) {
            top.setItem(i, fake.getItem(i));
        }
        return leftovers;
    }

    private void confirmPlacement(Inventory top, BountyGuiHolder bh, Player player) {
        UUID targetUuid = bh.get("targetUuid");
        String targetName = bh.get("targetName");
        if (targetUuid == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < GuiManager.PLACE_CAPACITY; i++) {
            ItemStack it = top.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            items.add(it.clone());
        }

        if (items.isEmpty()) {
            player.sendMessage(MessageUtil.error("You must place at least one item."));
            return;
        }

        int max = plugin.getConfig().getInt("max-items-per-bounty", 45);
        if (items.size() > max) {
            player.sendMessage(MessageUtil.error("A bounty can hold at most " + MessageUtil.count(max, "item stack") + "."));
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

        long durationMs = plugin.getConfig().getLong("default-expiration-hours", 168) * 3600_000L;

        bh.set("confirmed", true);

        for (int i = 0; i < GuiManager.PLACE_CAPACITY; i++) {
            top.setItem(i, null);
        }

        Bounty created = plugin.getBountyManager().createBounty(
                player.getUniqueId(), player.getName(),
                targetUuid, targetName,
                items, durationMs
        );

        if (created == null) {
            player.sendMessage(MessageUtil.error("Failed to save bounty. Your items have been returned."));
            for (ItemStack it : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
                for (ItemStack spill : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), spill);
                }
            }
            player.closeInventory();
            return;
        }

        Component targetDisplayName = PlayerUtil.getPlayerDisplayName(targetName, targetUuid);
        player.sendMessage(MessageUtil.prefix()
                .append(Component.text("Bounty #" + created.getId() + " placed on ", NamedTextColor.GREEN))
                .append(targetDisplayName)
                .append(Component.text(".", NamedTextColor.GREEN)));
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BountyGuiHolder bh)) return;
        if (bh.getType() != BountyGuiHolder.Type.PLACE) return;
        if (bh.getBoolean("confirmed")) return;

        Player player = (Player) event.getPlayer();
        Inventory top = event.getView().getTopInventory();
        refundPlaceInventory(player, top);
    }

    public static void refundPlaceInventory(Player player, Inventory top) {
        boolean hadItems = false;
        for (int i = 0; i < GuiManager.PLACE_CAPACITY; i++) {
            ItemStack it = top.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            top.setItem(i, null);
            hadItems = true;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
            for (ItemStack spill : leftover.values()) {
                if (player.isOnline()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), spill);
                }
            }
        }
        if (hadItems) {
            player.sendMessage(MessageUtil.info("Your items were returned."));
        }
    }
}
