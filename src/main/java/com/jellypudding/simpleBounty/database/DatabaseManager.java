package com.jellypudding.simpleBounty.database;

import com.jellypudding.simpleBounty.SimpleBounty;
import com.jellypudding.simpleBounty.model.Bounty;
import com.jellypudding.simpleBounty.utils.ItemSerialiser;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final SimpleBounty plugin;
    private Connection connection;
    private final Object connectionLock = new Object();

    public DatabaseManager(SimpleBounty plugin) {
        this.plugin = plugin;
    }

    public void initialise() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Failed to create plugin data folder.");
        }

        File dbFile = new File(dataFolder, "bounties.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
                st.execute("PRAGMA journal_mode = WAL;");
            }
            createTables();
            plugin.getLogger().info("Database initialised at " + dbFile.getName());
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise SQLite database.", e);
        }
    }

    private void createTables() throws SQLException {
        synchronized (connectionLock) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS bounties (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        target_uuid TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        placer_uuid TEXT NOT NULL,
                        placer_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        items_blob BLOB NOT NULL
                    );
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS pending_returns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    );
                    """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_bounties_target ON bounties(target_uuid);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_bounties_placer ON bounties(placer_uuid);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_bounties_expires ON bounties(expires_at);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_pending_player ON pending_returns(player_uuid);");
            }
        }
    }

    public void close() {
        synchronized (connectionLock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Error closing database connection.", e);
                }
            }
        }
    }

    private Connection getConnection() throws SQLException {
        synchronized (connectionLock) {
            if (connection == null || connection.isClosed()) {
                File dbFile = new File(plugin.getDataFolder(), "bounties.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            return connection;
        }
    }

    public long insertBounty(UUID targetUuid, String targetName, UUID placerUuid, String placerName,
                             long createdAt, long expiresAt, List<ItemStack> items) {
        synchronized (connectionLock) {
            String sql = "INSERT INTO bounties (target_uuid, target_name, placer_uuid, placer_name, created_at, expires_at, items_blob) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, targetName);
                ps.setString(3, placerUuid.toString());
                ps.setString(4, placerName);
                ps.setLong(5, createdAt);
                ps.setLong(6, expiresAt);
                ps.setBytes(7, ItemSerialiser.serialiseList(items));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to insert bounty.", e);
            }
            return -1;
        }
    }

    public List<Bounty> loadAllBounties() {
        List<Bounty> results = new ArrayList<>();
        synchronized (connectionLock) {
            String sql = "SELECT id, target_uuid, target_name, placer_uuid, placer_name, created_at, expires_at, items_blob FROM bounties";
            try (PreparedStatement ps = getConnection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(readBounty(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load bounties.", e);
            }
        }
        return results;
    }

    public Bounty getBountyById(long id) {
        synchronized (connectionLock) {
            String sql = "SELECT id, target_uuid, target_name, placer_uuid, placer_name, created_at, expires_at, items_blob FROM bounties WHERE id = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return readBounty(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch bounty " + id, e);
            }
        }
        return null;
    }

    private Bounty readBounty(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        UUID targetUuid = UUID.fromString(rs.getString("target_uuid"));
        String targetName = rs.getString("target_name");
        UUID placerUuid = UUID.fromString(rs.getString("placer_uuid"));
        String placerName = rs.getString("placer_name");
        long createdAt = rs.getLong("created_at");
        long expiresAt = rs.getLong("expires_at");
        byte[] blob = rs.getBytes("items_blob");
        List<ItemStack> items = ItemSerialiser.deserialiseList(blob);
        return new Bounty(id, targetUuid, targetName, placerUuid, placerName, createdAt, expiresAt, items);
    }

    public void deleteBounty(long id) {
        synchronized (connectionLock) {
            String sql = "DELETE FROM bounties WHERE id = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete bounty " + id, e);
            }
        }
    }

    public boolean resolveBountyAtomic(long bountyId, UUID pendingPlayerUuid,
                                       List<ItemStack> pendingItems, String reason) {
        synchronized (connectionLock) {
            Connection conn;
            try {
                conn = getConnection();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to acquire connection for atomic resolve of bounty " + bountyId, e);
                return false;
            }

            try {
                conn.setAutoCommit(false);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to begin transaction for bounty " + bountyId, e);
                return false;
            }

            try {
                if (pendingItems != null && !pendingItems.isEmpty() && pendingPlayerUuid != null) {
                    String insert = "INSERT INTO pending_returns (player_uuid, item_data, reason, created_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insert)) {
                        long now = System.currentTimeMillis();
                        boolean anyAdded = false;
                        for (ItemStack item : pendingItems) {
                            byte[] data = ItemSerialiser.serialise(item);
                            if (data == null) continue;
                            ps.setString(1, pendingPlayerUuid.toString());
                            ps.setBytes(2, data);
                            ps.setString(3, reason);
                            ps.setLong(4, now);
                            ps.addBatch();
                            anyAdded = true;
                        }
                        if (anyAdded) ps.executeBatch();
                    }
                }

                String delete = "DELETE FROM bounties WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(delete)) {
                    ps.setLong(1, bountyId);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Atomic resolve failed for bounty " + bountyId + "; rolling back.", e);
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Rollback failed for bounty " + bountyId, rollbackEx);
                }
                return false;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void updateBountyExpiry(long id, long expiresAt) {
        synchronized (connectionLock) {
            String sql = "UPDATE bounties SET expires_at = ? WHERE id = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, expiresAt);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update bounty expiry for " + id, e);
            }
        }
    }

    public void addPendingReturns(UUID playerUuid, List<ItemStack> items, String reason) {
        synchronized (connectionLock) {
            String sql = "INSERT INTO pending_returns (player_uuid, item_data, reason, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (ItemStack item : items) {
                    byte[] data = ItemSerialiser.serialise(item);
                    if (data == null) continue;
                    ps.setString(1, playerUuid.toString());
                    ps.setBytes(2, data);
                    ps.setString(3, reason);
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add pending returns for " + playerUuid, e);
            }
        }
    }

    public int countPendingReturns(UUID playerUuid) {
        synchronized (connectionLock) {
            String sql = "SELECT COUNT(*) FROM pending_returns WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to count pending returns.", e);
            }
        }
        return 0;
    }

    public List<ItemStack> drainPendingReturns(UUID playerUuid) {
        List<ItemStack> items = new ArrayList<>();
        synchronized (connectionLock) {
            Connection conn;
            try {
                conn = getConnection();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to acquire connection for pending returns drain.", e);
                return items;
            }

            try {
                conn.setAutoCommit(false);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to begin transaction for pending returns drain.", e);
                return items;
            }

            List<Long> ids = new ArrayList<>();
            List<ItemStack> collected = new ArrayList<>();
            try {
                String select = "SELECT id, item_data FROM pending_returns WHERE player_uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(select)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getLong("id"));
                            ItemStack item = ItemSerialiser.deserialise(rs.getBytes("item_data"));
                            if (item != null) collected.add(item);
                        }
                    }
                }

                if (!ids.isEmpty()) {
                    String delete = "DELETE FROM pending_returns WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(delete)) {
                        for (Long id : ids) {
                            ps.setLong(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();
                items.addAll(collected);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to drain pending returns; rolling back.", e);
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Rollback failed during pending returns drain.", rollbackEx);
                }
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
        return items;
    }
}
