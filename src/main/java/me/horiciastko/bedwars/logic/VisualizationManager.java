package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import me.horiciastko.bedwars.utils.SerializationUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class VisualizationManager {

    public static class LeaderboardHologram {
        private final String id;
        private final Location location;
        private final String statType;
        private final String period;
        private final int lines;
        private final String title;

        public LeaderboardHologram(String id, Location location, String statType, String period, int lines, String title) {
            this.id = id;
            this.location = location;
            this.statType = statType;
            this.period = period;
            this.lines = lines;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public Location getLocation() {
            return location;
        }

        public String getStatType() {
            return statType;
        }

        public String getPeriod() {
            return period;
        }

        public int getLines() {
            return lines;
        }

        public String getTitle() {
            return title;
        }
    }

    private final Map<UUID, List<ArmorStand>> activeHolograms = new ConcurrentHashMap<>();
    private final Map<String, List<ArmorStand>> activeGameHolograms = new ConcurrentHashMap<>();
    private final Map<String, List<ArmorStand>> generatorVisuals = new ConcurrentHashMap<>();
    private final Map<String, LeaderboardHologram> leaderboardDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<ArmorStand>> activeLeaderboardHolograms = new ConcurrentHashMap<>();
    private final BedWars plugin;
    private static final String LEADERBOARD_SETTINGS_KEY = "leaderboard_holograms";

    public VisualizationManager(BedWars plugin) {
        this.plugin = plugin;
        loadLeaderboardHolograms();
        startLeaderboardRefreshTask();
    }

    public String createLeaderboardHologram(Location location, String statType, String period, int lines, String customTitle) {
        if (location == null || location.getWorld() == null)
            return null;

        String normalizedStat = normalizeStatType(statType);
        String normalizedPeriod = normalizePeriod(period);
        if (normalizedStat == null || normalizedPeriod == null)
            return null;

        int safeLines = Math.max(1, Math.min(15, lines));
        String id = UUID.randomUUID().toString().substring(0, 8);
        Location base = location.clone();
        String title = customTitle == null ? "" : customTitle;

        leaderboardDefinitions.put(id,
                new LeaderboardHologram(id, base, normalizedStat, normalizedPeriod, safeLines, title));
        saveLeaderboardHolograms();
        refreshLeaderboardHolograms();
        return id;
    }

    public boolean removeLeaderboardHologram(String id) {
        if (id == null)
            return false;
        LeaderboardHologram removed = leaderboardDefinitions.remove(id);
        if (removed == null)
            return false;

        List<ArmorStand> stands = activeLeaderboardHolograms.remove(id);
        if (stands != null) {
            stands.forEach(ArmorStand::remove);
        }

        saveLeaderboardHolograms();
        return true;
    }

    public String removeNearestLeaderboardHologram(Location reference, double maxDistance) {
        if (reference == null || reference.getWorld() == null)
            return null;

        double maxDistanceSquared = maxDistance * maxDistance;
        String nearestId = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LeaderboardHologram hologram : leaderboardDefinitions.values()) {
            Location loc = hologram.getLocation();
            if (loc == null || loc.getWorld() == null)
                continue;
            if (!loc.getWorld().getUID().equals(reference.getWorld().getUID()))
                continue;

            double distance = loc.distanceSquared(reference);
            if (distance <= maxDistanceSquared && distance < nearestDistance) {
                nearestDistance = distance;
                nearestId = hologram.getId();
            }
        }

        if (nearestId != null && removeLeaderboardHologram(nearestId)) {
            return nearestId;
        }
        return null;
    }

    public List<LeaderboardHologram> getLeaderboardHolograms() {
        return new ArrayList<>(leaderboardDefinitions.values());
    }

    public void refreshLeaderboardHolograms() {
        for (List<ArmorStand> stands : activeLeaderboardHolograms.values()) {
            stands.forEach(ArmorStand::remove);
        }
        activeLeaderboardHolograms.clear();

        for (LeaderboardHologram hologram : leaderboardDefinitions.values()) {
            spawnSingleLeaderboardHologram(hologram);
        }
    }

    private void spawnSingleLeaderboardHologram(LeaderboardHologram hologram) {
        if (hologram == null || !isWorldLoaded(hologram.getLocation()))
            return;

        List<String> lines = buildLeaderboardLines(hologram);
        if (lines.isEmpty())
            return;

        List<ArmorStand> stands = new ArrayList<>();
        Location base = hologram.getLocation().clone().add(0.5, 2.6, 0.5);
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = createLeaderboardArmorStand(base.clone().add(0, -(i * 0.28), 0), lines.get(i), hologram.getId());
            if (stand != null) {
                stands.add(stand);
            }
        }
        activeLeaderboardHolograms.put(hologram.getId(), stands);
    }

    private List<String> buildLeaderboardLines(LeaderboardHologram hologram) {
        List<String> lines = new ArrayList<>();
        String header = (hologram.getTitle() != null && !hologram.getTitle().trim().isEmpty())
                ? ChatColor.translateAlternateColorCodes('&', hologram.getTitle())
                : "§6§l" + getStatDisplayName(hologram.getStatType()) + " §eLEADERBOARD";
        lines.add(header);
        lines.add("§7Period: §f" + getPeriodDisplayName(hologram.getPeriod()));
        lines.add("§8----------------------");

        for (int rank = 1; rank <= hologram.getLines(); rank++) {
            DatabaseManager.LeaderboardEntry entry;
            if ("alltime".equals(hologram.getPeriod())) {
                entry = plugin.getDatabaseManager().getTopLifetimeStatEntry(hologram.getStatType(), rank);
            } else {
                entry = plugin.getDatabaseManager().getTopStatEntry(hologram.getStatType(), getPeriodStartMillis(hologram.getPeriod()), rank);
            }

            if (entry == null) {
                lines.add("§7#" + rank + " §8- §7Brak danych");
            } else {
                lines.add("§f#" + rank + " §e" + entry.getName() + " §8- §b" + entry.getValue());
            }
        }

        return lines;
    }

    private ArmorStand createLeaderboardArmorStand(Location loc, String text, String id) {
        if (!isWorldLoaded(loc))
            return null;

        String idTag = "bw_lb_id_" + id;
        return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.addScoreboardTag("bw_lb_hologram");
            stand.addScoreboardTag(idTag);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
        });
    }

    private void loadLeaderboardHolograms() {
        leaderboardDefinitions.clear();
        String raw = plugin.getDatabaseManager().getSetting(LEADERBOARD_SETTINGS_KEY);
        if (raw == null || raw.trim().isEmpty())
            return;

        String[] records = raw.split("\\n");
        for (String record : records) {
            if (record.trim().isEmpty())
                continue;
            String[] part = record.split("\\|", 6);
            if (part.length < 6)
                continue;

            Location location = SerializationUtils.stringToLocation(part[1]);
            if (location == null)
                continue;

            String stat = normalizeStatType(part[2]);
            String period = normalizePeriod(part[3]);
            int lines;
            try {
                lines = Integer.parseInt(part[4]);
            } catch (NumberFormatException ex) {
                lines = 10;
            }

            if (stat == null || period == null)
                continue;

            String title;
            try {
                title = new String(Base64.getDecoder().decode(part[5]), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                title = "";
            }

            leaderboardDefinitions.put(part[0], new LeaderboardHologram(part[0], location, stat, period, Math.max(1, Math.min(15, lines)), title));
        }
    }

    private void saveLeaderboardHolograms() {
        StringBuilder builder = new StringBuilder();
        for (LeaderboardHologram hologram : leaderboardDefinitions.values()) {
            String encodedTitle = Base64.getEncoder().encodeToString(hologram.getTitle().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String location = SerializationUtils.locationToString(hologram.getLocation());
            builder.append(hologram.getId()).append("|")
                    .append(location).append("|")
                    .append(hologram.getStatType()).append("|")
                    .append(hologram.getPeriod()).append("|")
                    .append(hologram.getLines()).append("|")
                    .append(encodedTitle)
                    .append("\n");
        }
        plugin.getDatabaseManager().setSetting(LEADERBOARD_SETTINGS_KEY, builder.toString());
    }

    private void startLeaderboardRefreshTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    cancel();
                    return;
                }
                refreshLeaderboardHolograms();
            }
        }.runTaskTimer(plugin, 40L, 200L);
    }

    private String normalizeStatType(String statType) {
        if (statType == null)
            return null;
        String lowered = statType.toLowerCase(Locale.ROOT);
        if (lowered.equals("wins") || lowered.equals("final_kills") || lowered.equals("beds_broken")) {
            return lowered;
        }
        return null;
    }

    private String normalizePeriod(String period) {
        if (period == null)
            return null;
        String lowered = period.toLowerCase(Locale.ROOT);
        if (lowered.equals("daily") || lowered.equals("weekly") || lowered.equals("monthly") || lowered.equals("alltime")) {
            return lowered;
        }
        return null;
    }

    private String getStatDisplayName(String statType) {
        switch (statType) {
            case "wins":
                return "Wins";
            case "final_kills":
                return "Final Kills";
            case "beds_broken":
                return "Beds Broken";
            default:
                return "Stats";
        }
    }

    private String getPeriodDisplayName(String period) {
        switch (period) {
            case "daily":
                return "Daily";
            case "weekly":
                return "Weekly";
            case "monthly":
                return "Monthly";
            case "alltime":
            default:
                return "All-Time";
        }
    }

    private long getPeriodStartMillis(String period) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        switch (period) {
            case "daily":
                return now.toLocalDate().atStartOfDay(now.getZone()).toInstant().toEpochMilli();
            case "weekly":
                java.time.ZonedDateTime weekStart = now
                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        .toLocalDate()
                        .atStartOfDay(now.getZone());
                return weekStart.toInstant().toEpochMilli();
            case "monthly":
                java.time.ZonedDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone());
                return monthStart.toInstant().toEpochMilli();
            case "alltime":
            default:
                return 0L;
        }
    }

    public void showHolograms(Player player, Arena arena) {
        if (player == null || !player.isOnline())
            return;

        hideHolograms(player);

        if (arena == null)
            return;
        if (arena.getWorldName() == null)
            return;

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName());
        if (world == null)
            return;

        List<ArmorStand> holograms = new ArrayList<>();
        UUID uuid = player.getUniqueId();

        addHologram(holograms, arena.getLobbyLocation(), "§e§lWAITING LOBBY", uuid);
        addHologram(holograms, arena.getLobbyPos1(), "§c§lLOBBY REMOVE POS 1", uuid);
        addHologram(holograms, arena.getLobbyPos2(), "§c§lLOBBY REMOVE POS 2", uuid);

        for (Location loc : arena.getDiamondGenerators()) {
            addHologram(holograms, loc, "§b§lDIAMOND GENERATOR", uuid);
        }
        for (Location loc : arena.getEmeraldGenerators()) {
            addHologram(holograms, loc, "§2§lEMERALD GENERATOR", uuid);
        }

        for (Team team : arena.getTeams()) {
            ChatColor color = team.getColor();
            String prefix = (color != null ? color : ChatColor.WHITE) + "§l" + team.getName().toUpperCase();

            addHologram(holograms, team.getSpawnLocation(), prefix + " SPAWN", uuid);
            addHologram(holograms, team.getBedLocation(), prefix + " BED", uuid);
            addHologram(holograms, team.getShopLocation(), prefix + " SHOP", uuid);
            addHologram(holograms, team.getUpgradeLocation(), prefix + " UPGRADES", uuid);

            for (Location loc : team.getGenerators()) {
                addHologram(holograms, loc, prefix + " GENERATOR", uuid);
            }
        }

        activeHolograms.put(uuid, holograms);
    }

    private void addHologram(List<ArmorStand> list, Location loc, String text, UUID playerUuid) {
        if (!isWorldLoaded(loc))
            return;

        ArmorStand stand = createArmorStand(loc, text, playerUuid);
        if (stand != null) {
            list.add(stand);
        }
    }

    public void hideHolograms(Player player) {
        if (player == null)
            return;
        hideHolograms(player.getUniqueId());
    }

    public void hideHolograms(UUID uuid) {
        List<ArmorStand> holograms = activeHolograms.remove(uuid);
        if (holograms != null) {
            for (ArmorStand stand : holograms) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
            holograms.clear();
        }

        String playerTag = "bw_p_" + uuid.toString().substring(0, 8);
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getScoreboardTags().contains("bw_hologram")
                        && entity.getScoreboardTags().contains(playerTag)) {
                    entity.remove();
                }
            }
        }
    }

    public void refreshHologramsForArena(Arena arena) {
        if (arena == null)
            return;
        for (UUID uuid : new ArrayList<>(activeHolograms.keySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Arena session = BedWars.getInstance().getArenaManager().getEditArena(player);
                if (session != null && session.getName().equalsIgnoreCase(arena.getName())) {
                    showHolograms(player, session);
                }
            }
        }
    }

    private ArmorStand createArmorStand(Location loc, String text, UUID playerUuid) {
        Location displayLoc = loc.clone();

        displayLoc = displayLoc.getBlock().getLocation().add(0.5, 0, 0.5);
        if (text.contains("GENERATOR")) {
            displayLoc.add(0, 3.8, 0);
        } else {
            displayLoc.add(0, 0.6, 0);
        }

        String playerTag = "bw_p_" + playerUuid.toString().substring(0, 8);

        return (ArmorStand) displayLoc.getWorld().spawn(displayLoc, ArmorStand.class, stand -> {
            stand.addScoreboardTag("bw_hologram");
            stand.addScoreboardTag(playerTag);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
        });
    }

    public void spawnGameHolograms(Arena arena) {
        if (!plugin.getConfig().getBoolean("holographic-beds.enabled", true))
            return;

        removeGameHolograms(arena);
        List<ArmorStand> holograms = new ArrayList<>();

        String teamLine1 = plugin.getLanguageManager().getMessage(null, "hologram-team-line1");
        String teamLine2 = plugin.getLanguageManager().getMessage(null, "hologram-team-line2");
        String elimLine1 = plugin.getLanguageManager().getMessage(null, "hologram-eliminated-line1");

        String subtitleAlive = plugin.getLanguageManager().getMessage(null, "hologram-bed-subtitle-alive");
        String subtitleBroken = plugin.getLanguageManager().getMessage(null, "hologram-bed-subtitle-broken");
        String aliveSymbol = plugin.getLanguageManager().getMessage(null, "hologram-bed-status-alive");
        String destSymbol = plugin.getLanguageManager().getMessage(null, "hologram-bed-status-destroyed");

        for (Team team : arena.getTeams()) {
            if (team.getBedLocation() == null)
                continue;

            String teamColorStr = (team.getColor() != null ? team.getColor().toString() : "§f");

            if (team.isEliminated()) {
                String line = elimLine1.replace("%team%", team.getDisplayName())
                        .replace("%teamColor%", teamColorStr);
                ArmorStand as = createGameArmorStand(team.getBedLocation(), line);
                if (as != null)
                    holograms.add(as);
                continue;
            }

            String status = team.isBedBroken() ? destSymbol : aliveSymbol;
            String subtitle = team.isBedBroken() ? subtitleBroken : subtitleAlive;

            String l1 = teamLine1.replace("%status%", status)
                    .replace("%teamColor%", teamColorStr)
                    .replace("%team%", team.getDisplayName());

            String l2 = teamLine2.replace("%subtitle%", subtitle)
                    .replace("%teamColor%", teamColorStr)
                    .replace("%team%", team.getDisplayName());

            ArmorStand as1 = createGameArmorStand(team.getBedLocation(), l1);
            ArmorStand as2 = createGameArmorStand(team.getBedLocation().clone().add(0, -0.25, 0), l2);

            if (as1 != null)
                holograms.add(as1);
            if (as2 != null)
                holograms.add(as2);
        }

        activeGameHolograms.put(arena.getName(), holograms);
    }

    public void removeGameHolograms(Arena arena) {
        List<ArmorStand> stands = activeGameHolograms.remove(arena.getName());
        if (stands != null) {
            stands.forEach(ArmorStand::remove);
        }
    }

    public void spawnGeneratorVisuals(Arena arena) {
        removeGeneratorVisuals(arena);
        List<ArmorStand> stands = new ArrayList<>();
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGeneratorConfig();

        String dName = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                config.getString("global_generators.diamond.name", "§b§lDIAMOND"));
        org.bukkit.Material dBlock = com.cryptomorin.xseries.XMaterial
                .matchXMaterial(config.getString("global_generators.diamond.block", "DIAMOND_BLOCK")).get()
                .parseMaterial();
        for (Location loc : arena.getDiamondGenerators()) {
            stands.addAll(createGeneratorVisual(loc, dName, dBlock));
        }

        String eName = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                config.getString("global_generators.emerald.name", "§2§lEMERALD"));
        org.bukkit.Material eBlock = com.cryptomorin.xseries.XMaterial
                .matchXMaterial(config.getString("global_generators.emerald.block", "EMERALD_BLOCK")).get()
                .parseMaterial();
        for (Location loc : arena.getEmeraldGenerators()) {
            stands.addAll(createGeneratorVisual(loc, eName, eBlock));
        }

        generatorVisuals.put(arena.getName(), stands);
    }

    private List<ArmorStand> createGeneratorVisual(Location loc, String name, org.bukkit.Material block) {
        List<ArmorStand> list = new ArrayList<>();
        if (!isWorldLoaded(loc))
            return list;
        Location base = loc.getBlock().getLocation().add(0.5, 0, 0.5);

        ArmorStand head = (ArmorStand) base.getWorld().spawn(base.clone().add(0, 2.3, 0), ArmorStand.class,
                stand -> {
                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setSmall(true);
                    stand.setMarker(true);
                    stand.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(block));
                    stand.addScoreboardTag("bw_gen_visual");
                });
        list.add(head);

        ArmorStand hName = (ArmorStand) base.getWorld().spawn(base.clone().add(0, 3.8, 0), ArmorStand.class,
                stand -> {
                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setMarker(true);
                    stand.setCustomName(name);
                    stand.setCustomNameVisible(true);
                    stand.addScoreboardTag("bw_gen_hologram");
                });
        list.add(hName);

        ArmorStand hTimer = (ArmorStand) base.getWorld().spawn(base.clone().add(0, 3.5, 0), ArmorStand.class,
                stand -> {
                    stand.setVisible(false);
                    stand.setGravity(false);
                    stand.setMarker(true);
                    stand.setCustomName("§eSpawning in §c...");
                    stand.setCustomNameVisible(true);
                    stand.addScoreboardTag("bw_gen_timer");
                });
        list.add(hTimer);

        return list;
    }

    public void removeGeneratorVisuals(Arena arena) {
        List<ArmorStand> stands = generatorVisuals.remove(arena.getName());
        if (stands != null) {
            stands.forEach(ArmorStand::remove);
        }
    }

    public List<ArmorStand> getArenaGeneratorVisuals(String arenaName) {
        return generatorVisuals.getOrDefault(arenaName, Collections.emptyList());
    }

    private ArmorStand createGameArmorStand(Location loc, String text) {
        if (!isWorldLoaded(loc))
            return null;

        Location displayLoc = loc.clone();
        if (displayLoc.getX() == displayLoc.getBlockX() && displayLoc.getZ() == displayLoc.getBlockZ()) {
            displayLoc.add(0.5, 0, 0.5);
        }
        displayLoc.add(0, 1.5, 0);

        return (ArmorStand) displayLoc.getWorld().spawn(displayLoc, ArmorStand.class, stand -> {
            stand.addScoreboardTag("bw_game_hologram");
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
        });
    }

    public void clearAll() {
        for (UUID uuid : new ArrayList<>(activeHolograms.keySet())) {
            hideHolograms(uuid);
        }
        for (String arenaName : new ArrayList<>(activeGameHolograms.keySet())) {
            List<ArmorStand> stands = activeGameHolograms.remove(arenaName);
            if (stands != null)
                stands.forEach(ArmorStand::remove);
        }
        for (String id : new ArrayList<>(activeLeaderboardHolograms.keySet())) {
            List<ArmorStand> stands = activeLeaderboardHolograms.remove(id);
            if (stands != null)
                stands.forEach(ArmorStand::remove);
        }

        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getScoreboardTags().contains("bw_hologram")
                        || entity.getScoreboardTags().contains("bw_game_hologram")
                        || entity.getScoreboardTags().contains("bw_gen_visual")
                        || entity.getScoreboardTags().contains("bw_gen_hologram")
                        || entity.getScoreboardTags().contains("bw_gen_timer")
                        || entity.getScoreboardTags().contains("bw_lb_hologram")) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isWorldLoaded(Location loc) {
        if (loc == null)
            return false;
        try {
            return loc.getWorld() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
