package me.horiciastko.bedwars.logic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import me.horiciastko.bedwars.BedWars;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class DatabaseManager {

    public static class SplitMigrationResult {
        private final boolean success;
        private final String message;
        private final int arenasCopied;
        private final int npcsCopied;

        public SplitMigrationResult(boolean success, String message, int arenasCopied, int npcsCopied) {
            this.success = success;
            this.message = message;
            this.arenasCopied = arenasCopied;
            this.npcsCopied = npcsCopied;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getArenasCopied() {
            return arenasCopied;
        }

        public int getNpcsCopied() {
            return npcsCopied;
        }
    }

    public static class StandaloneNPCRecord {
        private final int id;
        private final String type;
        private final String location;

        public StandaloneNPCRecord(int id, String type, String location) {
            this.id = id;
            this.type = type;
            this.location = location;
        }

        public int getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getLocation() {
            return location;
        }
    }

    public static class LeaderboardEntry {
        private final java.util.UUID uuid;
        private final String name;
        private final int value;

        public LeaderboardEntry(java.util.UUID uuid, String name, int value) {
            this.uuid = uuid;
            this.name = name;
            this.value = value;
        }

        public java.util.UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    private final BedWars plugin;
    private HikariDataSource dataSource;
    private HikariDataSource arenaDataSource;
    private HikariDataSource npcDataSource;
    @Getter
    private String type;

    public DatabaseManager(BedWars plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("database");
        if (config == null) {
            plugin.getLogger().severe("Database configuration missing in config.yml!");
            return;
        }

        this.type = config.getString("type", "sqlite").toLowerCase();

        if (type.equals("mysql")) {
            HikariConfig hikariConfig = new HikariConfig();
            String host = config.getString("mysql.host");
            int port = config.getInt("mysql.port");
            String database = config.getString("mysql.database");
            String username = config.getString("mysql.username");
            String password = config.getString("mysql.password");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            hikariConfig.setMaximumPoolSize(config.getInt("mysql.pool.maximum-pool-size", 10));

            try {
                this.dataSource = new HikariDataSource(hikariConfig);
                this.arenaDataSource = this.dataSource;
                this.npcDataSource = this.dataSource;
                createCoreTables(this.dataSource);
                createArenaTables(this.arenaDataSource);
                createNpcTables(this.npcDataSource);
                plugin.getLogger().info("Database connected successfully using mysql");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not connect to database!", e);
            }
        } else {
            try {
                ConfigurationSection sqlite = config.getConfigurationSection("sqlite");
                String coreFile = sqlite != null ? sqlite.getString("file", "database.db") : "database.db";
                ConfigurationSection split = sqlite != null ? sqlite.getConfigurationSection("split") : null;

                boolean splitArenas = split != null && split.getBoolean("arenas-enabled", false);
                boolean splitNpcs = split != null && split.getBoolean("npcs-enabled", false);
                String arenasFile = split != null ? split.getString("arenas-file", "arenas.db") : "arenas.db";
                String npcsFile = split != null ? split.getString("npcs-file", "npcs.db") : "npcs.db";

                this.dataSource = createSqliteDataSource(coreFile, 1);
                this.arenaDataSource = splitArenas ? createSqliteDataSource(arenasFile, 1) : this.dataSource;
                this.npcDataSource = splitNpcs ? createSqliteDataSource(npcsFile, 1) : this.dataSource;

                createCoreTables(this.dataSource);
                createArenaTables(this.arenaDataSource);
                createNpcTables(this.npcDataSource);

                plugin.getLogger().info("Database connected successfully using sqlite");
                if (splitArenas) {
                    plugin.getLogger().info("Arena data DB enabled: " + arenasFile);
                }
                if (splitNpcs) {
                    plugin.getLogger().info("NPC data DB enabled: " + npcsFile);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not connect to database!", e);
            }
        }
    }

    private HikariDataSource createSqliteDataSource(String fileName, int maxPoolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        File dbFile = new File(plugin.getDataFolder(), fileName);
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(hikariConfig);
    }

    private void createCoreTables(HikariDataSource source) {
        try (Connection conn = source.getConnection();
                Statement stmt = conn.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS bw_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(16) NOT NULL," +
                    "wins INT DEFAULT 0," +
                    "kills INT DEFAULT 0," +
                    "deaths INT DEFAULT 0," +
                    "final_kills INT DEFAULT 0," +
                    "beds_broken INT DEFAULT 0," +
                    "experience INT DEFAULT 0" +
                    ");";
            stmt.execute(sql);

            String quickBuySql = "CREATE TABLE IF NOT EXISTS bw_player_quickbuy (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "slot INT NOT NULL," +
                    "category VARCHAR(64)," +
                    "item_key VARCHAR(64)," +
                    "PRIMARY KEY (uuid, slot)" +
                    ");";
            stmt.execute(quickBuySql);

            String settingsSql = "CREATE TABLE IF NOT EXISTS bw_settings (" +
                    "setting_key VARCHAR(64) PRIMARY KEY," +
                    "setting_value TEXT" +
                    ");";
            stmt.execute(settingsSql);

            String statEventsSql = "CREATE TABLE IF NOT EXISTS bw_stat_events (" +
                    "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "stat_type VARCHAR(32) NOT NULL," +
                    "amount INT NOT NULL DEFAULT 1," +
                    "event_time BIGINT NOT NULL" +
                    ");";
            stmt.execute(statEventsSql);

            try {
                stmt.execute("ALTER TABLE bw_players ADD COLUMN experience INT DEFAULT 0;");
                plugin.getLogger().info("Applied database migration: ALTER TABLE bw_players ADD COLUMN experience INT DEFAULT 0;");
            } catch (SQLException ignored) {
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create core database tables!", e);
        }
    }

    private void createArenaTables(HikariDataSource source) {
        try (Connection conn = source.getConnection();
                Statement stmt = conn.createStatement()) {

            String arenasSql = "CREATE TABLE IF NOT EXISTS bw_arenas (" +
                    "name VARCHAR(64) PRIMARY KEY," +
                    "world VARCHAR(64)," +
                    "lobby TEXT," +
                    "pos1 TEXT," +
                    "pos2 TEXT," +
                    "auto_setup BOOLEAN DEFAULT 0," +
                    "min_players INT DEFAULT 2," +
                    "max_players INT DEFAULT 8," +
                    "game_mode VARCHAR(32) DEFAULT 'SOLO'," +
                    "group_name VARCHAR(64) DEFAULT 'default'," +
                    "pvp_mode VARCHAR(32) DEFAULT 'LEGACY_1_8'," +
                    "enabled BOOLEAN DEFAULT 1," +
                    "waiting_lobby_pos1 TEXT," +
                    "waiting_lobby_pos2 TEXT" +
                    ");";
            stmt.execute(arenasSql);

            String teamsSql = "CREATE TABLE IF NOT EXISTS bw_teams (" +
                    "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                    "arena_name VARCHAR(64) NOT NULL," +
                    "team_name VARCHAR(32) NOT NULL," +
                    "color VARCHAR(16)," +
                    "material VARCHAR(32)," +
                    "spawn TEXT," +
                    "bed TEXT," +
                    "shop TEXT," +
                    "upgrade TEXT," +
                    "base_pos1 TEXT," +
                    "base_pos2 TEXT" +
                    ");";
            stmt.execute(teamsSql);

            String generatorsSql = "CREATE TABLE IF NOT EXISTS bw_generators (" +
                    "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                    "arena_name VARCHAR(64) NOT NULL," +
                    "team_name VARCHAR(32)," +
                    "type VARCHAR(32) NOT NULL," +
                    "location TEXT NOT NULL" +
                    ");";
            stmt.execute(generatorsSql);

            String signsSql = "CREATE TABLE IF NOT EXISTS bw_signs (" +
                    "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                    "arena_name VARCHAR(64) NOT NULL," +
                    "location TEXT NOT NULL" +
                    ");";
            stmt.execute(signsSql);

            String[] migrations = {
                    "ALTER TABLE bw_arenas ADD COLUMN pos1 TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN pos2 TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN lobby TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN auto_setup BOOLEAN DEFAULT 0;",
                    "ALTER TABLE bw_teams ADD COLUMN color VARCHAR(16);",
                    "ALTER TABLE bw_teams ADD COLUMN material VARCHAR(32);",
                    "ALTER TABLE bw_teams ADD COLUMN shop TEXT;",
                    "ALTER TABLE bw_teams ADD COLUMN upgrade TEXT;",
                    "ALTER TABLE bw_teams ADD COLUMN base_pos1 TEXT;",
                    "ALTER TABLE bw_teams ADD COLUMN base_pos2 TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN min_players INT DEFAULT 2;",
                    "ALTER TABLE bw_arenas ADD COLUMN max_players INT DEFAULT 8;",
                    "ALTER TABLE bw_arenas ADD COLUMN game_mode VARCHAR(32) DEFAULT 'SOLO';",
                    "ALTER TABLE bw_arenas ADD COLUMN waiting_lobby_pos1 TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN waiting_lobby_pos2 TEXT;",
                    "ALTER TABLE bw_arenas ADD COLUMN group_name VARCHAR(64) DEFAULT 'default';",
                    "ALTER TABLE bw_arenas ADD COLUMN pvp_mode VARCHAR(32) DEFAULT 'LEGACY_1_8';",
                    "ALTER TABLE bw_arenas ADD COLUMN enabled BOOLEAN DEFAULT 1;"
            };

            for (String migration : migrations) {
                try {
                    stmt.execute(migration);
                    plugin.getLogger().info("Applied database migration: " + migration);
                } catch (SQLException ignored) {
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create arena database tables!", e);
        }
    }

    private void createNpcTables(HikariDataSource source) {
        try (Connection conn = source.getConnection();
                Statement stmt = conn.createStatement()) {

            String standaloneNpcsSql = "CREATE TABLE IF NOT EXISTS bw_standalone_npcs (" +
                    "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                    "npc_type VARCHAR(32) NOT NULL," +
                    "location TEXT NOT NULL" +
                    ");";
            stmt.execute(standaloneNpcsSql);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create NPC database tables!", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null)
            throw new SQLException("DataSource is null");
        return dataSource.getConnection();
    }

    public Connection getArenaConnection() throws SQLException {
        if (arenaDataSource == null)
            throw new SQLException("Arena DataSource is null");
        return arenaDataSource.getConnection();
    }

    public Connection getNpcConnection() throws SQLException {
        if (npcDataSource == null)
            throw new SQLException("NPC DataSource is null");
        return npcDataSource.getConnection();
    }

    public boolean isSplitModeEnabled() {
        if (!"sqlite".equals(type)) {
            return false;
        }
        return hasSeparateArenaDatabase() || hasSeparateNpcDatabase();
    }

    public boolean isSplitMigrationAvailable() {
        if (!isSplitModeEnabled()) {
            return false;
        }

        try (Connection sourceConn = getConnection()) {
            String sourcePath = getMainDatabasePath(sourceConn);

            boolean arenaNeedsMigration = false;
            if (hasSeparateArenaDatabase()) {
                try (Connection arenaConn = getArenaConnection()) {
                    String arenaPath = getMainDatabasePath(arenaConn);
                    if (!samePath(sourcePath, arenaPath)) {
                        arenaNeedsMigration = getRowCountSafe(sourceConn, "bw_arenas") > 0
                                && getRowCountSafe(arenaConn, "bw_arenas") == 0;
                    }
                }
            }

            boolean npcNeedsMigration = false;
            if (hasSeparateNpcDatabase()) {
                try (Connection npcConn = getNpcConnection()) {
                    String npcPath = getMainDatabasePath(npcConn);
                    if (!samePath(sourcePath, npcPath)) {
                        npcNeedsMigration = getRowCountSafe(sourceConn, "bw_standalone_npcs") > 0
                                && getRowCountSafe(npcConn, "bw_standalone_npcs") == 0;
                    }
                }
            }

            return arenaNeedsMigration || npcNeedsMigration;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not determine split migration availability", e);
            return false;
        }
    }

    public SplitMigrationResult migrateLegacyDataToSplit() {
        if (!"sqlite".equals(type)) {
            return new SplitMigrationResult(false, "Split migration is available only for sqlite mode.", 0, 0);
        }
        if (!isSplitModeEnabled()) {
            return new SplitMigrationResult(false, "Split databases are not enabled in config.", 0, 0);
        }

        int arenasCopied = 0;
        int npcsCopied = 0;

        try (Connection sourceConn = getConnection()) {
            String sourcePath = getMainDatabasePath(sourceConn);

            if (hasSeparateArenaDatabase()) {
                try (Connection arenaConn = getArenaConnection()) {
                    String arenaPath = getMainDatabasePath(arenaConn);
                    if (!samePath(sourcePath, arenaPath)) {
                        arenasCopied = migrateArenaData(sourceConn, arenaConn);
                    }
                }
            }

            if (hasSeparateNpcDatabase()) {
                try (Connection npcConn = getNpcConnection()) {
                    String npcPath = getMainDatabasePath(npcConn);
                    if (!samePath(sourcePath, npcPath)) {
                        npcsCopied = migrateNpcData(sourceConn, npcConn);
                    }
                }
            }

            String summary = "Split migration finished. Arenas copied: " + arenasCopied + ", NPCs copied: " + npcsCopied
                    + ".";
            return new SplitMigrationResult(true, summary, arenasCopied, npcsCopied);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Split migration failed", e);
            return new SplitMigrationResult(false, "Split migration failed: " + e.getMessage(), arenasCopied, npcsCopied);
        }
    }

    private int migrateArenaData(Connection sourceConn, Connection arenaConn) throws SQLException {
        if (getRowCountSafe(sourceConn, "bw_arenas") <= 0) {
            return 0;
        }

        int copiedArenas = 0;
        arenaConn.setAutoCommit(false);
        try {
            try (java.sql.Statement clear = arenaConn.createStatement()) {
                clear.executeUpdate("DELETE FROM bw_signs");
                clear.executeUpdate("DELETE FROM bw_generators");
                clear.executeUpdate("DELETE FROM bw_teams");
                clear.executeUpdate("DELETE FROM bw_arenas");
            }

            String arenaSelect = "SELECT name, world, lobby, pos1, pos2, auto_setup, min_players, max_players, game_mode, group_name, pvp_mode, enabled, waiting_lobby_pos1, waiting_lobby_pos2 FROM bw_arenas";
            String arenaInsert = "INSERT INTO bw_arenas (name, world, lobby, pos1, pos2, auto_setup, min_players, max_players, game_mode, group_name, pvp_mode, enabled, waiting_lobby_pos1, waiting_lobby_pos2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (java.sql.PreparedStatement select = sourceConn.prepareStatement(arenaSelect);
                    java.sql.ResultSet rs = select.executeQuery();
                    java.sql.PreparedStatement insert = arenaConn.prepareStatement(arenaInsert)) {
                while (rs.next()) {
                    insert.setString(1, rs.getString("name"));
                    insert.setString(2, rs.getString("world"));
                    insert.setString(3, rs.getString("lobby"));
                    insert.setString(4, rs.getString("pos1"));
                    insert.setString(5, rs.getString("pos2"));
                    insert.setBoolean(6, rs.getBoolean("auto_setup"));
                    insert.setInt(7, rs.getInt("min_players"));
                    insert.setInt(8, rs.getInt("max_players"));
                    insert.setString(9, rs.getString("game_mode"));
                    insert.setString(10, rs.getString("group_name"));
                    insert.setString(11, rs.getString("pvp_mode"));
                    insert.setBoolean(12, rs.getBoolean("enabled"));
                    insert.setString(13, rs.getString("waiting_lobby_pos1"));
                    insert.setString(14, rs.getString("waiting_lobby_pos2"));
                    insert.addBatch();
                    copiedArenas++;
                }
                insert.executeBatch();
            }

            copyArenaRelatedTable(sourceConn, arenaConn, "bw_signs",
                    "INSERT INTO bw_signs (arena_name, location) VALUES (?, ?)",
                    new String[] { "arena_name", "location" });

            copyArenaRelatedTable(sourceConn, arenaConn, "bw_teams",
                    "INSERT INTO bw_teams (arena_name, team_name, color, material, spawn, bed, shop, upgrade, base_pos1, base_pos2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] { "arena_name", "team_name", "color", "material", "spawn", "bed", "shop", "upgrade",
                            "base_pos1", "base_pos2" });

            copyArenaRelatedTable(sourceConn, arenaConn, "bw_generators",
                    "INSERT INTO bw_generators (arena_name, team_name, type, location) VALUES (?, ?, ?, ?)",
                    new String[] { "arena_name", "team_name", "type", "location" });

            arenaConn.commit();
            return copiedArenas;
        } catch (SQLException e) {
            arenaConn.rollback();
            throw e;
        } finally {
            arenaConn.setAutoCommit(true);
        }
    }

    private void copyArenaRelatedTable(Connection sourceConn, Connection targetConn, String tableName, String insertSql,
            String[] columns) throws SQLException {
        if (getRowCountSafe(sourceConn, tableName) <= 0) {
            return;
        }

        String selectSql = "SELECT " + String.join(", ", columns) + " FROM " + tableName;
        try (java.sql.PreparedStatement select = sourceConn.prepareStatement(selectSql);
                java.sql.ResultSet rs = select.executeQuery();
                java.sql.PreparedStatement insert = targetConn.prepareStatement(insertSql)) {
            while (rs.next()) {
                for (int i = 0; i < columns.length; i++) {
                    insert.setObject(i + 1, rs.getObject(columns[i]));
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private int migrateNpcData(Connection sourceConn, Connection npcConn) throws SQLException {
        if (getRowCountSafe(sourceConn, "bw_standalone_npcs") <= 0) {
            return 0;
        }

        int copiedNpcs = 0;
        npcConn.setAutoCommit(false);
        try {
            try (java.sql.Statement clear = npcConn.createStatement()) {
                clear.executeUpdate("DELETE FROM bw_standalone_npcs");
            }

            String selectSql = "SELECT npc_type, location FROM bw_standalone_npcs";
            String insertSql = "INSERT INTO bw_standalone_npcs (npc_type, location) VALUES (?, ?)";
            try (java.sql.PreparedStatement select = sourceConn.prepareStatement(selectSql);
                    java.sql.ResultSet rs = select.executeQuery();
                    java.sql.PreparedStatement insert = npcConn.prepareStatement(insertSql)) {
                while (rs.next()) {
                    insert.setString(1, rs.getString("npc_type"));
                    insert.setString(2, rs.getString("location"));
                    insert.addBatch();
                    copiedNpcs++;
                }
                insert.executeBatch();
            }

            npcConn.commit();
            return copiedNpcs;
        } catch (SQLException e) {
            npcConn.rollback();
            throw e;
        } finally {
            npcConn.setAutoCommit(true);
        }
    }

    private int getRowCountSafe(Connection conn, String tableName) {
        String sql = "SELECT COUNT(*) AS cnt FROM " + tableName;
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private String getMainDatabasePath(Connection conn) {
        try (java.sql.PreparedStatement ps = conn.prepareStatement("PRAGMA database_list");
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if ("main".equalsIgnoreCase(rs.getString("name"))) {
                    return rs.getString("file");
                }
            }
        } catch (SQLException ignored) {
        }
        return "";
    }

    private boolean samePath(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private boolean hasSeparateArenaDatabase() {
        return arenaDataSource != null && arenaDataSource != dataSource;
    }

    private boolean hasSeparateNpcDatabase() {
        return npcDataSource != null && npcDataSource != dataSource;
    }

    public void setSetting(String key, String value) {
        String sql = type.equals("sqlite")
                ? "INSERT OR REPLACE INTO bw_settings (setting_key, setting_value) VALUES (?, ?)"
                : "INSERT INTO bw_settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save setting " + key, e);
        }
    }

    public String getSetting(String key) {
        String sql = "SELECT setting_value FROM bw_settings WHERE setting_key = ?";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load setting " + key, e);
        }
        return null;
    }

    public void close() {
        Set<HikariDataSource> sources = new HashSet<>();
        if (dataSource != null)
            sources.add(dataSource);
        if (arenaDataSource != null)
            sources.add(arenaDataSource);
        if (npcDataSource != null)
            sources.add(npcDataSource);

        for (HikariDataSource source : sources) {
            if (!source.isClosed()) {
                source.close();
            }
        }
    }

    public void savePlayerQuickBuy(java.util.UUID uuid, int slot, String category, String itemKey) {
        String sql = type.equals("sqlite")
                ? "INSERT OR REPLACE INTO bw_player_quickbuy (uuid, slot, category, item_key) VALUES (?, ?, ?, ?)"
                : "INSERT INTO bw_player_quickbuy (uuid, slot, category, item_key) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE category = VALUES(category), item_key = VALUES(item_key)";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, category);
            ps.setString(4, itemKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save quickbuy for " + uuid, e);
        }
    }

    public void removePlayerQuickBuy(java.util.UUID uuid, int slot) {
        String sql = "DELETE FROM bw_player_quickbuy WHERE uuid = ? AND slot = ?";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not remove quickbuy for " + uuid, e);
        }
    }

    public java.util.Map<Integer, String[]> getPlayerQuickBuy(java.util.UUID uuid) {
        java.util.Map<Integer, String[]> result = new java.util.HashMap<>();
        String sql = "SELECT slot, category, item_key FROM bw_player_quickbuy WHERE uuid = ?";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("slot"), new String[] { rs.getString("category"), rs.getString("item_key") });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load quickbuy for " + uuid, e);
        }
        return result;
    }

    public me.horiciastko.bedwars.models.PlayerStats getPlayerStats(java.util.UUID uuid) {
        String sql = "SELECT * FROM bw_players WHERE uuid = ?";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new me.horiciastko.bedwars.models.PlayerStats(
                            uuid,
                            rs.getInt("wins"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("final_kills"),
                            rs.getInt("beds_broken"),
                            rs.getInt("experience"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load stats for " + uuid, e);
        }
        return new me.horiciastko.bedwars.models.PlayerStats(uuid);
    }

    public void updatePlayerStats(java.util.UUID uuid, String name, me.horiciastko.bedwars.models.PlayerStats stats) {
        String sql = type.equals("sqlite")
                ? "INSERT OR REPLACE INTO bw_players (uuid, name, wins, kills, deaths, final_kills, beds_broken, experience) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO bw_players (uuid, name, wins, kills, deaths, final_kills, beds_broken, experience) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        +
                        "ON DUPLICATE KEY UPDATE name=VALUES(name), wins=VALUES(wins), kills=VALUES(kills), deaths=VALUES(deaths), final_kills=VALUES(final_kills), beds_broken=VALUES(beds_broken), experience=VALUES(experience)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, stats.getWins());
            ps.setInt(4, stats.getKills());
            ps.setInt(5, stats.getDeaths());
            ps.setInt(6, stats.getFinalKills());
            ps.setInt(7, stats.getBedsBroken());
            ps.setInt(8, stats.getExperience());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save stats for " + uuid, e);
        }
    }

    public void recordStatEvent(java.util.UUID playerUuid, String statType, int amount) {
        String sql = "INSERT INTO bw_stat_events (player_uuid, stat_type, amount, event_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, statType);
            ps.setInt(3, amount);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not record stat event for " + playerUuid + " type=" + statType, e);
        }
    }

    public LeaderboardEntry getTopStatEntry(String statType, long sinceEpochMillis, int rank) {
        int safeRank = Math.max(1, rank);
        int offset = safeRank - 1;

        String sql = "SELECT e.player_uuid, COALESCE(p.name, e.player_uuid) AS player_name, SUM(e.amount) AS total " +
                "FROM bw_stat_events e " +
                "LEFT JOIN bw_players p ON p.uuid = e.player_uuid " +
                "WHERE e.stat_type = ? AND (? <= 0 OR e.event_time >= ?) " +
                "GROUP BY e.player_uuid " +
                "ORDER BY total DESC " +
                "LIMIT 1 OFFSET ?";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statType);
            ps.setLong(2, sinceEpochMillis);
            ps.setLong(3, sinceEpochMillis);
            ps.setInt(4, offset);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                java.util.UUID uuid;
                try {
                    uuid = java.util.UUID.fromString(rs.getString("player_uuid"));
                } catch (IllegalArgumentException ex) {
                    uuid = null;
                }

                String name = rs.getString("player_name");
                int value = rs.getInt("total");
                return new LeaderboardEntry(uuid, name, value);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not fetch leaderboard stat type=" + statType, e);
        }

        return null;
    }

    public LeaderboardEntry getTopLifetimeStatEntry(String statType, int rank) {
        String column;
        switch (statType) {
            case "wins":
                column = "wins";
                break;
            case "final_kills":
                column = "final_kills";
                break;
            case "beds_broken":
                column = "beds_broken";
                break;
            default:
                return null;
        }

        int safeRank = Math.max(1, rank);
        int offset = safeRank - 1;
        String sql = "SELECT uuid, name, " + column + " AS total FROM bw_players ORDER BY " + column + " DESC LIMIT 1 OFFSET ?";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, offset);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                java.util.UUID uuid;
                try {
                    uuid = java.util.UUID.fromString(rs.getString("uuid"));
                } catch (IllegalArgumentException ex) {
                    uuid = null;
                }

                return new LeaderboardEntry(uuid, rs.getString("name"), rs.getInt("total"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not fetch lifetime leaderboard for " + statType, e);
        }

        return null;
    }

    public int saveStandaloneNPC(String type, String location) {
        String sql = "INSERT INTO bw_standalone_npcs (npc_type, location) VALUES (?, ?)";
        try (Connection conn = getNpcConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, location);
            int changed = ps.executeUpdate();
            if (changed > 0) {
                try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save standalone NPC", e);
        }
        return -1;
    }

    public java.util.List<StandaloneNPCRecord> loadStandaloneNPCs() {
        java.util.List<StandaloneNPCRecord> npcs = new java.util.ArrayList<>();
        String sql = "SELECT id, npc_type, location FROM bw_standalone_npcs";

        try (Connection conn = getNpcConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                npcs.add(new StandaloneNPCRecord(
                        rs.getInt("id"),
                        rs.getString("npc_type"),
                        rs.getString("location")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load standalone NPCs", e);
        }

        return npcs;
    }

    public void deleteStandaloneNPC(int id) {
        String sql = "DELETE FROM bw_standalone_npcs WHERE id = ?";

        try (Connection conn = getNpcConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete standalone NPC id=" + id, e);
        }
    }
}
