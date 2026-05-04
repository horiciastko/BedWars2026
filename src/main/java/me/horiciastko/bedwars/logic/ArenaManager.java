package me.horiciastko.bedwars.logic;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import me.horiciastko.bedwars.utils.SerializationUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class ArenaManager {

    private final BedWars plugin;
    private final List<Arena> arenas = new ObjectArrayList<>();
    private final Map<Player, Arena> playerArenaCache = new ConcurrentHashMap<>();
    private final Map<Player, Arena> editSessions = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, String> lastArena = new ConcurrentHashMap<>();
    private final Map<Player, Team> editTeamSessions = new ConcurrentHashMap<>();
    private final Map<Player, Location> preEditLocations = new ConcurrentHashMap<>();

    public ArenaManager(BedWars plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    public void setLastArena(java.util.UUID uuid, String arenaName) {
        lastArena.put(uuid, arenaName);
    }

    public String getLastArena(java.util.UUID uuid) {
        return lastArena.get(uuid);
    }

    public void loadArenas() {
        arenas.clear();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM bw_arenas")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String name = rs.getString("name");
                    Arena arena = new Arena(name);
                    arena.getTeams().clear();
                    String worldName = rs.getString("world");
                    arena.setWorldName(worldName);

                    if (worldName != null && org.bukkit.Bukkit.getWorld(worldName) == null) {
                        if (new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName).exists()
                                || me.horiciastko.bedwars.utils.WorldBackupUtils.hasBackup(worldName)) {
                            // Restore clean backup before loading so mid-game state from a crashed
                            // server does not persist
                            boolean hadBackup = me.horiciastko.bedwars.utils.WorldBackupUtils.hasBackup(worldName);
                            if (hadBackup) {
                                plugin.getLogger().info("Restoring world backup for arena: " + name);
                                me.horiciastko.bedwars.utils.WorldBackupUtils.restoreBackup(worldName);
                            }
                            org.bukkit.World creator = org.bukkit.Bukkit
                                    .createWorld(new org.bukkit.WorldCreator(worldName));
                            if (creator != null) {
                                creator.setAutoSave(false);
                                creator.getEntitiesByClass(org.bukkit.entity.Item.class)
                                        .forEach(org.bukkit.entity.Entity::remove);
                                if (!hadBackup) {
                                    // No backup existed — create one now so future resets work correctly
                                    final String worldNameForBackup = worldName;
                                    final String arenaNameForLog = name;
                                    org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                        boolean ok = me.horiciastko.bedwars.utils.WorldBackupUtils.createBackup(worldNameForBackup);
                                        if (ok) {
                                            plugin.getLogger().info("Created initial world backup for arena: " + arenaNameForLog);
                                        }
                                    });
                                }
                            }
                        }
                    } else if (worldName != null && org.bukkit.Bukkit.getWorld(worldName) != null) {
                        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                        world.setAutoSave(false);
                        world.getEntitiesByClass(org.bukkit.entity.Item.class)
                                .forEach(org.bukkit.entity.Entity::remove);
                    }

                    arena.setLobbyLocation(
                            SerializationUtils.stringToLocationWithWorldName(rs.getString("lobby"), worldName));
                    arena.setPos1(SerializationUtils.stringToLocationWithWorldName(rs.getString("pos1"), worldName));
                    arena.setPos2(SerializationUtils.stringToLocationWithWorldName(rs.getString("pos2"), worldName));
                    arena.setLobbyPos1(SerializationUtils
                            .stringToLocationWithWorldName(rs.getString("waiting_lobby_pos1"), worldName));
                    arena.setLobbyPos2(SerializationUtils
                            .stringToLocationWithWorldName(rs.getString("waiting_lobby_pos2"), worldName));
                    arena.setAutoSetup(rs.getBoolean("auto_setup"));
                    arena.setMinPlayers(rs.getInt("min_players"));
                    arena.setMaxPlayers(rs.getInt("max_players"));
                    String gameMode = rs.getString("game_mode");
                    if (gameMode != null) {
                        try {
                            arena.setMode(Arena.ArenaMode.valueOf(gameMode));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    arena.setGroup(rs.getString("group_name"));
                    arena.setEnabled(rs.getBoolean("enabled"));
                    String pvpMode = rs.getString("pvp_mode");
                    if (pvpMode != null) {
                        try {
                            arena.setPvpMode(Arena.PvpMode.valueOf(pvpMode));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    try (PreparedStatement psSigns = conn
                            .prepareStatement("SELECT * FROM bw_signs WHERE arena_name = ?")) {
                        psSigns.setString(1, name);
                        ResultSet rsSigns = psSigns.executeQuery();
                        while (rsSigns.next()) {
                            Location loc = SerializationUtils.stringToLocation(rsSigns.getString("location"));
                            if (loc != null)
                                arena.getJoinSigns().add(loc);
                        }
                    }

                    try (PreparedStatement psTeams = conn
                            .prepareStatement("SELECT * FROM bw_teams WHERE arena_name = ?")) {
                        psTeams.setString(1, name);
                        ResultSet rsTeams = psTeams.executeQuery();
                        java.util.Map<String, Team> distinctTeams = new java.util.LinkedHashMap<>();

                        while (rsTeams.next()) {
                            String teamName = rsTeams.getString("team_name");
                            String colorStr = rsTeams.getString("color");
                            String matStr = rsTeams.getString("material");

                            if (teamName == null || colorStr == null || matStr == null)
                                continue;

                            String displayName = teamName;
                            org.bukkit.configuration.file.FileConfiguration tConfig = plugin.getConfigManager()
                                    .getTeamConfig();
                            if (tConfig != null && tConfig.contains("teams." + teamName + ".display_name")) {
                                displayName = tConfig.getString("teams." + teamName + ".display_name");
                            }

                            Team team = new Team(
                                    teamName,
                                    displayName,
                                    org.bukkit.ChatColor.valueOf(colorStr),
                                    com.cryptomorin.xseries.XMaterial.matchXMaterial(matStr)
                                            .orElse(com.cryptomorin.xseries.XMaterial.WHITE_WOOL).parseMaterial());
                            team.setSpawnLocation(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("spawn"), worldName));
                            team.setBedLocation(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("bed"), worldName));
                            team.setShopLocation(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("shop"), worldName));
                            team.setUpgradeLocation(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("upgrade"), worldName));
                            team.setBasePos1(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("base_pos1"), worldName));
                            team.setBasePos2(SerializationUtils
                                    .stringToLocationWithWorldName(rsTeams.getString("base_pos2"), worldName));

                            // Debug log for base positions
                            if (team.getBasePos1() != null || team.getBasePos2() != null) {
                                plugin.getLogger().info("Loaded base positions for team " + teamName + 
                                    " in arena " + name + 
                                    " - Pos1: " + (team.getBasePos1() != null ? team.getBasePos1().toString() : "null") +
                                    ", Pos2: " + (team.getBasePos2() != null ? team.getBasePos2().toString() : "null"));
                            }

                            distinctTeams.put(teamName.toLowerCase(), team);
                        }

                        arena.getTeams().addAll(distinctTeams.values());

                        plugin.getNpcManager().cleanupArenaNpcResidue(arena);

                        for (Team team : arena.getTeams()) {
                            try (PreparedStatement psGens = conn.prepareStatement(
                                    "SELECT * FROM bw_generators WHERE arena_name = ? AND team_name = ? AND type = 'TEAM'")) {
                                psGens.setString(1, name);
                                psGens.setString(2, team.getName());
                                ResultSet rsGens = psGens.executeQuery();
                                while (rsGens.next()) {
                                    Location genLoc = SerializationUtils
                                            .stringToLocationWithWorldName(rsGens.getString("location"), worldName);
                                    if (genLoc != null) {
                                        team.getGenerators().add(genLoc);
                                    }
                                }
                            }
                        }
                    }

                    try (PreparedStatement psGlobal = conn.prepareStatement(
                            "SELECT * FROM bw_generators WHERE arena_name = ? AND team_name IS NULL")) {
                        psGlobal.setString(1, name);
                        ResultSet rsGlobal = psGlobal.executeQuery();
                        while (rsGlobal.next()) {
                            String type = rsGlobal.getString("type");
                            Location loc = SerializationUtils
                                    .stringToLocationWithWorldName(rsGlobal.getString("location"), worldName);
                            if (loc == null)
                                continue;

                            if (type.equals("DIAMOND"))
                                arena.getDiamondGenerators().add(loc);
                            else if (type.equals("EMERALD"))
                                arena.getEmeraldGenerators().add(loc);
                        }
                    }

                    arenas.add(arena);
                }
            }
            plugin.getLogger().info("Successfully loaded " + arenas.size() + " arenas from database.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load arenas from database!");
            e.printStackTrace();
        }
    }

    public void unloadArenaWorlds() {
        for (Arena arena : arenas) {
            if (arena.getWorldName() != null) {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
                if (world != null) {
                    world.setAutoSave(false);
                    org.bukkit.Bukkit.unloadWorld(world, false);
                }
            }
        }
    }

    public void saveArenas() {
        for (Arena arena : arenas) {
            saveArena(arena);
        }
    }

    public void saveArena(Arena arena) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            try {
                String arenaUpsert = plugin.getDatabaseManager().getType().equals("sqlite")
                        ? "INSERT OR REPLACE INTO bw_arenas (name, world, lobby, pos1, pos2, auto_setup, min_players, max_players, game_mode, group_name, pvp_mode, enabled, waiting_lobby_pos1, waiting_lobby_pos2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        : "INSERT INTO bw_arenas (name, world, lobby, pos1, pos2, auto_setup, min_players, max_players, game_mode, group_name, pvp_mode, enabled, waiting_lobby_pos1, waiting_lobby_pos2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                +
                                "ON DUPLICATE KEY UPDATE world=VALUES(world), lobby=VALUES(lobby), pos1=VALUES(pos1), pos2=VALUES(pos2), auto_setup=VALUES(auto_setup), min_players=VALUES(min_players), max_players=VALUES(max_players), game_mode=VALUES(game_mode), group_name=VALUES(group_name), pvp_mode=VALUES(pvp_mode), enabled=VALUES(enabled), waiting_lobby_pos1=VALUES(waiting_lobby_pos1), waiting_lobby_pos2=VALUES(waiting_lobby_pos2)";

                try (PreparedStatement psArena = conn.prepareStatement(arenaUpsert)) {
                    psArena.setString(1, arena.getName());
                    psArena.setString(2, arena.getWorldName());
                    psArena.setString(3, SerializationUtils.locationToString(arena.getLobbyLocation()));
                    psArena.setString(4, SerializationUtils.locationToString(arena.getPos1()));
                    psArena.setString(5, SerializationUtils.locationToString(arena.getPos2()));
                    psArena.setBoolean(6, arena.isAutoSetup());
                    psArena.setInt(7, arena.getMinPlayers());
                    psArena.setInt(8, arena.getMaxPlayers());
                    psArena.setString(9, arena.getMode().name());
                    psArena.setString(10, arena.getGroup());
                    psArena.setString(11, arena.getPvpMode().name());
                    psArena.setBoolean(12, arena.isEnabled());
                    psArena.setString(13, SerializationUtils.locationToString(arena.getLobbyPos1()));
                    psArena.setString(14, SerializationUtils.locationToString(arena.getLobbyPos2()));
                    psArena.executeUpdate();
                }

                try (PreparedStatement psClearSigns = conn
                        .prepareStatement("DELETE FROM bw_signs WHERE arena_name = ?")) {
                    psClearSigns.setString(1, arena.getName());
                    psClearSigns.executeUpdate();
                }

                try (PreparedStatement psSign = conn
                        .prepareStatement("INSERT INTO bw_signs (arena_name, location) VALUES (?, ?)")) {
                    for (Location loc : arena.getJoinSigns()) {
                        psSign.setString(1, arena.getName());
                        psSign.setString(2, SerializationUtils.locationToString(loc));
                        psSign.addBatch();
                    }
                    psSign.executeBatch();
                }

                try (PreparedStatement psClearTeams = conn
                        .prepareStatement("DELETE FROM bw_teams WHERE arena_name = ?")) {
                    psClearTeams.setString(1, arena.getName());
                    psClearTeams.executeUpdate();
                }
                try (PreparedStatement psClearGens = conn
                        .prepareStatement("DELETE FROM bw_generators WHERE arena_name = ?")) {
                    psClearGens.setString(1, arena.getName());
                    psClearGens.executeUpdate();
                }

                String teamInsert = "INSERT INTO bw_teams (arena_name, team_name, color, material, spawn, bed, shop, upgrade, base_pos1, base_pos2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psTeam = conn.prepareStatement(teamInsert)) {
                    for (Team team : arena.getTeams()) {
                        psTeam.setString(1, arena.getName());
                        psTeam.setString(2, team.getName());
                        psTeam.setString(3, team.getColor() != null ? team.getColor().name() : "WHITE");
                        psTeam.setString(4, team.getMaterial() != null ? team.getMaterial().name() : "WHITE_WOOL");
                        psTeam.setString(5, SerializationUtils.locationToString(team.getSpawnLocation()));
                        psTeam.setString(6, SerializationUtils.locationToString(team.getBedLocation()));
                        psTeam.setString(7, SerializationUtils.locationToString(team.getShopLocation()));
                        psTeam.setString(8, SerializationUtils.locationToString(team.getUpgradeLocation()));
                        psTeam.setString(9, SerializationUtils.locationToString(team.getBasePos1()));
                        psTeam.setString(10, SerializationUtils.locationToString(team.getBasePos2()));
                        
                        // Debug log when saving base positions
                        if (team.getBasePos1() != null || team.getBasePos2() != null) {
                            plugin.getLogger().info("Saving base positions for team " + team.getName() + 
                                " in arena " + arena.getName() + 
                                " - Pos1: " + (team.getBasePos1() != null ? team.getBasePos1().toString() : "null") +
                                ", Pos2: " + (team.getBasePos2() != null ? team.getBasePos2().toString() : "null"));
                        }
                        
                        psTeam.addBatch();
                    }
                    psTeam.executeBatch();
                }

                String generatorInsert = "INSERT INTO bw_generators (arena_name, team_name, type, location) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psGen = conn.prepareStatement(generatorInsert)) {
                    for (Team team : arena.getTeams()) {
                        for (Location loc : team.getGenerators()) {
                            psGen.setString(1, arena.getName());
                            psGen.setString(2, team.getName());
                            psGen.setString(3, "TEAM");
                            psGen.setString(4, SerializationUtils.locationToString(loc));
                            psGen.addBatch();
                        }
                    }
                    for (Location loc : arena.getDiamondGenerators()) {
                        psGen.setString(1, arena.getName());
                        psGen.setNull(2, java.sql.Types.VARCHAR);
                        psGen.setString(3, "DIAMOND");
                        psGen.setString(4, SerializationUtils.locationToString(loc));
                        psGen.addBatch();
                    }
                    for (Location loc : arena.getEmeraldGenerators()) {
                        psGen.setString(1, arena.getName());
                        psGen.setNull(2, java.sql.Types.VARCHAR);
                        psGen.setString(3, "EMERALD");
                        psGen.setString(4, SerializationUtils.locationToString(loc));
                        psGen.addBatch();
                    }
                    psGen.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            plugin.getVisualizationManager().refreshHologramsForArena(arena);

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save arena " + arena.getName() + " to database!");
            e.printStackTrace();
        }
    }

    public void deleteArena(Arena arena) {
        arenas.remove(arena);
        for (Player p : editSessions.keySet()) {
            if (editSessions.get(p).getName().equals(arena.getName())) {
                plugin.getVisualizationManager().hideHolograms(p);
                editSessions.remove(p);
            }
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement psSigns = conn.prepareStatement("DELETE FROM bw_signs WHERE arena_name = ?")) {
                psSigns.setString(1, arena.getName());
                psSigns.executeUpdate();
            }
            try (PreparedStatement psTeams = conn.prepareStatement("DELETE FROM bw_teams WHERE arena_name = ?")) {
                psTeams.setString(1, arena.getName());
                psTeams.executeUpdate();
            }
            try (PreparedStatement psGens = conn.prepareStatement("DELETE FROM bw_generators WHERE arena_name = ?")) {
                psGens.setString(1, arena.getName());
                psGens.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bw_arenas WHERE name = ?")) {
                ps.setString(1, arena.getName());
                ps.executeUpdate();
            }
            plugin.getLogger().info("Deleted arena " + arena.getName() + " and all related data from database.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not delete arena " + arena.getName() + " from database!");
            e.printStackTrace();
        }
    }

    public List<Arena> getArenas() {
        return arenas;
    }

    public void addArena(Arena arena) {
        arenas.add(arena);
        saveArenas();
    }

    public Arena getArena(String name) {
        for (Arena arena : arenas) {
            if (arena.getName().equalsIgnoreCase(name))
                return arena;
        }
        return null;
    }

    public Arena getPlayerArena(Player player) {
        return playerArenaCache.get(player);
    }

    public void setPlayerArena(Player player, Arena arena) {
        if (arena == null) {
            playerArenaCache.remove(player);
        } else {
            playerArenaCache.put(player, arena);
        }
    }

    public Arena getEditArena(Player player) {
        return editSessions.get(player);
    }

    public void setEditArena(Player player, Arena arena) {
        if (arena == null) {
            editSessions.remove(player);
            preEditLocations.remove(player);
        } else {
            if (arena.isResetting()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-resetting"));
                return;
            }

            Arena currentArena = editSessions.get(player);
            boolean isNewEdit = currentArena == null || !currentArena.getName().equals(arena.getName());

            if (!editSessions.containsKey(player)) {
                preEditLocations.put(player, player.getLocation());
            }
            editSessions.put(player, arena);

            if (arena.getWorldName() != null) {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
                if (world == null) {
                    if (new java.io.File(org.bukkit.Bukkit.getWorldContainer(), arena.getWorldName()).exists()) {
                        world = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(arena.getWorldName()));
                    }
                }

                if (world != null) {
                    world.setAutoSave(true);
                    plugin.getNpcManager().cleanupArenaNpcResidue(arena);

                    if (isNewEdit || !player.getWorld().getName().equals(world.getName())) {
                        Location target = arena.getLobbyLocation();
                        if (target == null)
                            target = arena.getPos1();
                        if (target != null && (target.getWorld() == null
                                || !target.getWorld().getName().equals(world.getName()))) {
                            target = new Location(world, target.getX(), target.getY(), target.getZ(), target.getYaw(),
                                    target.getPitch());
                        }

                        if (target == null)
                            target = world.getSpawnLocation();

                        player.teleport(target);
                        player.setGameMode(org.bukkit.GameMode.CREATIVE);
                        player.setFlying(true);
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-teleported").replace("%world%", arena.getWorldName()));
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-setup-mode"));
                    }
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-world-load-failed").replace("%world%", arena.getWorldName()));
                }
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-no-world"));
            }
        }
    }

    public void stopEditing(Player player) {
        Arena arena = editSessions.remove(player);
        editTeamSessions.remove(player);
        plugin.getVisualizationManager().hideHolograms(player);
        plugin.getScoreboardManager().updateScoreboard(player);

        Location loc = preEditLocations.remove(player);
        if (loc != null) {
            player.teleport(loc);
        }

        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFlying(false);
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-exited-setup"));

        if (arena != null && arena.getWorldName() != null) {
            plugin.getNpcManager().cleanupArenaNpcResidue(arena);
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
            if (world != null) {
                world.save();
                world.setAutoSave(false);
                // Create/update backup asynchronously so the clean post-edit state is preserved
                final String worldNameToBackup = arena.getWorldName();
                final String arenaNameForLog = arena.getName();
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean ok = me.horiciastko.bedwars.utils.WorldBackupUtils.createBackup(worldNameToBackup);
                    if (ok) {
                        plugin.getLogger().info("World backup created for arena: " + arenaNameForLog);
                    } else {
                        plugin.getLogger().warning("Failed to create world backup for arena: " + arenaNameForLog);
                    }
                });
            }
        }
    }

    public Team getEditTeam(Player player) {
        return editTeamSessions.get(player);
    }

    public void setEditTeam(Player player, Team team) {
        if (team == null) {
            editTeamSessions.remove(player);
        } else {
            editTeamSessions.put(player, team);
        }
    }

    public void leaveArena(Player player) {
        Arena arena = playerArenaCache.remove(player);
        if (arena != null) {
            setLastArena(player.getUniqueId(), arena.getName());
            arena.getPlayers().remove(player);
            plugin.getGameManager().checkLobbyLogistics(arena);

            for (Team team : arena.getTeams()) {
                team.getMembers().remove(player);
            }

            plugin.getSignManager().updateSigns(arena);
            plugin.getGameManager().applyPvpSettings(player, null);

            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.getInventory().clear();
            player.getEnderChest().clear();
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            String lobbyStr = plugin.getDatabaseManager().getSetting("main_lobby");
            if (lobbyStr != null && !lobbyStr.isEmpty()) {
                Location lobbyLoc = SerializationUtils.stringToLocation(lobbyStr);
                if (lobbyLoc != null)
                    player.teleport(lobbyLoc);
            }

            if (arena.getState() == Arena.GameState.IN_GAME) {
                plugin.getGameManager().checkForWin(arena);
            }
        }
    }

    public void joinArena(Player player, Arena arena) {
        if (arena.isResetting()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-resetting"));
            return;
        }

        if (!arena.isEnabled() && !player.hasPermission("bedwars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-disabled"));
            return;
        }

        Location lobbyLoc = arena.getLobbyLocation();
        if (lobbyLoc == null || lobbyLoc.getWorld() == null) {
            if (lobbyLoc != null && arena.getWorldName() != null) {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
                if (world != null) {
                    Location fixedLobby = new Location(world, lobbyLoc.getX(), lobbyLoc.getY(), lobbyLoc.getZ(),
                            lobbyLoc.getYaw(), lobbyLoc.getPitch());
                    arena.setLobbyLocation(fixedLobby);
                    lobbyLoc = fixedLobby;
                    plugin.getLogger().info("Fixed stale lobby location for arena " + arena.getName());
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-world-not-loaded"));
                    return;
                }
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-not-setup"));
                return;
            }
        }

        if (arena.getState() == Arena.GameState.IN_GAME || arena.getState() == Arena.GameState.ENDING) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-joining-spectator"));
            player.teleport(lobbyLoc);
            setPlayerArena(player, arena);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return;
        }

        if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-full"));
            return;
        }

        player.teleport(lobbyLoc);
        if (!arena.getPlayers().contains(player)) {
            arena.getPlayers().add(player);
            setPlayerArena(player, arena);
            plugin.getSignManager().updateSigns(arena);

            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            plugin.getGameManager().applyPvpSettings(player, arena);

            player.getInventory().clear();
            org.bukkit.inventory.ItemStack leaveItem = com.cryptomorin.xseries.XMaterial.RED_BED.parseItem();
            if (leaveItem == null)
                leaveItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta meta = leaveItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getLanguageManager().getMessage(player.getUniqueId(), "lobby-leave-item"));
                leaveItem.setItemMeta(meta);
                me.horiciastko.bedwars.utils.ItemTagUtils.setTag(leaveItem, "special_item", "lobby_leave");
            }
            player.getInventory().setItem(8, leaveItem);

            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-joined").replace("%arena%", arena.getName()));
            plugin.getGameManager().checkLobbyLogistics(arena);
        }
    }

    public Team findClosestTeam(Arena arena, Location loc) {
        if (arena == null || loc == null)
            return null;
        Team closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Team team : arena.getTeams()) {
            if (team.getSpawnLocation() == null)
                continue;
            if (!team.getSpawnLocation().getWorld().equals(loc.getWorld()))
                continue;

            double dist = team.getSpawnLocation().distance(loc);
            if (dist < minDistance) {
                minDistance = dist;
                closest = team;
            }
        }
        return closest;
    }
}
