package com.jellypudding.simpleBounty;

import com.jellypudding.simpleBounty.commands.BountyAdminCommand;
import com.jellypudding.simpleBounty.commands.BountyCommand;
import com.jellypudding.simpleBounty.database.DatabaseManager;
import com.jellypudding.simpleBounty.gui.BountyGuiHolder;
import com.jellypudding.simpleBounty.gui.GuiManager;
import com.jellypudding.simpleBounty.listeners.BattleLockNpcListener;
import com.jellypudding.simpleBounty.listeners.BountyGuiListener;
import com.jellypudding.simpleBounty.listeners.PlayerDeathListener;
import com.jellypudding.simpleBounty.listeners.PlayerJoinListener;
import com.jellypudding.simpleBounty.manager.BountyManager;
import com.jellypudding.simpleBounty.manager.ExpirationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleBounty extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BountyManager bountyManager;
    private GuiManager guiManager;
    private ExpirationManager expirationManager;

    private boolean discordRelayEnabled = false;
    private boolean chromaTagEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialise();

        bountyManager = new BountyManager(this);
        bountyManager.loadAll();

        guiManager = new GuiManager(this);

        checkPluginIntegrations();

        expirationManager = new ExpirationManager(this);
        expirationManager.start();

        registerListeners();
        registerCommands();

        // Initialise bStats.
        new Metrics(this, 30828);

        getLogger().info("SimpleBounty has been enabled.");
        getLogger().info("Plugin integrations: DiscordRelay=" + discordRelayEnabled
                + ", ChromaTag=" + chromaTagEnabled);
    }

    @Override
    public void onDisable() {
        if (expirationManager != null) {
            expirationManager.stop();
        }

        // Refund players still in a Place GUI so nothing is lost on shutdown.
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                InventoryHolder topHolder = player.getOpenInventory().getTopInventory().getHolder();
                if (topHolder instanceof BountyGuiHolder bh && bh.getType() == BountyGuiHolder.Type.PLACE
                        && !bh.getBoolean("confirmed")) {
                    BountyGuiListener.refundPlaceInventory(player, player.getOpenInventory().getTopInventory());
                    player.closeInventory();
                }
            } catch (Exception e) {
                getLogger().warning("Failed to refund bounty placement for " + player.getName() + ": " + e.getMessage());
            }
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("SimpleBounty has been disabled.");
    }

    private void checkPluginIntegrations() {
        Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordRelay");
        if (discord != null && discord.isEnabled()) {
            discordRelayEnabled = true;
            getLogger().info("DiscordRelay integration enabled.");
        } else {
            getLogger().info("DiscordRelay not found. Discord announcements disabled.");
        }

        Plugin chromaTag = Bukkit.getPluginManager().getPlugin("ChromaTag");
        if (chromaTag != null && chromaTag.isEnabled()) {
            chromaTagEnabled = true;
            getLogger().info("ChromaTag integration enabled.");
        } else {
            getLogger().info("ChromaTag not found. Player colours will use defaults.");
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new BountyGuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BattleLockNpcListener(this), this);
    }

    private void registerCommands() {
        BountyCommand bountyCmd = new BountyCommand(this);
        getCommand("bounty").setExecutor(bountyCmd);
        getCommand("bounty").setTabCompleter(bountyCmd);

        BountyAdminCommand adminCmd = new BountyAdminCommand(this);
        getCommand("bountyadmin").setExecutor(adminCmd);
        getCommand("bountyadmin").setTabCompleter(adminCmd);
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public BountyManager getBountyManager() { return bountyManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public ExpirationManager getExpirationManager() { return expirationManager; }

    public boolean isDiscordRelayEnabled() { return discordRelayEnabled; }
    public boolean isChromaTagEnabled() { return chromaTagEnabled; }
}
