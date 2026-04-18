package com.jellypudding.simpleBounty.gui;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.MessageUtil;
import com.jellypudding.simpleBounty.utils.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class GuiManager {

    public static final int LIST_ITEMS_PER_PAGE = 45;
    public static final int PLACE_CAPACITY = 45;

    private final SimpleBounty plugin;

    public GuiManager(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    public void openActiveList(Player viewer, int page) {
        openList(viewer, plugin.getBountyManager().getAllActive(), page,
                BountyGuiHolder.Type.LIST, "Active Bounties");
    }

    public void openTargetedAtMe(Player viewer, int page) {
        openList(viewer, plugin.getBountyManager().getActiveForTarget(viewer.getUniqueId()), page,
                BountyGuiHolder.Type.ON_ME, "Bounties On You");
    }

    public void openPlacedByMe(Player viewer, int page) {
        openList(viewer, plugin.getBountyManager().getActiveByPlacer(viewer.getUniqueId()), page,
                BountyGuiHolder.Type.MINE, "Your Placed Bounties");
    }

    public void openBountiesOnTarget(Player viewer, UUID targetUuid, String targetName, int page) {
        List<Bounty> bounties = plugin.getBountyManager().getActiveForTarget(targetUuid);
        openList(viewer, bounties, page, BountyGuiHolder.Type.LIST,
                "Bounties on " + targetName);
    }

    private void openList(Player viewer, Collection<Bounty> source, int page,
                          BountyGuiHolder.Type type, String titleText) {
        List<Bounty> list = new ArrayList<>(source);
        list.sort(Comparator
                .comparingInt((Bounty b) -> -b.getTotalItemCount())
                .thenComparingLong(Bounty::getCreatedAt));

        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) LIST_ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        BountyGuiHolder holder = new BountyGuiHolder(type);
        holder.set("page", page);
        holder.set("totalPages", totalPages);

        Component title = Component.text(titleText, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" (" + (page + 1) + "/" + totalPages + ")", NamedTextColor.GRAY));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * LIST_ITEMS_PER_PAGE;
        int end = Math.min(start + LIST_ITEMS_PER_PAGE, list.size());
        long[] ids = new long[LIST_ITEMS_PER_PAGE];
        for (int i = 0; i < LIST_ITEMS_PER_PAGE; i++) ids[i] = -1;
        for (int i = start; i < end; i++) {
            Bounty b = list.get(i);
            int slot = i - start;
            inv.setItem(slot, buildBountyHead(b));
            ids[slot] = b.getId();
        }
        holder.set("ids", ids);

        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, GuiItems.filler());
        }
        if (page > 0) inv.setItem(45, GuiItems.previousPage());
        if (page < totalPages - 1) inv.setItem(53, GuiItems.nextPage());

        ItemStack info = GuiItems.infoBook("Bounties: " + list.size(),
                "Left-click a head to view full reward details.",
                type == BountyGuiHolder.Type.MINE ? "Shift-click a head to cancel that bounty." : "");
        inv.setItem(49, info);
        inv.setItem(48, GuiItems.close());

        viewer.openInventory(inv);
    }

    private ItemStack buildBountyHead(Bounty b) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Placer: ", NamedTextColor.GRAY)
                .append(PlayerUtil.getPlayerDisplayName(b.getPlacerName(), b.getPlacerUuid())));
        lore.add(Component.text("Bounty ID: #" + b.getId(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Items: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.count(b.getStackCount(), "stack"), NamedTextColor.YELLOW))
                .append(Component.text(" / " + MessageUtil.count(b.getTotalItemCount(), "item") + " total", NamedTextColor.DARK_GRAY)));
        lore.add(Component.text("Expires in: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatDuration(b.getMillisUntilExpiry()), NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(Component.text("Left-click for details.", NamedTextColor.DARK_GRAY));

        Component name = Component.text("Target: ", NamedTextColor.GRAY)
                .append(PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid()));

        return GuiItems.playerHead(b.getTargetUuid(), name, lore);
    }

    public void openDetail(Player viewer, long bountyId) {
        Bounty b = plugin.getBountyManager().getActive(bountyId);
        if (b == null) {
            viewer.sendMessage(MessageUtil.error("That bounty is no longer active."));
            return;
        }
        BountyGuiHolder holder = new BountyGuiHolder(BountyGuiHolder.Type.DETAIL);
        holder.set("bountyId", bountyId);

        Component title = Component.text("Bounty #" + b.getId() + " - ", NamedTextColor.GOLD)
                .append(Component.text(b.getTargetName(), NamedTextColor.RED));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Placed by: ", NamedTextColor.GRAY)
                .append(PlayerUtil.getPlayerDisplayName(b.getPlacerName(), b.getPlacerUuid())));
        lore.add(Component.text("Expires in: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatDuration(b.getMillisUntilExpiry()), NamedTextColor.YELLOW)));
        lore.add(Component.text("Reward: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.count(b.getStackCount(), "stack"), NamedTextColor.YELLOW))
                .append(Component.text(" (" + MessageUtil.count(b.getTotalItemCount(), "item") + " total)", NamedTextColor.DARK_GRAY)));
        Component headName = Component.text("Kill ", NamedTextColor.RED)
                .append(PlayerUtil.getPlayerDisplayName(b.getTargetName(), b.getTargetUuid()))
                .append(Component.text(" to claim", NamedTextColor.RED));
        inv.setItem(4, GuiItems.playerHead(b.getTargetUuid(), headName, lore));

        for (int s = 0; s < 9; s++) {
            if (s != 4) inv.setItem(s, GuiItems.filler());
        }

        List<ItemStack> items = b.getItems();
        int slot = 9;
        for (ItemStack item : items) {
            if (slot >= 54) break;
            if (item == null || item.getType().isAir()) continue;
            inv.setItem(slot++, item.clone());
        }
        while (slot < 54) {
            inv.setItem(slot++, null);
        }

        viewer.openInventory(inv);
    }

    public void openPlace(Player viewer, UUID targetUuid, String targetName) {
        BountyGuiHolder holder = new BountyGuiHolder(BountyGuiHolder.Type.PLACE);
        holder.set("targetUuid", targetUuid);
        holder.set("targetName", targetName);
        holder.set("confirmed", false);

        Component title = Component.text("Place bounty on ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.RED));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        inv.setItem(45, buildPlaceInfoHead(targetUuid, targetName));
        for (int s = 46; s < 54; s++) inv.setItem(s, GuiItems.filler());

        long hours = plugin.getConfig().getLong("default-expiration-hours", 168);
        String hoursLabel = hours == 1 ? "hour" : "hours";
        inv.setItem(48, GuiItems.button(Material.CLOCK, "Duration", NamedTextColor.YELLOW,
                "This bounty will expire in " + hours + " " + hoursLabel,
                "if not claimed. Items will be returned",
                "to you automatically."));

        inv.setItem(50, GuiItems.confirm());
        inv.setItem(53, GuiItems.cancel());

        viewer.openInventory(inv);
    }

    private ItemStack buildPlaceInfoHead(UUID uuid, String name) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Place items above to offer them as a reward.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Click CONFIRM to publish the bounty.", NamedTextColor.GREEN));
        lore.add(Component.text("Click CANCEL to get your items back.", NamedTextColor.RED));
        Component headName = Component.text("Target: ", NamedTextColor.GRAY)
                .append(PlayerUtil.getPlayerDisplayName(name, uuid));
        return GuiItems.playerHead(uuid, headName, lore);
    }

    public static boolean isPlaceItemSlot(int slot) {
        return slot >= 0 && slot < PLACE_CAPACITY;
    }
}
