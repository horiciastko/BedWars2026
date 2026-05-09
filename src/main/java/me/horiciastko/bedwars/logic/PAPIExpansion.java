package me.horiciastko.bedwars.logic;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.PlayerStats;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {

    private final BedWars plugin;

    public PAPIExpansion(BedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bw";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Horiciastko";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        String lowered = params.toLowerCase();

        java.util.regex.Matcher topMatcher = java.util.regex.Pattern
                .compile("^(wins|final_kills|beds_broken)_(daily|weekly|monthly|alltime)_top-(\\d+)(?:_(value))?$")
                .matcher(lowered);
        if (topMatcher.matches()) {
            String statType = topMatcher.group(1);
            String period = topMatcher.group(2);
            int rank;
            try {
                rank = Integer.parseInt(topMatcher.group(3));
            } catch (NumberFormatException ex) {
                return "0";
            }

            boolean valueMode = "value".equals(topMatcher.group(4));
            DatabaseManager.LeaderboardEntry entry;
            if ("alltime".equals(period)) {
                entry = plugin.getDatabaseManager().getTopLifetimeStatEntry(statType, rank);
            } else {
                long since = getPeriodStartMillis(period);
                entry = plugin.getDatabaseManager().getTopStatEntry(statType, since, rank);
            }

            if (entry == null) {
                return valueMode ? "0" : "None";
            }

            return valueMode ? String.valueOf(entry.getValue()) : entry.getName();
        }

        if (player == null) {
            return "";
        }

        PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId());
        Arena arena = plugin.getArenaManager().getPlayerArena(player);

        switch (lowered) {
            case "wins":
                return stats != null ? String.valueOf(stats.getWins()) : "0";
            case "kills":
                return stats != null ? String.valueOf(stats.getKills()) : "0";
            case "final_kills":
                return stats != null ? String.valueOf(stats.getFinalKills()) : "0";
            case "deaths":
                return stats != null ? String.valueOf(stats.getDeaths()) : "0";
            case "beds_broken":
                return stats != null ? String.valueOf(stats.getBedsBroken()) : "0";
            case "level":
                return stats != null ? String.valueOf(stats.getLevel()) : "1";
            case "coins":
                return stats != null ? String.valueOf(stats.getCoins()) : "0";
            case "arena":
                return arena != null ? arena.getName() : "None";
            case "team":
                if (arena != null) {
                    Team team = plugin.getGameManager().getPlayerTeam(arena, player);
                    return team != null ? team.getName() : "None";
                }
                return "None";
            case "team_color":
                if (arena != null) {
                    Team team = plugin.getGameManager().getPlayerTeam(arena, player);
                    return team != null ? team.getColor().toString() : "§7";
                }
                return "§7";
            case "winrate":
                if (stats == null) return "0.00";
                int totalGames = Math.max(1, stats.getWins() + (stats.getKills() / Math.max(1, stats.getFinalKills())));
                return String.format("%.2f", (stats.getWins() * 100.0 / totalGames)) + "%";
            case "kd_ratio":
                if (stats == null) return "0.00";
                return String.format("%.2f", (stats.getKills() * 1.0 / Math.max(1, stats.getDeaths())));
            case "fk_ratio":
                if (stats == null) return "0.00";
                return String.format("%.2f", (stats.getFinalKills() * 1.0 / Math.max(1, stats.getDeaths())));
            case "rank":
                if (stats == null) return "§7Beginner";
                return stats.getRank();
            case "progress_bar":
                if (stats == null) return "§7■■■■■■■■■■";
                return stats.getProgressBar();
            case "progress_percent":
                if (stats == null) return "0";
                return String.format("%.1f", stats.getProgressPercent()) + "%";
            case "current_level_xp":
                if (stats == null) return "0";
                return String.valueOf(stats.getCurrentLevelXp());
            case "required_xp_next_level":
                if (stats == null) return "0";
                return String.valueOf(stats.getRequiredXpForNextLevel());
            case "kills_per_bedbreak":
                if (stats == null || stats.getBedsBroken() == 0) return "0.00";
                return String.format("%.2f", (stats.getKills() * 1.0 / stats.getBedsBroken()));
            case "total_stats":
                if (stats == null) return "0";
                return String.valueOf(stats.getWins() + stats.getKills() + stats.getFinalKills() + stats.getBedsBroken());
            case "beds_per_game":
                if (stats == null) return "0.00";
                int estimatedGames = Math.max(1, stats.getWins() + (stats.getKills() / Math.max(1, stats.getFinalKills())));
                return String.format("%.2f", (stats.getBedsBroken() * 1.0 / estimatedGames));
        }

        return null;
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
}
