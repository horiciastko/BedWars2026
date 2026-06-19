package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class GameManager {

    private final BedWars plugin;
    private final Map<Arena, StartingTask> startTasks = new ConcurrentHashMap<>();
    private final Map<Arena, Map<UUID, Team>> playerTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trapImmunity = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastDamager = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamagerTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPickaxeTiers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerAxeTiers = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArmorTiers = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> playerHasShears = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final java.util.Set<UUID> buildMode = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Arena, Map<UUID, SessionStats>> sessionStats = new ConcurrentHashMap<>();

    public static class SessionStats {
        public int kills = 0;
        public int finalKills = 0;
        public int bedsBroken = 0;
    }

    public boolean isInBuildMode(Player player) {
        return buildMode.contains(player.getUniqueId());
    }

    public void setBuildMode(Player player, boolean enabled) {
        if (enabled)
            buildMode.add(player.getUniqueId());
        else
            buildMode.remove(player.getUniqueId());
        plugin.getDatabaseManager().setSetting("build_mode_" + player.getUniqueId(), enabled ? "1" : "0");
    }

    public void setTrapImmunity(UUID uuid, int seconds) {
        trapImmunity.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasTrapImmunity(UUID uuid) {
        return trapImmunity.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    public GameManager(BedWars plugin) {
        this.plugin = plugin;
    }

    public void forceStart(Arena arena) {
        if (arena.getState() != Arena.GameState.WAITING && arena.getState() != Arena.GameState.STARTING) {
            return;
        }

        StartingTask existingTask = startTasks.remove(arena);
        if (existingTask != null) {
            existingTask.cancel();
        }

        if (arena.getPlayers().size() <= 1) {
            arena.setSoloTest(true);
        }

        startCountdown(arena, 5);
    }

    public void checkLobbyLogistics(Arena arena) {
        if (arena.getState() != Arena.GameState.WAITING && arena.getState() != Arena.GameState.STARTING) {
            return;
        }

        int playerCount = arena.getPlayers().size();
        int minPlayers = arena.getMinPlayers();
        int maxPlayers = arena.getMaxPlayers();

        if (playerCount < minPlayers) {
            if (arena.getState() == Arena.GameState.STARTING) {
                if (startTasks.containsKey(arena)) {
                    startTasks.get(arena).cancel();
                    startTasks.remove(arena);
                }
                arena.setState(Arena.GameState.WAITING);
                arena.getPlayers().forEach(p -> {
                    p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-starting-cancelled"));
                });
            }
            return;
        }

        StartingTask task = startTasks.get(arena);
        if (task == null) {
            startCountdown(arena, 60);
            task = startTasks.get(arena);
        }

        if (playerCount >= maxPlayers) {
            task.setTimer(10);
        } else if (playerCount >= maxPlayers / 2) {
            task.setTimer(30);
        }
    }

    public void setLastDamager(Player victim, Player damager) {
        if (victim.getUniqueId().equals(damager.getUniqueId()))
            return;
        lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
        lastDamagerTime.put(victim.getUniqueId(), System.currentTimeMillis() + 10000);
    }

    public int getStartTimer(Arena arena) {
        StartingTask task = startTasks.get(arena);
        return (task != null) ? task.getTimer() : 0;
    }

    public Player getLastDamager(Player victim) {
        if (!lastDamagerTime.containsKey(victim.getUniqueId()))
            return null;
        if (lastDamagerTime.get(victim.getUniqueId()) < System.currentTimeMillis())
            return null;
        return org.bukkit.Bukkit.getPlayer(lastDamager.get(victim.getUniqueId()));
    }

    private void startCountdown(Arena arena, int seconds) {
        if (arena.getState() == Arena.GameState.IN_GAME || arena.getState() == Arena.GameState.ENDING) {
            return;
        }

        StartingTask existingTask = startTasks.get(arena);
        if (existingTask != null) {
            existingTask.setTimer(seconds);
            if (arena.getState() != Arena.GameState.STARTING) {
                arena.setState(Arena.GameState.STARTING);
            }
            return;
        }

        arena.setState(Arena.GameState.STARTING);
        StartingTask task = new StartingTask(arena, seconds);
        startTasks.put(arena, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private class StartingTask extends BukkitRunnable {
        private final Arena arena;
        private int timer;

        public StartingTask(Arena arena, int timer) {
            this.arena = arena;
            this.timer = timer;
        }

        public int getTimer() {
            return timer;
        }

        public void setTimer(int seconds) {
            if (seconds < this.timer) {
                this.timer = seconds;
            }
        }

        @Override
        public void run() {
            if (arena.getState() != Arena.GameState.STARTING && arena.getState() != Arena.GameState.WAITING) {
                startTasks.remove(arena, this);
                this.cancel();
                return;
            }

            if (arena.getPlayers().isEmpty()) {
                arena.setState(Arena.GameState.WAITING);
                startTasks.remove(arena, this);
                this.cancel();
                return;
            }

            if (timer <= 0) {
                startGame(arena);
                startTasks.remove(arena, this);
                this.cancel();
                return;
            }

            if (timer <= 5 || timer == 10 || timer == 30 || timer == 60) {
                arena.getPlayers().forEach(p -> {
                    String msg = plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-starting");
                    p.sendMessage(msg.replace("%seconds%", String.valueOf(timer)));

                    String soundPath = "game-countdown-s" + Math.min(timer, 5);
                    if (timer > 5)
                        soundPath = "game-countdown-others";
                    plugin.getSoundManager().playSound(p, soundPath);

                    if (timer <= 5) {
                        String color;
                        switch (timer) {
                            case 5:
                            case 4:
                                color = "§a";
                                break;
                            case 3:
                            case 2:
                                color = "§e";
                                break;
                            default:
                                color = "§c";
                                break;
                        }

                        StringBuilder circles = new StringBuilder();
                        for (int i = 0; i < 5; i++) {
                            if (i < timer)
                                circles.append(color).append("● ");
                            else
                                circles.append("§8○ ");
                        }

                        plugin.sendTitle(p, color + "§l" + timer, circles.toString().trim(), 0, 20, 0);
                    }
                });
            }

            timer--;
        }
    }

    private boolean isInside(org.bukkit.Location loc, org.bukkit.Location p1, org.bukkit.Location p2) {
        if (p1 == null || p2 == null || loc == null)
            return false;
        if (!loc.getWorld().getName().equals(p1.getWorld().getName()))
            return false;
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double maxY = Math.max(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public synchronized void startGame(Arena arena) {
        if (arena.getState() == Arena.GameState.IN_GAME || arena.getState() == Arena.GameState.ENDING) {
            return;
        }

        StartingTask existingTask = startTasks.remove(arena);
        if (existingTask != null) {
            existingTask.cancel();
        }

        sessionStats.put(arena, new ConcurrentHashMap<>());
        arena.setState(Arena.GameState.IN_GAME);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != Arena.GameState.IN_GAME) {
                    this.cancel();
                    return;
                }

                for (Team t : arena.getTeams()) {
                    if (t.isEliminated())
                        continue;
                    if (t.getUpgradeLevel("heal_pool") > 0) {
                        for (Player p : t.getMembers()) {
                            if (p.isOnline() && p.getLocation().getWorld().equals(t.getBasePos1().getWorld())
                                    && isInside(p.getLocation(), t.getBasePos1(), t.getBasePos2())) {
                                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                        org.bukkit.potion.PotionEffectType.REGENERATION, 100, 0));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        assignTeams(arena);

        arena.getInitialBedStates().clear();
        for (Team team : arena.getTeams()) {
            if (team.getBedLocation() != null) {
                org.bukkit.block.Block b = team.getBedLocation().getBlock();
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            org.bukkit.block.Block rel = b.getRelative(x, y, z);
                            String name = rel.getType().name();
                            if (name.endsWith("_BED") || name.equals("BED_BLOCK")) {
                                arena.getInitialBedStates().add(rel.getState());
                            }
                        }
                    }
                }
            }
        }

        for (Team team : arena.getTeams()) {
            if (team.getMembers().isEmpty()) {
                team.setBedBroken(true);
                if (team.getBedLocation() != null) {
                    team.getBedLocation().getBlock().setType(org.bukkit.Material.AIR);
                }
            }
        }

        arena.getPlayers().forEach(p -> {
            p.getInventory().clear();
            p.getEnderChest().clear();
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            p.setFoodLevel(20);
            p.setHealth(20);

            Team team = getPlayerTeam(arena, p);
            if (team != null) {
                playerArmorTiers.putIfAbsent(p.getUniqueId(), "LEATHER");
                giveStartingKit(p, team);
                applyPvpSettings(p, arena);

                if (team.getSpawnLocation() != null) {
                    p.teleport(team.getSpawnLocation());
                } else {
                    p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "error-spawn-not-set")
                            .replace("%team%", team.getName()));
                    plugin.getLogger().warning(
                            "Spawn location missing for team " + team.getName() + " in arena " + arena.getName());
                    if (arena.getLobbyLocation() != null)
                        p.teleport(arena.getLobbyLocation());
                }
            } else {
                p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "error-not-assigned-team"));
            }

            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            plugin.getSoundManager().playSound(p, "game-countdown-start");
        });

        arena.getPlayers().forEach(p -> {
            plugin.getLanguageManager().getMessageList(p.getUniqueId(), "game-started")
                    .forEach(p::sendMessage);
        });

        if (arena.getWorldName() != null) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
            if (world != null) {
                world.setAutoSave(false);
                world.setDifficulty(org.bukkit.Difficulty.EASY);
                prepareWorldRules(world);
                world.setGameRuleValue("mobGriefing", "true");
                world.setTime(6000L);
                world.setStorm(false);
                world.setThundering(false);
                world.getWorldBorder().reset();
                // Remove any lingering setup holograms left by admins in edit mode
                world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).stream()
                        .filter(as -> as.getScoreboardTags().contains("bw_hologram"))
                        .forEach(org.bukkit.entity.Entity::remove);
            }
        }

        spawnGameVisuals(arena);
        plugin.getVisualizationManager().spawnGeneratorVisuals(arena);

        if (plugin.getConfig().getBoolean("game.remove-waiting-lobby", true)) {
            removeLobbyStructure(arena);
        }

        arena.setTicks(0);
        arena.setGameTime(0);
        arena.setEventIndex(0);
        arena.setDiamondTier(1);
        arena.setEmeraldTier(1);
        arena.setDiamondCooldown(0);
        arena.setEmeraldCooldown(0);
        org.bukkit.configuration.file.FileConfiguration gConfig = plugin.getConfigManager().getGeneratorConfig();
        java.util.List<Map<?, ?>> events = gConfig.getMapList("events");
        if (!events.isEmpty()) {
            int firstDuration = resolveEventDurationSeconds(events, 0, 360);
            arena.setEventTimer(firstDuration);
        } else {
            arena.setEventTimer(-1);
        }

        plugin.getSignManager().updateSigns(arena);
    }

    private int resolveEventDurationSeconds(java.util.List<Map<?, ?>> events, int index, int fallbackSeconds) {
        if (events == null || index < 0 || index >= events.size()) {
            return fallbackSeconds;
        }

        Map<?, ?> event = events.get(index);
        int duration = parsePositiveInt(event.get("duration"), -1);
        if (duration > 0) {
            return duration;
        }

        // Legacy format support: "time" as absolute timestamp since game start.
        int absoluteTime = parsePositiveInt(event.get("time"), -1);
        if (absoluteTime > 0) {
            if (index == 0) {
                return absoluteTime;
            }
            int previousAbsolute = parsePositiveInt(events.get(index - 1).get("time"), -1);
            if (previousAbsolute > 0 && absoluteTime > previousAbsolute) {
                return absoluteTime - previousAbsolute;
            }
            return absoluteTime;
        }

        plugin.getLogger().warning("Event at index " + index + " in arena schedule is missing duration/time, using default "
                + fallbackSeconds + " seconds");
        return fallbackSeconds;
    }

    private int parsePositiveInt(Object value, int fallback) {
        if (value instanceof Number) {
            int parsed = ((Number) value).intValue();
            return parsed > 0 ? parsed : fallback;
        }
        if (value instanceof String) {
            try {
                int parsed = Integer.parseInt(((String) value).trim());
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void removeLobbyStructure(Arena arena) {
        if (arena.getLobbyLocation() == null)
            return;

        if (arena.getLobbyPos1() != null && arena.getLobbyPos2() != null) {
            Location l1 = arena.getLobbyPos1();
            Location l2 = arena.getLobbyPos2();

            if (!l1.getWorld().getName().equals(l2.getWorld().getName()))
                return;

            int minX = Math.min(l1.getBlockX(), l2.getBlockX());
            int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
            int minY = Math.min(l1.getBlockY(), l2.getBlockY());
            int maxY = Math.max(l1.getBlockY(), l2.getBlockY());
            int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
            int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        org.bukkit.block.Block block = l1.getWorld().getBlockAt(x, y, z);
                        if (block.getType() != org.bukkit.Material.AIR) {
                            block.setType(org.bukkit.Material.AIR);
                        }
                    }
                }
            }
            return;
        }

        int radius = plugin.getConfig().getInt("game.waiting-lobby-radius", 10);
        Location center = arena.getLobbyLocation();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.getBlock().getType() != org.bukkit.Material.AIR) {
                        loc.getBlock().setType(org.bukkit.Material.AIR);
                    }
                }
            }
        }
    }

    private void assignTeams(Arena arena) {
        boolean randomAssignment = plugin.getConfig().getBoolean("game.random-team-assignment", true);
        java.util.Random random = randomAssignment ? java.util.concurrent.ThreadLocalRandom.current() : null;

        List<Player> shuffledPlayers = new ArrayList<>(arena.getPlayers());
        if (randomAssignment) {
            java.util.Collections.shuffle(shuffledPlayers, random);
        }

        Map<UUID, Team> teamsMap = new ConcurrentHashMap<>();
        int playersPerTeam = arena.getMode().getPlayersPerTeam();

        for (Team team : arena.getTeams()) {
            team.getMembers().clear();
        }

        List<Team> randomizedTeams = new ArrayList<>(arena.getTeams());
        if (randomAssignment) {
            java.util.Collections.shuffle(randomizedTeams, random);
        }

        List<List<Player>> groups = plugin.getPartyManager().buildAssignmentGroups(shuffledPlayers, playersPerTeam);
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        for (List<Player> group : groups) {
            Team target = findLeastFilledTeam(randomizedTeams, group.size(), playersPerTeam, random);

            if (target == null) {
                for (Player player : group) {
                    Team singleTarget = findLeastFilledTeam(randomizedTeams, 1, playersPerTeam, random);

                    if (singleTarget == null) {
                        continue;
                    }

                    singleTarget.getMembers().add(player);
                    teamsMap.put(player.getUniqueId(), singleTarget);
                }
                continue;
            }

            for (Player player : group) {
                target.getMembers().add(player);
                teamsMap.put(player.getUniqueId(), target);
            }
        }

        playerTeams.put(arena, teamsMap);
    }

    private Team findLeastFilledTeam(List<Team> teams, int neededSlots, int playersPerTeam, java.util.Random random) {
        int minSize = Integer.MAX_VALUE;
        List<Team> candidates = new ArrayList<>();

        for (Team team : teams) {
            int size = team.getMembers().size();
            if (size + neededSlots > playersPerTeam) {
                continue;
            }

            if (size < minSize) {
                minSize = size;
                candidates.clear();
                candidates.add(team);
            } else if (size == minSize) {
                candidates.add(team);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (random == null) {
            return candidates.get(0);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    public Team getPlayerTeam(Arena arena, Player player) {
        Map<UUID, Team> map = playerTeams.get(arena);
        return map != null ? map.get(player.getUniqueId()) : null;
    }

    private void spawnGameVisuals(Arena arena) {
        clearDroppedItems(arena);
        plugin.getVisualizationManager().spawnGameHolograms(arena);

        plugin.getNpcManager().spawnNPCs(arena);
    }


    public void cleanupArena(Arena arena) {
        // Capture team map BEFORE removing it — endGame calls leaveArena first which
        // empties arena.getPlayers(), so we must use the team map keyset for tier cleanup
        Map<UUID, Team> arenaPlayerTeams = playerTeams.remove(arena);
        if (arenaPlayerTeams != null) {
            for (UUID uuid : arenaPlayerTeams.keySet()) {
                playerPickaxeTiers.remove(uuid);
                playerAxeTiers.remove(uuid);
                playerArmorTiers.remove(uuid);
                playerHasShears.remove(uuid);
            }
        }
        // Also clean up any lobby-only players (waiting state, no team assigned)
        if (arena.getPlayers() != null) {
            for (Player p : arena.getPlayers()) {
                playerPickaxeTiers.remove(p.getUniqueId());
                playerAxeTiers.remove(p.getUniqueId());
                playerArmorTiers.remove(p.getUniqueId());
                playerHasShears.remove(p.getUniqueId());
            }
        }
        plugin.getVisualizationManager().removeGameHolograms(arena);

        plugin.getNpcManager().removeNPCs(arena);

        if (arena.getWorldName() != null) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(arena.getWorldName());
            if (w != null) {
                clearDroppedItems(arena);
                // Manually clear player-placed blocks as a fallback in case world reload doesn't
                for (org.bukkit.Location loc : arena.getPlacedBlocks()) {
                    try {
                        if (loc.getWorld() != null) {
                            loc.getBlock().setType(org.bukkit.Material.AIR, false);
                        }
                    } catch (Exception ignored) {}
                }
                // Manually restore bed blocks as a fallback
                for (org.bukkit.block.BlockState state : arena.getInitialBedStates()) {
                    try {
                        if (state.getLocation().getWorld() != null) {
                            state.update(true, false);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void clearDroppedItems(Arena arena) {
        if (arena == null || arena.getWorldName() == null) {
            return;
        }

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
        if (world == null) {
            return;
        }

        world.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(org.bukkit.entity.Entity::remove);
    }

    private void clearAllPotionEffects(Player player) {
        if (player == null) {
            return;
        }
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void handleDeath(Player player, Arena arena, String reason) {
        if (player.getGameMode() == GameMode.SPECTATOR)
            return;

        Team team = getPlayerTeam(arena, player);
        if (team == null) {
            player.setHealth(20);
            player.setFoodLevel(20);
            player.setGameMode(GameMode.SURVIVAL);
            clearAllPotionEffects(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.setItemOnCursor(new ItemStack(Material.AIR));
            player.closeInventory();

            if (arena.getLobbyLocation() != null) {
                player.teleport(arena.getLobbyLocation());
            } else {
                player.teleport(player.getWorld().getSpawnLocation());
            }
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "error-died-void-lobby"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
            return;
        }

        Player victim = player;
        Player killer = null;

        if (reason.contains("was killed by ") || reason.contains("was shot by ") || reason.contains("was blown up by ")
                || reason.contains("was fireballed by ")) {
            String temp = reason.replace("was killed by ", "").replace("was shot by ", "")
                    .replace("was blown up by ", "").replace("was fireballed by ", "");
            if (temp.endsWith("."))
                temp = temp.substring(0, temp.length() - 1);
            killer = org.bukkit.Bukkit.getPlayer(temp);
        }

        if (killer == null) {
            killer = getLastDamager(victim);
            if (killer != null) {
                reason = "was knocked into the void by " + killer.getName() + ".";
            }
        }

        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) {
            killer = null;
            if (reason.contains("void")) {
                reason = "fell into the void.";
            }
        }

        lastDamager.remove(victim.getUniqueId());
        lastDamagerTime.remove(victim.getUniqueId());

        plugin.getStatsManager().addDeath(victim.getUniqueId());
        if (killer != null) {
            plugin.getStatsManager().addKill(killer.getUniqueId());
            getOrCreateSessionStats(arena, killer.getUniqueId()).kills++;
        }

        boolean isFinal = team.isBedBroken();

        if (killer != null && killer.isOnline()) {
            List<org.bukkit.Material> resources = java.util.Arrays.asList(org.bukkit.Material.IRON_INGOT,
                    org.bukkit.Material.GOLD_INGOT, org.bukkit.Material.EMERALD,
                    org.bukkit.Material.DIAMOND);
            for (ItemStack itemStack : victim.getInventory().getContents()) {
                if (itemStack != null && resources.contains(itemStack.getType())) {
                    killer.getInventory().addItem(itemStack.clone());
                    String resName = plugin.getLanguageManager().getItemName(killer.getUniqueId(), itemStack.getType());
                    killer.sendMessage(
                            plugin.getLanguageManager().getMessage(killer.getUniqueId(), "player-looted-resources")
                                    .replace("%amount%", String.valueOf(itemStack.getAmount()))
                                    .replace("%resource%", resName)
                                    .replace("%player%", victim.getName()));
                }
            }
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setItemOnCursor(new ItemStack(Material.AIR));
        player.closeInventory();
        clearAllPotionEffects(player);
        player.setHealth(20);
        player.setFoodLevel(20);

        String msg;
        if (killer == null) {
            String key;
            if (reason.contains("fell into the void")) {
                key = isFinal ? "player-die-void-final" : "player-die-void-regular";
            } else {
                key = isFinal ? "player-die-unknown-final" : "player-die-unknown-regular";
            }
            msg = plugin.getLanguageManager().getMessage(player.getUniqueId(), key);
        } else {
            String key;
            if (reason.contains("shot by")) {
                key = isFinal ? "player-die-bow-final" : "player-die-bow-regular";
            } else if (reason.contains("blown up by")) {
                key = isFinal ? "player-die-tnt-final" : "player-die-tnt-regular";
            } else if (reason.contains("fireballed by")) {
                key = isFinal ? "player-die-fireball-final" : "player-die-fireball-regular";
            } else if (reason.contains("knocked into the void")) {
                key = isFinal ? "player-die-void-attack-final" : "player-die-void-attack-regular";
            } else {
                key = isFinal ? "player-die-attack-final" : "player-die-attack-regular";
            }
            msg = plugin.getLanguageManager().getMessage(player.getUniqueId(), key);
        }

        String playerColor = (team.getColor() != null) ? team.getColor().toString() : "§f";
        String playerPureName = player.getName();
        String killerColor = "";
        String killerPureName = "";
        if (killer != null) {
            Team killerTeam = getPlayerTeam(arena, killer);
            killerColor = (killerTeam != null && killerTeam.getColor() != null) ? killerTeam.getColor().toString()
                    : "§f";
            killerPureName = killer.getName();
        }

        String finalMsg = msg
                .replace("{PlayerColor}", playerColor)
                .replace("{PlayerName}", playerPureName)
                .replace("{KillerColor}", killerColor)
                .replace("{KillerName}", killerPureName);

        arena.getPlayers().forEach(p -> {
            p.sendMessage(finalMsg);
            plugin.getSoundManager().playSound(p, isFinal ? "bed-destroy" : "kill");
            plugin.getScoreboardManager().updateScoreboard(p);
        });

        if (isFinal) {
            long aliveMembers = team.getMembers().stream()
                    .filter(m -> m.getGameMode() != GameMode.SPECTATOR)
                    .filter(m -> !m.getUniqueId().equals(player.getUniqueId()))
                    .count();

            if (aliveMembers == 0) {
                team.setEliminated(true);
                String teamColor = (team.getColor() != null) ? team.getColor().toString() : "§f";

                String elimChat = plugin.getLanguageManager().getMessage(null, "team-eliminated-chat");
                String elimTitle = plugin.getLanguageManager().getMessage(null, "team-eliminated-title");
                String elimSubtitle = plugin.getLanguageManager().getMessage(null, "team-eliminated-subtitle");

                String formattedChat = elimChat.replace("{TeamColor}", teamColor).replace("{TeamName}",
                        team.getDisplayName());
                String formattedTitle = elimTitle;
                String formattedSubtitle = elimSubtitle.replace("{TeamColor}", teamColor).replace("{TeamName}",
                        team.getDisplayName());

                arena.getPlayers().forEach(p -> {
                    p.sendMessage(formattedChat);
                    plugin.sendTitle(p, formattedTitle, formattedSubtitle, 10, 40, 10);
                    plugin.getSoundManager().playSound(p, "team-eliminated");
                });

                plugin.getVisualizationManager().spawnGameHolograms(arena);
            }
            if (killer != null) {
                plugin.getStatsManager().addFinalKill(killer.getUniqueId());
                getOrCreateSessionStats(arena, killer.getUniqueId()).finalKills++;
            }
            plugin.getStatsManager().addDeath(player.getUniqueId());

            // Clear tier maps immediately so they don't persist into the next game
            playerPickaxeTiers.remove(player.getUniqueId());
            playerAxeTiers.remove(player.getUniqueId());
            playerArmorTiers.remove(player.getUniqueId());
            playerHasShears.remove(player.getUniqueId());

            player.setGameMode(GameMode.SPECTATOR);
            // Teleport to team spawn (inside the arena) so the spectator can fly around.
            // Do NOT use getLobbyLocation() — that is the pre-game waiting lobby, which is
            // outside the arena bounds and would trap the spectator immediately.
            if (team.getSpawnLocation() != null) {
                player.teleport(team.getSpawnLocation());
            } else if (arena.getLobbyLocation() != null) {
                player.teleport(arena.getLobbyLocation());
            }
            String specTitle = plugin.getLanguageManager().getMessage(player.getUniqueId(), "spectator-title");
            String specSub   = plugin.getLanguageManager().getMessage(player.getUniqueId(), "spectator-subtitle");
            plugin.sendTitle(player, specTitle, specSub, 10, 60, 10);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "spectator-message"));

            checkForWin(arena);
        } else {
            int pickTier = playerPickaxeTiers.getOrDefault(player.getUniqueId(), 0);
            if (pickTier > 0) {
                playerPickaxeTiers.put(player.getUniqueId(), Math.max(1, pickTier - 1));
            }
            int axeTier = playerAxeTiers.getOrDefault(player.getUniqueId(), 0);
            if (axeTier > 0) {
                playerAxeTiers.put(player.getUniqueId(), Math.max(1, axeTier - 1));
            }

            player.setGameMode(GameMode.SPECTATOR);

            if (arena.getLobbyLocation() != null) {
                player.teleport(arena.getLobbyLocation());
            } else if (team.getSpawnLocation() != null) {
                player.teleport(team.getSpawnLocation());
            }

            String title = plugin.getLanguageManager().getMessage(player.getUniqueId(), "player-died-title");
            String subtitle = plugin.getLanguageManager().getMessage(player.getUniqueId(), "player-died-subtitle")
                    .replace("%time%", "5");
            plugin.sendTitle(player, title, subtitle, 0, 25, 0);

            new BukkitRunnable() {
                int timeLeft = 5;

                @Override
                public void run() {
                    if (!player.isOnline() || arena.getState() != Arena.GameState.IN_GAME) {
                        this.cancel();
                        return;
                    }

                    if (timeLeft <= 0) {
                        player.setGameMode(GameMode.SURVIVAL);
                        if (team.getSpawnLocation() != null) {
                            player.teleport(team.getSpawnLocation());
                        } else {
                            player.teleport(arena.getLobbyLocation());
                        }
                        clearAllPotionEffects(player);
                        String respawnTitle = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                                "player-re-spawn-title");
                        plugin.sendTitle(player, respawnTitle, "", 5, 20, 5);
                        player.sendMessage(
                                plugin.getLanguageManager().getMessage(player.getUniqueId(), "player-respawned"));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                        giveStartingKit(player, team);
                        applyPvpSettings(player, arena);

                        this.cancel();
                        return;
                    }

                    if (timeLeft < 5) {
                        String rTitle = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                                "player-died-title");
                        String rSubtitle = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                                "player-died-subtitle")
                                .replace("%time%", String.valueOf(timeLeft));
                        player.sendTitle(rTitle, rSubtitle, 0, 25, 0);
                    }

                    timeLeft--;
                }
            }.runTaskTimer(plugin, 0L, 20L);

            // Even for non-final deaths, check whether the game should end.
            // This handles edge cases such as misconfigured arenas where multiple
            // players are on the same team — in that case, killing a teammate makes
            // only 1 team alive, and the game should end immediately.
            checkForWin(arena);
        }
    }

    public boolean isBedWarsItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR)
            return false;
        return true;
    }

    public void triggerGameEvent(Arena arena, String eventId) {
        if (eventId == null) {
            plugin.getLogger().warning("Attempted to trigger game event with null eventId for arena: " + arena.getName());
            return;
        }
        
        org.bukkit.configuration.file.FileConfiguration gConfig = plugin.getConfigManager().getGeneratorConfig();
        String broadcastFmt = plugin.getLanguageManager().getMessage(null, "generator-upgrade-broadcast");
        if (broadcastFmt.equals("§cMissing path: generator-upgrade-broadcast")) {
            broadcastFmt = "§b§l%type% GENERATORS §eUPGRADED TO §6§lTIER %tier%";
        }

        switch (eventId) {
            case "diamond_2":
                if (arena.getDiamondTier() < 2) {
                    arena.setDiamondTier(2);
                    broadcastUpgrade(arena, gConfig.getString("global_generators.diamond.name", "Diamond"), "II",
                            broadcastFmt);
                }
                break;
            case "emerald_2":
                if (arena.getEmeraldTier() < 2) {
                    arena.setEmeraldTier(2);
                    broadcastUpgrade(arena, gConfig.getString("global_generators.emerald.name", "Emerald"), "II",
                            broadcastFmt);
                }
                break;
            case "diamond_3":
                if (arena.getDiamondTier() < 3) {
                    arena.setDiamondTier(3);
                    broadcastUpgrade(arena, gConfig.getString("global_generators.diamond.name", "Diamond"), "III",
                            broadcastFmt);
                }
                break;
            case "emerald_3":
                if (arena.getEmeraldTier() < 3) {
                    arena.setEmeraldTier(3);
                    broadcastUpgrade(arena, gConfig.getString("global_generators.emerald.name", "Emerald"), "III",
                            broadcastFmt);
                }
                break;
            case "bed_gone":
                destroyAllBeds(arena);
                break;
            case "sudden_death":
                startSuddenDeath(arena);
                break;
            case "game_end":
                endGame(arena, null);
                break;
        }
    }

    private void broadcastUpgrade(Arena arena, String type, String tier, String fmt) {
        // Translate & color codes first (e.g. "&2&lEMERALD"), then strip them for a plain name.
        String translatedType = org.bukkit.ChatColor.translateAlternateColorCodes('&', type);
        String cleanType = org.bukkit.ChatColor.stripColor(translatedType);
        String msg = fmt.replace("%type%", cleanType != null ? cleanType : type).replace("%tier%", tier);
        arena.getPlayers().forEach(p -> {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        });
    }

    private void destroyAllBeds(Arena arena) {
        arena.getPlayers().forEach(p -> {
            p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "event-bed-destruction-chat"));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        });

        for (Team team : arena.getTeams()) {
            if (!team.isBedBroken()) {
                team.setBedBroken(true);
                if (team.getBedLocation() != null) {
                    team.getBedLocation().getBlock().setType(org.bukkit.Material.AIR);
                }
            }
        }

        plugin.getVisualizationManager().spawnGameHolograms(arena);
        arena.getPlayers().forEach(p -> plugin.getScoreboardManager().updateScoreboard(p));
    }

    private void startSuddenDeath(Arena arena) {
        arena.getPlayers().forEach(p -> {
            p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "event-sudden-death-chat"));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

            String title = plugin.getLanguageManager().getMessage(p.getUniqueId(), "event-sudden-death-title");
            String subtitle = plugin.getLanguageManager().getMessage(p.getUniqueId(), "event-sudden-death-subtitle");
            p.sendTitle(title, subtitle, 10, 60, 20);
        });

        if (arena.getWorldName() != null && arena.getPos1() != null && arena.getPos2() != null) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
            if (world != null) {
                double minX = Math.min(arena.getPos1().getX(), arena.getPos2().getX());
                double maxX = Math.max(arena.getPos1().getX(), arena.getPos2().getX());
                double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ());
                double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ());

                Location center = new Location(world, (minX + maxX) / 2.0, 100, (minZ + maxZ) / 2.0);
                double initialSize = Math.max(maxX - minX, maxZ - minZ) + 20;

                org.bukkit.WorldBorder border = world.getWorldBorder();
                border.setCenter(center);
                border.setSize(initialSize);
                border.setSize(2.0, 300);
                // Damage starts immediately at the border edge (no buffer), 0.5 HP per second
                border.setDamageBuffer(0.0);
                border.setDamageAmount(0.5);
                border.setWarningDistance(5);
                border.setWarningTime(5);
                startSuddenDeathBorderDamageTask(arena, border);
            }
        }

        spawnSuddenDeathDragons(arena);
    }

    private void startSuddenDeathBorderDamageTask(Arena arena, org.bukkit.WorldBorder border) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != Arena.GameState.IN_GAME || arena.getWorldName() == null) {
                    cancel();
                    return;
                }

                org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
                if (world == null || !world.equals(border.getCenter().getWorld())) {
                    cancel();
                    return;
                }

                for (Player player : arena.getPlayers()) {
                    if (player.getGameMode() != GameMode.SURVIVAL || !player.getWorld().equals(world) || player.isDead()) {
                        continue;
                    }

                    double distanceOutside = getWorldBorderOverflow(player.getLocation(), border);
                    if (distanceOutside > 0.0D) {
                        player.damage(Math.max(0.5D, distanceOutside * border.getDamageAmount()));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private double getWorldBorderOverflow(Location location, org.bukkit.WorldBorder border) {
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0D;
        double overflowX = Math.max(0.0D, Math.abs(location.getX() - center.getX()) - halfSize);
        double overflowZ = Math.max(0.0D, Math.abs(location.getZ() - center.getZ()) - halfSize);
        return Math.max(overflowX, overflowZ);
    }

    private void spawnSuddenDeathDragons(Arena arena) {
        if (arena.getWorldName() == null)
            return;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
        if (world == null)
            return;

        List<Team> aliveTeams = arena.getTeams().stream()
                .filter(t -> t.getMembers().stream().anyMatch(p -> p.getGameMode() != GameMode.SPECTATOR))
                .collect(java.util.stream.Collectors.toList());

        for (Team team : aliveTeams) {
            Location spawnLoc;
            if (team.getSpawnLocation() != null) {
                spawnLoc = team.getSpawnLocation().clone().add(0, 50, 0);
            } else if (arena.getLobbyLocation() != null) {
                spawnLoc = arena.getLobbyLocation().clone().add(0, 50, 0);
            } else {
                spawnLoc = world.getSpawnLocation().clone().add(0, 50, 0);
            }

            String teamColor = (team.getColor() != null) ? team.getColor().toString() : "§f";
            String teamName = team.getDisplayName();

            org.bukkit.entity.EnderDragon dragon = world.spawn(spawnLoc, org.bukkit.entity.EnderDragon.class, d -> {
                String dragonName = plugin.getLanguageManager().getMessage(null, "entity-sudden-death-dragon-team")
                        .replace("{TeamColor}", teamColor)
                        .replace("{TeamName}", teamName);
                d.setCustomName(dragonName);
                d.setCustomNameVisible(true);
                d.setHealth(200.0);
                d.addScoreboardTag("bw_dragon");
                d.addScoreboardTag("bw_sudden_death");
                d.addScoreboardTag("team_" + team.getName());
                d.setPhase(org.bukkit.entity.EnderDragon.Phase.CIRCLING);
            });

            startSuddenDeathDragonLogic(dragon, arena, team);

            if (team.getUpgradeLevel("dragon") > 0) {
                Location bonusLoc = spawnLoc.clone().add(10, 0, 10);
                org.bukkit.entity.EnderDragon bonusDragon = world.spawn(bonusLoc, org.bukkit.entity.EnderDragon.class, d -> {
                    String dragonName = plugin.getLanguageManager().getMessage(null, "entity-sudden-death-dragon-team")
                            .replace("{TeamColor}", teamColor)
                            .replace("{TeamName}", teamName + " II");
                    d.setCustomName(dragonName);
                    d.setCustomNameVisible(true);
                    d.setHealth(200.0);
                    d.addScoreboardTag("bw_dragon");
                    d.addScoreboardTag("bw_sudden_death");
                    d.addScoreboardTag("team_" + team.getName());
                    d.setPhase(org.bukkit.entity.EnderDragon.Phase.CIRCLING);
                });

                startSuddenDeathDragonLogic(bonusDragon, arena, team);
            }
        }
    }

    private void startSuddenDeathDragonLogic(org.bukkit.entity.EnderDragon dragon, Arena arena, Team ownerTeam) {
        new BukkitRunnable() {
            Location targetLocation = null;
            int ticks = 0;

            @Override
            public void run() {
                if (dragon.isDead() || !dragon.isValid() || arena.getState() != Arena.GameState.IN_GAME) {
                    if (dragon.isValid() && !dragon.isDead()) {
                        dragon.remove();
                    }
                    this.cancel();
                    return;
                }

                if (ticks % 100 == 0 || targetLocation == null) {
                    targetLocation = findNearestEnemyPlayer(dragon, arena, ownerTeam);
                    if (targetLocation == null) {
                        targetLocation = findEnemyBedLocation(arena, ownerTeam);
                    }
                }

                if (targetLocation != null) {
                    Location dragonLoc = dragon.getLocation();
                    Location flightTarget = adjustDragonFlightTarget(targetLocation, dragonLoc, arena);

                    if (arena.getPos1() != null && arena.getPos2() != null) {
                        double minX = Math.min(arena.getPos1().getX(), arena.getPos2().getX());
                        double maxX = Math.max(arena.getPos1().getX(), arena.getPos2().getX());
                        double minY = Math.min(arena.getPos1().getY(), arena.getPos2().getY()) - 12;
                        double maxY = Math.max(arena.getPos1().getY(), arena.getPos2().getY()) + 40;
                        double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ());
                        double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ());

                        double cx = (minX + maxX) / 2.0;
                        double cz = (minZ + maxZ) / 2.0;

                        org.bukkit.util.Vector correction = new org.bukkit.util.Vector(0, 0, 0);

                        // XZ boundary — push back with a buffer of just 5 blocks
                        boolean outXZ = dragonLoc.getX() < minX - 5 || dragonLoc.getX() > maxX + 5
                                || dragonLoc.getZ() < minZ - 5 || dragonLoc.getZ() > maxZ + 5;
                        if (outXZ) {
                            correction = correction.add(
                                    new Location(dragon.getWorld(), cx, dragonLoc.getY(), cz)
                                            .toVector().subtract(dragonLoc.toVector()).normalize().multiply(2.5));
                        }

                        // Y boundary — push back strongly if outside arena height
                        double clampedY = Math.max(minY + 8, Math.min(maxY - 6, dragonLoc.getY()));
                        double yDiff = clampedY - dragonLoc.getY();
                        if (Math.abs(yDiff) > 3) {
                            correction = correction.add(new org.bukkit.util.Vector(0, yDiff * 0.15, 0));
                        }

                        if (correction.lengthSquared() > 0) {
                            dragon.setVelocity(dragon.getVelocity().add(correction));
                        }
                    }

                    if (ticks % 200 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER);
                    } else if (ticks % 300 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                    } else if (ticks % 100 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.FLY_TO_PORTAL);
                    }

                    double distance = dragonLoc.distance(flightTarget);
                    if (distance > 5) {
                        org.bukkit.util.Vector toTarget = flightTarget.toVector().subtract(dragonLoc.toVector())
                                .normalize().multiply(1.2);
                        dragon.setVelocity(dragon.getVelocity().add(toTarget));
                    }

                    attackNearbyEnemies(dragon, arena, ownerTeam);
                    destroyNearbyEnemyBeds(dragon, arena, ownerTeam);
                    destroyNearbyBlocks(dragon, arena);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private void destroyNearbyBlocks(org.bukkit.entity.EnderDragon dragon, Arena arena) {
        Location dragonLoc = dragon.getLocation();
        org.bukkit.World world = dragonLoc.getWorld();
        if (world == null) return;

        int radius = 4;
        int centerX = dragonLoc.getBlockX();
        int centerY = dragonLoc.getBlockY();
        int centerZ = dragonLoc.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == org.bukkit.Material.AIR 
                        || block.getType() == org.bukkit.Material.BEDROCK
                        || block.getType().name().endsWith("_BED") || block.getType().name().equals("BED_BLOCK")) {
                        continue;
                    }

                    double distSq = dragonLoc.distanceSquared(block.getLocation().add(0.5, 0.5, 0.5));
                    if (distSq <= radius * radius) {
                        world.spawnParticle(org.bukkit.Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5),
                                5, 0.3, 0.3, 0.3, block.getBlockData());
                        block.setType(org.bukkit.Material.AIR);
                    }
                }
            }
        }
    }

    public void spawnTeamDragon(Player player, Arena arena, Location spawnLocation) {
        if (arena.getWorldName() == null)
            return;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
        if (world == null)
            return;

        Team playerTeam = getPlayerTeam(arena, player);
        if (playerTeam == null)
            return;

        Location dragonSpawn = spawnLocation.clone().add(0, 3, 0);

        org.bukkit.entity.EnderDragon dragon = world.spawn(dragonSpawn, org.bukkit.entity.EnderDragon.class, d -> {
            String dragonName = plugin.getLanguageManager().getMessage(player.getUniqueId(), "entity-team-dragon")
                    .replace("{TeamColor}", playerTeam.getColor() != null ? playerTeam.getColor().toString() : "")
                    .replace("{TeamName}", playerTeam.getName());
            d.setCustomName(dragonName);
            d.setCustomNameVisible(true);
            d.setHealth(200.0);
            d.addScoreboardTag("bw_dragon");
            d.addScoreboardTag("team_" + playerTeam.getName());
            d.setPhase(org.bukkit.entity.EnderDragon.Phase.CIRCLING);
        });

        startTeamDragonLogic(dragon, arena, playerTeam);

        for (Player p : arena.getPlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    private void startTeamDragonLogic(org.bukkit.entity.EnderDragon dragon, Arena arena, Team ownerTeam) {
        new BukkitRunnable() {
            Location targetLocation = null;
            int ticks = 0;
            int lifetime = 30 * 20;

            @Override
            public void run() {
                if (dragon.isDead() || !dragon.isValid() || arena.getState() != Arena.GameState.IN_GAME || ticks >= lifetime) {
                    if (dragon.isValid() && !dragon.isDead()) {
                        dragon.remove();
                    }
                    this.cancel();
                    return;
                }

                if (ticks % 100 == 0 || targetLocation == null) {
                    targetLocation = findEnemyBedLocation(arena, ownerTeam);
                    if (targetLocation == null) {
                        targetLocation = findNearestEnemyPlayer(dragon, arena, ownerTeam);
                    }
                }

                if (targetLocation != null) {
                    Location dragonLoc = dragon.getLocation();
                    Location flightTarget = adjustDragonFlightTarget(targetLocation, dragonLoc, arena);

                    if (arena.getPos1() != null && arena.getPos2() != null) {
                        double minX = Math.min(arena.getPos1().getX(), arena.getPos2().getX()) - 50;
                        double maxX = Math.max(arena.getPos1().getX(), arena.getPos2().getX()) + 50;
                        double minY = Math.min(arena.getPos1().getY(), arena.getPos2().getY()) - 12;
                        double maxY = Math.max(arena.getPos1().getY(), arena.getPos2().getY()) + 40;
                        double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ()) - 50;
                        double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ()) + 50;

                        org.bukkit.util.Vector correction = new org.bukkit.util.Vector(0, 0, 0);

                        if (dragonLoc.getX() < minX || dragonLoc.getX() > maxX ||
                                dragonLoc.getZ() < minZ || dragonLoc.getZ() > maxZ) {
                            correction = correction.add(new Location(dragon.getWorld(),
                                    (minX + maxX) / 2, dragonLoc.getY(), (minZ + maxZ) / 2)
                                    .toVector().subtract(dragonLoc.toVector()).normalize().multiply(1.5));
                        }

                        double clampedY = Math.max(minY + 8, Math.min(maxY - 6, dragonLoc.getY()));
                        double yDiff = clampedY - dragonLoc.getY();
                        if (Math.abs(yDiff) > 3) {
                            correction = correction.add(new org.bukkit.util.Vector(0, yDiff * 0.15, 0));
                        }

                        if (correction.lengthSquared() > 0) {
                            dragon.setVelocity(dragon.getVelocity().add(correction));
                        }
                    }

                    if (ticks % 200 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER);
                    } else if (ticks % 300 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                    } else if (ticks % 100 == 0) {
                        dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.FLY_TO_PORTAL);
                    }

                    double distance = dragonLoc.distance(flightTarget);
                    if (distance > 5) {
                        org.bukkit.util.Vector toTarget = flightTarget.toVector().subtract(dragonLoc.toVector())
                                .normalize().multiply(1.2);
                        dragon.setVelocity(dragon.getVelocity().add(toTarget));
                    }

                    attackNearbyEnemies(dragon, arena, ownerTeam);

                    destroyNearbyEnemyBeds(dragon, arena, ownerTeam);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private Location findEnemyBedLocation(Arena arena, Team ownerTeam) {
        for (Team team : arena.getTeams()) {
            if (!team.getName().equals(ownerTeam.getName()) && !team.isBedBroken()) {
                Location bedLoc = team.getBedLocation();
                if (bedLoc != null) {
                    return bedLoc.clone().add(0, 5, 0);
                }
            }
        }
        return null;
    }

    private Location adjustDragonFlightTarget(Location rawTarget, Location dragonLocation, Arena arena) {
        Location adjustedTarget = rawTarget.clone();

        double minY = 32.0;
        double maxY = dragonLocation.getWorld() != null ? dragonLocation.getWorld().getMaxHeight() - 16.0 : rawTarget.getY() + 32.0;
        if (arena.getPos1() != null && arena.getPos2() != null) {
            minY = Math.min(arena.getPos1().getY(), arena.getPos2().getY()) - 12.0;
            maxY = Math.max(arena.getPos1().getY(), arena.getPos2().getY()) + 40.0;
        }

        double lowerY = minY + 8.0;
        double upperY = Math.max(lowerY + 1.0, maxY - 6.0);
        double preferredY = Math.max(rawTarget.getY() + 16.0, dragonLocation.getY() - 2.0);
        adjustedTarget.setY(Math.max(lowerY, Math.min(upperY, preferredY)));
        return adjustedTarget;
    }

    private Location findNearestEnemyPlayer(org.bukkit.entity.EnderDragon dragon, Arena arena, Team ownerTeam) {
        Player nearest = null;
        double minDst = Double.MAX_VALUE;

        for (Player p : arena.getPlayers()) {
            Team playerTeam = getPlayerTeam(arena, p);
            if (playerTeam != null && !playerTeam.getName().equals(ownerTeam.getName()) &&
                    p.getGameMode() == GameMode.SURVIVAL && p.getWorld().equals(dragon.getWorld())) {
                double dst = p.getLocation().distanceSquared(dragon.getLocation());
                if (dst < minDst) {
                    minDst = dst;
                    nearest = p;
                }
            }
        }
        return nearest != null ? nearest.getLocation() : null;
    }

    private void attackNearbyEnemies(org.bukkit.entity.EnderDragon dragon, Arena arena, Team ownerTeam) {
        for (Player p : arena.getPlayers()) {
            Team playerTeam = getPlayerTeam(arena, p);
            if (playerTeam != null && !playerTeam.getName().equals(ownerTeam.getName()) &&
                    p.getGameMode() == GameMode.SURVIVAL) {
                double distance = p.getLocation().distance(dragon.getLocation());
                if (distance < 5.0) {
                    p.damage(4.0);
                }
            }
        }
    }

    private void destroyNearbyEnemyBeds(org.bukkit.entity.EnderDragon dragon, Arena arena, Team ownerTeam) {
        for (Team team : arena.getTeams()) {
            if (!team.getName().equals(ownerTeam.getName()) && !team.isBedBroken()) {
                Location bedLoc = team.getBedLocation();
                if (bedLoc != null) {
                    double distance = dragon.getLocation().distance(bedLoc);
                    if (distance < 8.0) {
                        team.setBedBroken(true);
                        bedLoc.getBlock().setType(org.bukkit.Material.AIR);

                        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                                org.bukkit.block.BlockFace.NORTH,
                                org.bukkit.block.BlockFace.SOUTH,
                                org.bukkit.block.BlockFace.EAST,
                                org.bukkit.block.BlockFace.WEST
                        }) {
                            org.bukkit.block.Block relative = bedLoc.getBlock().getRelative(face);
                            if (relative.getType().name().contains("BED")) {
                                relative.setType(org.bukkit.Material.AIR);
                            }
                        }

                        String bedMsg = plugin.getLanguageManager().getMessage(null, "interact-bed-destroy-chat");
                        String ownerColor = (ownerTeam.getColor() != null ? ownerTeam.getColor().toString() : "§f");
                        String formattedMsg = bedMsg
                                .replace("{TeamColor}", team.getColor() != null ? team.getColor().toString() : "§f")
                                .replace("{TeamName}", team.getName())
                                .replace("{PlayerColor}", ownerColor)
                                .replace("{PlayerName}", ownerTeam.getName() + " Dragon");

                        for (Player p : arena.getPlayers()) {
                            p.sendMessage(formattedMsg);
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

                            Team playerTeam = getPlayerTeam(arena, p);
                            if (playerTeam != null && playerTeam.getName().equals(team.getName())) {
                                String title = plugin.getLanguageManager().getMessage(p.getUniqueId(), "interact-bed-destroy-title");
                                String subtitle = plugin.getLanguageManager().getMessage(p.getUniqueId(), "interact-bed-destroy-subtitle");
                                p.sendTitle(title, subtitle, 10, 60, 20);
                            }
                        }

                        plugin.getVisualizationManager().spawnGameHolograms(arena);
                    }
                }
            }
        }
    }

    public void checkForWin(Arena arena) {
        if (arena.getState() != Arena.GameState.IN_GAME)
            return;

        List<Team> aliveTeams = new ArrayList<>();
        for (Team t : arena.getTeams()) {
            boolean anyAlive = t.getMembers().stream()
                    .anyMatch(p -> p.isOnline() && p.getGameMode() != GameMode.SPECTATOR);

            // A team is still alive if their bed is intact AND they have members who can respawn.
            // If all members left (via /leave), the bed no longer saves them.
            boolean bedIntact = !t.isBedBroken() && !t.getMembers().isEmpty();

            if (anyAlive || bedIntact) {
                aliveTeams.add(t);
            } else if (!t.isEliminated()) {
                t.setEliminated(true);
                plugin.getVisualizationManager().spawnGameHolograms(arena);
            }
        }

        if (aliveTeams.size() <= 1) {
            if (arena.getPlayers().size() == 0) {
                endGame(arena, null);
                return;
            }

            if (arena.isSoloTest() && arena.getPlayers().size() == 1) {
                return;
            }

            Team winner = aliveTeams.isEmpty() ? null : aliveTeams.get(0);
            endGame(arena, winner);
        }
    }

    public int getPlayerPickaxeTier(UUID uuid) {
        return playerPickaxeTiers.getOrDefault(uuid, 0);
    }

    public void setPlayerPickaxeTier(UUID uuid, int tier) {
        playerPickaxeTiers.put(uuid, tier);
    }

    public int getPlayerAxeTier(UUID uuid) {
        return playerAxeTiers.getOrDefault(uuid, 0);
    }

    public void setPlayerAxeTier(UUID uuid, int tier) {
        playerAxeTiers.put(uuid, tier);
    }

    public String getPlayerArmorTier(UUID uuid) {
        return playerArmorTiers.getOrDefault(uuid, "LEATHER");
    }

    public void setPlayerArmorTier(Player player, String tier) {
        playerArmorTiers.put(player.getUniqueId(), tier);
        Arena arena = getPlayerTeam(plugin.getArenaManager().getPlayerArena(player), player) != null
                ? plugin.getArenaManager().getPlayerArena(player)
                : null;
        if (arena != null) {
            giveStartingKit(player, getPlayerTeam(arena, player));
            applyPvpSettings(player, arena);
        }
    }

    public boolean playerHasShears(UUID uuid) {
        return playerHasShears.contains(uuid);
    }

    public void setPlayerHasShears(UUID uuid, boolean has) {
        if (has) playerHasShears.add(uuid);
        else playerHasShears.remove(uuid);
    }

    public void resetPlayerPermanentPurchases(UUID uuid) {
        playerPickaxeTiers.remove(uuid);
        playerAxeTiers.remove(uuid);
        playerArmorTiers.remove(uuid);
        playerHasShears.remove(uuid);
    }

    public void normalizeSwordInventory(Player player, Team team) {
        if (player == null)
            return;

        boolean hasBetterSword = false;
        boolean hasAnySword = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR || !item.getType().name().endsWith("_SWORD")) {
                continue;
            }
            hasAnySword = true;
            if (item.getType() != Material.WOODEN_SWORD) {
                hasBetterSword = true;
            }
        }

        if (hasBetterSword) {
            removeSpecificItem(player, Material.WOODEN_SWORD);
            hasAnySword = hasAnySword(player);
        }

        if (!hasAnySword) {
            ItemStack sword = com.cryptomorin.xseries.XMaterial.WOODEN_SWORD.parseItem();
            if (sword == null)
                sword = new ItemStack(Material.WOODEN_SWORD);
            setUnbreakable(sword);
            renameItem(player, sword);
            player.getInventory().addItem(sword);
        }

        applyTeamUpgrades(player, team);
    }

    public void placePurchasedSword(Player player, Team team, ItemStack sword) {
        if (player == null || sword == null || sword.getType() == Material.AIR) {
            return;
        }

        int woodenSlot = findFirstSlot(player, Material.WOODEN_SWORD);
        if (woodenSlot >= 0) {
            player.getInventory().setItem(woodenSlot, sword);
        } else {
            player.getInventory().addItem(sword);
        }

        applyTeamUpgrades(player, team);
    }

    public void ensureWoodenSwordInSlot(Player player, Team team, int preferredSlot) {
        if (player == null || hasAnySword(player)) {
            return;
        }

        ItemStack sword = com.cryptomorin.xseries.XMaterial.WOODEN_SWORD.parseItem();
        if (sword == null) {
            sword = new ItemStack(Material.WOODEN_SWORD);
        }

        setUnbreakable(sword);
        renameItem(player, sword);

        if (preferredSlot >= 0 && preferredSlot < player.getInventory().getSize()) {
            ItemStack current = player.getInventory().getItem(preferredSlot);
            if (current == null || current.getType() == Material.AIR) {
                player.getInventory().setItem(preferredSlot, sword);
                applyTeamUpgrades(player, team);
                return;
            }
        }

        player.getInventory().addItem(sword);
        applyTeamUpgrades(player, team);
    }

    public void giveStartingKit(Player player, Team team) {
        if (team == null)
            return;

        applyTeamUpgrades(player, team);

        // Restore permanent shears
        if (playerHasShears.contains(player.getUniqueId())) {
            boolean hasShears = false;
            for (org.bukkit.inventory.ItemStack is : player.getInventory().getContents()) {
                if (is != null && is.getType() == org.bukkit.Material.SHEARS) {
                    hasShears = true;
                    break;
                }
            }
            if (!hasShears) {
                org.bukkit.inventory.ItemStack shears = new org.bukkit.inventory.ItemStack(org.bukkit.Material.SHEARS);
                setUnbreakable(shears);
                renameItem(player, shears);
                player.getInventory().addItem(shears);
            }
        }

        int pickTier = playerPickaxeTiers.getOrDefault(player.getUniqueId(), 0);
        if (pickTier > 0) {
            removeSimilarItems(player, "_PICKAXE");
            player.getInventory().addItem(createTool(player, "PICKAXE", pickTier));
        }
        int axeTier = playerAxeTiers.getOrDefault(player.getUniqueId(), 0);
        if (axeTier > 0) {
            removeSimilarItems(player, "_AXE");
            player.getInventory().addItem(createTool(player, "AXE", axeTier));
        }

        String tier = playerArmorTiers.getOrDefault(player.getUniqueId(), "LEATHER");
        org.bukkit.Color color = getTeamColor(team);

        ItemStack helmet, chestplate, leggings, boots;

        helmet = createColoredArmor(org.bukkit.Material.LEATHER_HELMET, color);
        chestplate = createColoredArmor(org.bukkit.Material.LEATHER_CHESTPLATE, color);

        if (tier.equals("LEATHER")) {
            leggings = createColoredArmor(org.bukkit.Material.LEATHER_LEGGINGS, color);
            boots = createColoredArmor(org.bukkit.Material.LEATHER_BOOTS, color);
        } else if (tier.equals("CHAIN")) {
            leggings = new ItemStack(org.bukkit.Material.CHAINMAIL_LEGGINGS);
            boots = new ItemStack(org.bukkit.Material.CHAINMAIL_BOOTS);
        } else if (tier.equals("IRON")) {
            leggings = new ItemStack(org.bukkit.Material.IRON_LEGGINGS);
            boots = new ItemStack(org.bukkit.Material.IRON_BOOTS);
        } else {
            leggings = new ItemStack(org.bukkit.Material.DIAMOND_LEGGINGS);
            boots = new ItemStack(org.bukkit.Material.DIAMOND_BOOTS);
        }

        renameItem(player, helmet);
        renameItem(player, chestplate);
        renameItem(player, leggings);
        renameItem(player, boots);

        setUnbreakable(helmet);
        setUnbreakable(chestplate);
        setUnbreakable(leggings);
        setUnbreakable(boots);

        int protLevel = team.getUpgradeLevel("protection");
        if (protLevel > 0) {
            helmet.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
            chestplate.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
            leggings.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
            boots.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
        }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        normalizeSwordInventory(player, team);
    }

    public void applyTeamUpgrades(Player player, Team team) {
        if (team == null)
            return;

        int sharpLevel = team.getUpgradeLevel("sharpness");
        if (sharpLevel > 0) {
            for (ItemStack is : player.getInventory().getContents()) {
                if (is != null && is.getType().name().endsWith("_SWORD")) {
                    is.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, sharpLevel);
                }
            }
        }

        int protLevel = team.getUpgradeLevel("protection");
        if (protLevel > 0) {
            for (ItemStack is : player.getInventory().getArmorContents()) {
                if (is != null && is.getType() != Material.AIR) {
                    is.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL, protLevel);
                }
            }
        }

        int hasteLevel = team.getUpgradeLevel("haste");
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING);
        if (hasteLevel > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING,
                    400, hasteLevel - 1, false, false));
        }
    }

    private void setUnbreakable(ItemStack item) {
        if (item == null)
            return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    private void renameItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String taggedName = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "inventory_name");
            if (taggedName != null && !taggedName.isEmpty()) {
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', taggedName));
            } else {
                meta.setDisplayName(plugin.getLanguageManager().getItemName(player.getUniqueId(), item.getType()));
            }
            item.setItemMeta(meta);
        }
    }

    private boolean hasAnySword(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getType().name().endsWith("_SWORD")) {
                return true;
            }
        }
        return false;
    }

    private void removeSpecificItem(Player player, Material material) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private int findFirstSlot(Player player, Material material) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                return i;
            }
        }
        return -1;
    }

    private org.bukkit.inventory.ItemStack createColoredArmor(org.bukkit.Material material, org.bukkit.Color color) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) item
                .getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private org.bukkit.Color getTeamColor(Team team) {
        if (team.getColor() == null)
            return org.bukkit.Color.WHITE;
        switch (team.getColor()) {
            case RED:
                return org.bukkit.Color.RED;
            case BLUE:
                return org.bukkit.Color.BLUE;
            case GREEN:
                return org.bukkit.Color.LIME;
            case YELLOW:
                return org.bukkit.Color.YELLOW;
            case AQUA:
                return org.bukkit.Color.AQUA;
            case WHITE:
                return org.bukkit.Color.WHITE;
            case LIGHT_PURPLE:
                return org.bukkit.Color.FUCHSIA;
            case GRAY:
            case DARK_GRAY:
                return org.bukkit.Color.GRAY;
            default:
                return org.bukkit.Color.WHITE;
        }
    }

    public void endGame(Arena arena, Team winner) {
        if (arena.getState() == Arena.GameState.ENDING) {
            return; // already ending, prevent duplicate calls
        }
        arena.setState(Arena.GameState.ENDING);

        String winnerText = (winner != null)
                ? (winner.getColor() != null ? winner.getColor().toString() : "§f") + winner.getName()
                : null;

        String winnerColor = (winner != null && winner.getColor() != null) ? winner.getColor().toString() : "§f";
        boolean isDraw = (winner == null);

        for (Player p : arena.getPlayers()) {
            boolean isWinner = winner != null && winner.getMembers().contains(p);

            if (isDraw) {
                plugin.sendTitle(p,
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-draw-title"),
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-draw-subtitle"),
                        10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);
                plugin.getStatsManager().addLoss(p.getUniqueId());
            } else if (isWinner) {
                plugin.sendTitle(p,
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-win-title"),
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-win-subtitle"),
                        10, 100, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                plugin.getStatsManager().addWin(p.getUniqueId());
            } else {
                plugin.sendTitle(p,
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-over-title"),
                        plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-over-subtitle")
                                .replace("%winner%", winnerText),
                        10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);
                plugin.getStatsManager().addLoss(p.getUniqueId());
            }

            p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-separator"));
            p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-title"));
            p.sendMessage(" ");
            if (isDraw) {
                p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-draw"));
            } else {
                p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-winner")
                        .replace("%color%", winnerColor).replace("%winner%", winnerText));
                p.sendMessage(" ");
                String members = winner.getMembers().stream().map(Player::getName)
                        .collect(java.util.stream.Collectors.joining(", "));
                p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-members")
                        .replace("%members%", members));
            }
            p.sendMessage(" ");

            displayTopStats(p, arena);

            p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "game-victory-separator"));
        }

        for (Player p : arena.getPlayers()) {
            plugin.getStatsManager().saveStats(p.getUniqueId());
        }

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 5 || arena.getState() != Arena.GameState.ENDING) {
                    this.cancel();
                    return;
                }
                if (winner != null && winner.getSpawnLocation() != null) {
                    spawnWinFireworks(winner.getSpawnLocation());
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                List<Player> toKick = new ArrayList<>(arena.getPlayers());
                // Also collect mid-game spectators that are in playerArenaCache but not arena.getPlayers()
                for (java.util.Map.Entry<Player, Arena> entry : new java.util.ArrayList<>(plugin.getArenaManager().getPlayerArenaCacheEntries())) {
                    if (entry.getValue() == arena && !toKick.contains(entry.getKey())) {
                        toKick.add(entry.getKey());
                    }
                }
                for (Player p : toKick) {
                    plugin.getArenaManager().leaveArena(p);
                    p.sendMessage(plugin.getLanguageManager().getMessage(p.getUniqueId(), "arena-send-lobby"));
                }

                cleanupArena(arena);
                sessionStats.remove(arena);
                plugin.getVisualizationManager().removeGeneratorVisuals(arena);
                plugin.getVisualizationManager().removeGameHolograms(arena);

                arena.reset();
                for (org.bukkit.entity.Player editor : org.bukkit.Bukkit.getOnlinePlayers()) {
                    Arena editArena = plugin.getArenaManager().getEditArena(editor);
                    if (editArena != null && editArena.getName().equals(arena.getName())) {
                        editor.sendMessage(plugin.getLanguageManager().getMessage(editor.getUniqueId(), "setup-edit-stopped-resetting")
                                .replace("%name%", arena.getName()));
                        plugin.getArenaManager().kickFromEditMode(editor);
                    }
                }
                arena.setResetting(true);
                plugin.getSignManager().updateSigns(arena);

                String worldName = arena.getWorldName();
                if (worldName != null && !worldName.isEmpty()) {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world != null) {
                        world.getWorldBorder().reset();

                        world.getEntitiesByClass(org.bukkit.entity.EnderDragon.class)
                                .forEach(org.bukkit.entity.Entity::remove);
                        world.getEntitiesByClass(org.bukkit.entity.Villager.class)
                                .forEach(org.bukkit.entity.Entity::remove);
                        world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).stream()
                                .filter(as -> !as.getScoreboardTags().isEmpty())
                                .forEach(org.bukkit.entity.Entity::remove);
                        world.getEntitiesByClass(org.bukkit.entity.IronGolem.class).stream()
                                .filter(e -> e.getScoreboardTags().contains("bw_mob"))
                                .forEach(org.bukkit.entity.Entity::remove);
                        world.getEntitiesByClass(org.bukkit.entity.Silverfish.class).stream()
                                .filter(e -> e.getScoreboardTags().contains("bw_mob"))
                                .forEach(org.bukkit.entity.Entity::remove);
                        world.getEntitiesByClass(org.bukkit.entity.Item.class)
                                .forEach(org.bukkit.entity.Entity::remove);

                        for (Player p : world.getPlayers()) {
                            Location mainLobby = getMainLobbyLocation();
                            if (mainLobby != null) {
                                p.teleport(mainLobby);
                            } else {
                                org.bukkit.World defaultWorld = org.bukkit.Bukkit.getWorlds().get(0);
                                if (defaultWorld != null) {
                                    p.teleport(defaultWorld.getSpawnLocation());
                                }
                            }
                        }

                        boolean unloaded = org.bukkit.Bukkit.unloadWorld(worldName, false);
                        if (!unloaded) {
                            plugin.getLogger().warning(
                                    "Failed to unload world: " + worldName + ". Retrying after delay...");

                            new BukkitRunnable() {
                                int attempts = 0;

                                @Override
                                public void run() {
                                    attempts++;
                                    boolean retryUnload = org.bukkit.Bukkit.unloadWorld(worldName, false);
                                    if (retryUnload) {
                                        plugin.getLogger().info("Successfully unloaded world on attempt " + attempts);
                                        this.cancel();
                                        reloadWorldAndResetArena(arena, worldName);
                                    } else if (attempts >= 5) {
                                        plugin.getLogger().severe("Failed to unload world " + worldName + " after "
                                                + attempts + " attempts. Arena reset may fail!");
                                        arena.setResetting(false);
                                        this.cancel();
                                        plugin.getSignManager().updateSigns(arena);
                                    }
                                }
                            }.runTaskTimer(plugin, 20L, 20L);
                        } else {
                            reloadWorldAndResetArena(arena, worldName);
                        }
                    } else {
                        java.io.File worldFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName);
                        if (worldFolder.exists() && worldFolder.isDirectory()) {
                            plugin.getLogger().info("World " + worldName + " not loaded, loading from disk...");
                            org.bukkit.World loadedWorld = org.bukkit.Bukkit.createWorld(
                                    new org.bukkit.WorldCreator(worldName));
                            if (loadedWorld != null) {
                                loadedWorld.setAutoSave(false);
                                updateArenaLocations(arena, loadedWorld);
                                arena.setResetting(false);
                                plugin.getSignManager().updateSigns(arena);
                                plugin.getLogger().info("Arena " + arena.getName() + " world loaded successfully.");
                            } else {
                                plugin.getLogger().severe("Failed to load world: " + worldName + ". Arena "
                                        + arena.getName() + " may be in invalid state!");
                                arena.setResetting(false);
                            }
                        } else {
                            plugin.getLogger().warning("World folder does not exist: " + worldName
                                    + ". Arena may have invalid configuration.");
                            arena.setResetting(false);
                        }
                    }
                } else {
                    plugin.getLogger().warning("Arena " + arena.getName() + " has no world assigned!");
                    arena.setResetting(false);
                }
            }
        }.runTaskLater(plugin, 200L);
    }

    public SessionStats getOrCreateSessionStats(Arena arena, UUID uuid) {
        Map<UUID, SessionStats> arenaStats = sessionStats.computeIfAbsent(arena, k -> new ConcurrentHashMap<>());
        return arenaStats.computeIfAbsent(uuid, k -> new SessionStats());
    }

    private void displayTopStats(Player player, Arena arena) {
        Map<UUID, SessionStats> stats = sessionStats.get(arena);
        if (stats == null || stats.isEmpty())
            return;

        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "game-victory-stats-header"));

        UUID topKiller = stats.entrySet().stream()
                .max(java.util.Comparator.comparingInt(e -> e.getValue().kills))
                .map(Map.Entry::getKey).orElse(null);
        if (topKiller != null && stats.get(topKiller).kills > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "game-victory-top-kills")
                    .replace("%player%", org.bukkit.Bukkit.getOfflinePlayer(topKiller).getName())
                    .replace("%amount%", String.valueOf(stats.get(topKiller).kills)));
        }

        UUID topFinalKiller = stats.entrySet().stream()
                .max(java.util.Comparator.comparingInt(e -> e.getValue().finalKills))
                .map(Map.Entry::getKey).orElse(null);
        if (topFinalKiller != null && stats.get(topFinalKiller).finalKills > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "game-victory-top-final-kills")
                    .replace("%player%", org.bukkit.Bukkit.getOfflinePlayer(topFinalKiller).getName())
                    .replace("%amount%", String.valueOf(stats.get(topFinalKiller).finalKills)));
        }

        UUID topBedBreaker = stats.entrySet().stream()
                .max(java.util.Comparator.comparingInt(e -> e.getValue().bedsBroken))
                .map(Map.Entry::getKey).orElse(null);
        if (topBedBreaker != null && stats.get(topBedBreaker).bedsBroken > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "game-victory-top-beds")
                    .replace("%player%", org.bukkit.Bukkit.getOfflinePlayer(topBedBreaker).getName())
                    .replace("%amount%", String.valueOf(stats.get(topBedBreaker).bedsBroken)));
        }
    }

    private void spawnWinFireworks(Location loc) {
        org.bukkit.entity.Firework fw = loc.getWorld().spawn(loc, org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(org.bukkit.Color.ORANGE, org.bukkit.Color.YELLOW)
                .withFade(org.bukkit.Color.WHITE)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());
        fwm.setPower(1);
        fw.setFireworkMeta(fwm);
    }

    private void removeSimilarItems(Player player, String suffix) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);
            if (invItem != null && invItem.getType().name().endsWith(suffix)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private ItemStack createTool(Player player, String type, int tier) {
        org.bukkit.Material mat = null;
        int eff = 1;

        org.bukkit.configuration.ConfigurationSection shopConfig = plugin.getLanguageManager()
                .getConfig(player.getUniqueId(), "shop");
        if (shopConfig == null)
            shopConfig = plugin.getConfigManager().getShopConfig();

        if (shopConfig != null) {
            String path = "sections.tools.items." + (type.equals("PICKAXE") ? "pickaxe" : "axe") + ".tiers." + tier;
            org.bukkit.configuration.ConfigurationSection tierData = shopConfig.getConfigurationSection(path);
            if (tierData != null) {
                String matName = tierData.getString("material");
                if (matName != null) {
                    mat = org.bukkit.Material.matchMaterial(matName);
                    if (mat == null) {
                        com.cryptomorin.xseries.XMaterial xmat = com.cryptomorin.xseries.XMaterial
                                .matchXMaterial(matName).orElse(null);
                        if (xmat != null)
                            mat = xmat.parseMaterial();
                    }
                }

                org.bukkit.configuration.ConfigurationSection enchants = tierData
                        .getConfigurationSection("enchantments");
                if (enchants != null) {
                    eff = enchants.getInt("DIG_SPEED", 1);
                }
            }
        }

        if (mat == null) {
            if (type.equals("PICKAXE")) {
                switch (tier) {
                    case 1:
                        mat = com.cryptomorin.xseries.XMaterial.WOODEN_PICKAXE.parseMaterial();
                        break;
                    case 2:
                        mat = org.bukkit.Material.STONE_PICKAXE;
                        break;
                    case 3:
                        mat = org.bukkit.Material.IRON_PICKAXE;
                        break;
                    default:
                        mat = org.bukkit.Material.DIAMOND_PICKAXE;
                        break;
                }
                eff = tier == 3 ? 2 : (tier == 4 ? 3 : 1);
            } else {
                switch (tier) {
                    case 1:
                        mat = com.cryptomorin.xseries.XMaterial.WOODEN_AXE.parseMaterial();
                        break;
                    case 2:
                        mat = org.bukkit.Material.STONE_AXE;
                        break;
                    case 3:
                        mat = org.bukkit.Material.IRON_AXE;
                        break;
                    default:
                        mat = org.bukkit.Material.DIAMOND_AXE;
                        break;
                }
                eff = tier == 3 ? 2 : (tier == 4 ? 3 : 1);
            }
        }

        ItemStack item = new ItemStack(mat);
        renameItem(player, item);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DIG_SPEED, eff, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void applyPvpSettings(Player player, Arena arena) {
        try {
            applyPvpSettingsInner(player, arena);
        } catch (Throwable ignored) {
            // Attributes don't exist on older versions (e.g. 1.8.8), safe to ignore
        }
    }

    private void applyPvpSettingsInner(Player player, Arena arena) {
        org.bukkit.attribute.AttributeInstance attackSpeed = player
                .getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED);
        if (attackSpeed == null)
            return;

        if (arena != null) {
            if (arena.getPvpMode() == Arena.PvpMode.LEGACY_1_8) {
                attackSpeed.setBaseValue(1024.0);

                player.getInventory().remove(org.bukkit.Material.SHIELD);

                try {
                    org.bukkit.attribute.AttributeInstance sweepAttr = player
                            .getAttribute(org.bukkit.attribute.Attribute.valueOf("SWEEPING_DAMAGE_RATIO"));
                    if (sweepAttr != null) {
                        sweepAttr.setBaseValue(0.0);
                    }
                } catch (Throwable ignored) {
                }
            } else {
                attackSpeed.setBaseValue(4.0);
            }
        } else {
            attackSpeed.setBaseValue(4.0);
        }
    }

    private void updateArenaLocations(Arena arena, org.bukkit.World newWorld) {
        if (arena.getLobbyLocation() != null) {
            arena.setLobbyLocation(updateLocation(arena.getLobbyLocation(), newWorld));
        }
        if (arena.getPos1() != null) {
            arena.setPos1(updateLocation(arena.getPos1(), newWorld));
        }
        if (arena.getPos2() != null) {
            arena.setPos2(updateLocation(arena.getPos2(), newWorld));
        }

        java.util.List<org.bukkit.Location> updatedDiamond = new java.util.ArrayList<>();
        for (org.bukkit.Location loc : arena.getDiamondGenerators()) {
            updatedDiamond.add(updateLocation(loc, newWorld));
        }
        arena.getDiamondGenerators().clear();
        arena.getDiamondGenerators().addAll(updatedDiamond);

        java.util.List<org.bukkit.Location> updatedEmerald = new java.util.ArrayList<>();
        for (org.bukkit.Location loc : arena.getEmeraldGenerators()) {
            updatedEmerald.add(updateLocation(loc, newWorld));
        }
        arena.getEmeraldGenerators().clear();
        arena.getEmeraldGenerators().addAll(updatedEmerald);

        for (Team team : arena.getTeams()) {
            if (team.getSpawnLocation() != null) {
                team.setSpawnLocation(updateLocation(team.getSpawnLocation(), newWorld));
            }
            if (team.getBedLocation() != null) {
                team.setBedLocation(updateLocation(team.getBedLocation(), newWorld));
            }
            if (team.getShopLocation() != null) {
                team.setShopLocation(updateLocation(team.getShopLocation(), newWorld));
            }
            if (team.getUpgradeLocation() != null) {
                team.setUpgradeLocation(updateLocation(team.getUpgradeLocation(), newWorld));
            }
            if (team.getBasePos1() != null) {
                team.setBasePos1(updateLocation(team.getBasePos1(), newWorld));
            }
            if (team.getBasePos2() != null) {
                team.setBasePos2(updateLocation(team.getBasePos2(), newWorld));
            }

            java.util.List<org.bukkit.Location> updatedTeamGens = new java.util.ArrayList<>();
            for (org.bukkit.Location loc : team.getGenerators()) {
                updatedTeamGens.add(updateLocation(loc, newWorld));
            }
            team.getGenerators().clear();
            team.getGenerators().addAll(updatedTeamGens);
        }
    }

    private org.bukkit.Location updateLocation(org.bukkit.Location old, org.bukkit.World newWorld) {
        if (old == null)
            return null;
        return new org.bukkit.Location(newWorld, old.getX(), old.getY(), old.getZ(), old.getYaw(), old.getPitch());
    }

    private void reloadWorldAndResetArena(Arena arena, String worldName) {
        // Guard: world should already be unloaded at this point
        org.bukkit.World existingWorld = org.bukkit.Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            plugin.getLogger().warning(
                    "World " + worldName + " is still loaded, cannot reload. Trying to update references...");
            updateArenaLocations(arena, existingWorld);
            arena.setResetting(false);
            plugin.getSignManager().updateSigns(arena);
            return;
        }

        // Restore backup on an async thread (file I/O), then load the world back on the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                if (me.horiciastko.bedwars.utils.WorldBackupUtils.hasBackup(worldName)) {
                    boolean restored = me.horiciastko.bedwars.utils.WorldBackupUtils.restoreBackup(worldName);
                    if (restored) {
                        plugin.getLogger().info("Restored world backup for arena: " + arena.getName());
                    } else {
                        plugin.getLogger()
                                .warning("[BedWars] Failed to restore backup for " + worldName + ", using existing folder.");
                    }
                } else {
                    plugin.getLogger()
                            .warning("[BedWars] No backup found for " + worldName + ". Loading existing folder as-is.");
                }

                boolean folderExists = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName).exists();
                if (!folderExists) {
                    plugin.getLogger().severe("World folder missing and no backup available: " + worldName);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            arena.setResetting(false);
                            plugin.getSignManager().updateSigns(arena);
                        }
                    }.runTask(plugin);
                    return;
                }

                // Back on the main thread to load the world
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            org.bukkit.World reloadedWorld = org.bukkit.Bukkit.createWorld(
                                    new org.bukkit.WorldCreator(worldName));

                            if (reloadedWorld == null) {
                                plugin.getLogger().severe(
                                        "[BedWars] Failed to reload world: " + worldName + ". Arena may be in invalid state!");
                                arena.setResetting(false);
                                plugin.getSignManager().updateSigns(arena);
                                return;
                            }

                            reloadedWorld.setAutoSave(false);
                            reloadedWorld.getEntitiesByClass(org.bukkit.entity.Item.class)
                                    .forEach(org.bukkit.entity.Entity::remove);
                            prepareWorldRules(reloadedWorld);

                            updateArenaLocations(arena, reloadedWorld);

                            if (arena.getLobbyLocation() == null || arena.getLobbyLocation().getWorld() == null) {
                                plugin.getLogger().warning(
                                        "Arena " + arena.getName() + " lobby location is invalid after world reload!");
                            }

                            arena.setResetting(false);
                            plugin.getSignManager().updateSigns(arena);
                            plugin.getLogger().info("Arena " + arena.getName() + " world reloaded successfully.");
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error reloading world " + worldName + ": " + e.getMessage());
                            e.printStackTrace();
                            arena.setResetting(false);
                            plugin.getSignManager().updateSigns(arena);
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public Location getMainLobbyLocation() {
        String lobbyStr = plugin.getDatabaseManager().getSetting("main_lobby");
        if (lobbyStr != null && !lobbyStr.isEmpty()) {
            return me.horiciastko.bedwars.utils.SerializationUtils.stringToLocation(lobbyStr);
        }
        return null;
    }

    public void setMainLobbyLocation(Location loc) {
        if (loc == null)
            return;
        String lobbyStr = me.horiciastko.bedwars.utils.SerializationUtils.locationToString(loc);
        plugin.getDatabaseManager().setSetting("main_lobby", lobbyStr);
        prepareWorldRules(loc.getWorld());
    }

    public void prepareWorldRules(org.bukkit.World world) {
        if (world == null)
            return;

        world.setDifficulty(org.bukkit.Difficulty.EASY);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("showDeathMessages", "false");

        try {
            world.setGameRuleValue("announceAdvancements", "false");
        } catch (Exception ignored) {
        }
    }
}
