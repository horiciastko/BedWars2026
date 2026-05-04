package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class ScoreboardManager {

    private final BedWars plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> lastLines = new ConcurrentHashMap<>();
    private final Set<String> hiddenNames = ConcurrentHashMap.newKeySet();
    private DateTimeFormatter dateFormatter;

    public void hideName(String name) {
        if (name != null) {
            hiddenNames.add(name);
        }
    }

    public ScoreboardManager(BedWars plugin) {
        this.plugin = plugin;
        reloadConfig();
        startTask();
    }

    public void reloadConfig() {
        this.dateFormatter = DateTimeFormatter
                .ofPattern(plugin.getConfig().getString("scoreboard.date-format", "dd/MM/yy"));
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 20L, 20L);
    }

    public void updateScoreboard(Player player) {
        Arena arena = plugin.getArenaManager().getPlayerArena(player);

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective obj = board.getObjective("bw_board");
        if (obj == null) {
            String title = plugin.getLanguageManager().getMessage(player.getUniqueId(), "scoreboard-title");
            obj = board.registerNewObjective("bw_board", "dummy");
            obj.setDisplayName(color(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        org.bukkit.scoreboard.Team npcTeam = board.getTeam("bw_npcs");
        if (npcTeam == null) {
            npcTeam = board.registerNewTeam("bw_npcs");
            npcTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            npcTeam.addEntry("");
        }
        for (String hidden : hiddenNames) {
            if (!npcTeam.hasEntry(hidden)) {
                npcTeam.addEntry(hidden);
            }
        }

        List<String> linesTemplate;
        Arena editArena = plugin.getArenaManager().getEditArena(player);

        if (arena == null) {
            if (editArena != null) {
                linesTemplate = new ArrayList<>();
                linesTemplate.add("§7" + LocalDateTime.now().format(dateFormatter));
                linesTemplate.add(" ");
                linesTemplate.add("§fArena: §a" + editArena.getName());
                linesTemplate
                        .add("§fWorld: §e" + (editArena.getWorldName() != null ? editArena.getWorldName() : "§cNone"));
                linesTemplate.add("§fGroup: §b" + (editArena.getGroup() != null ? editArena.getGroup() : "Default"));
                linesTemplate.add(" ");
                linesTemplate.add("§fMax Players: §a" + editArena.getMaxPlayers());
                linesTemplate.add("§fMin Players: §a" + editArena.getMinPlayers());
                linesTemplate.add(" ");
                linesTemplate.add("%server_ip%");
            } else {
                linesTemplate = plugin.getLanguageManager().getMessageList(player.getUniqueId(),
                        "scoreboard-lobby-lines");
            }
        } else if (arena.getState() == Arena.GameState.WAITING || arena.getState() == Arena.GameState.STARTING) {
            linesTemplate = plugin.getLanguageManager().getMessageList(player.getUniqueId(),
                    "scoreboard-waiting-lines");
        } else {
            linesTemplate = plugin.getLanguageManager().getMessageList(player.getUniqueId(), "scoreboard-ingame-lines");
        }

        if (linesTemplate.isEmpty() || linesTemplate.get(0).startsWith("§cMissing")) {
            String state = (arena == null) ? "lobby"
                    : (arena.getState() == Arena.GameState.WAITING || arena.getState() == Arena.GameState.STARTING)
                            ? "waiting"
                            : "ingame";
            linesTemplate = plugin.getConfig().getStringList("scoreboard." + state + ".lines");
        }

        List<String> currentLines = new ArrayList<>();
        for (String line : linesTemplate) {
            if (line.contains("%teams_status%")) {
                currentLines.addAll(formatTeamsStatus(player, arena));
            } else {
                currentLines.add(replacePlaceholders(line, player, editArena != null ? editArena : arena));
            }
        }

        List<String> previousLines = lastLines.get(player.getUniqueId());
        if (previousLines != null && previousLines.equals(currentLines)) {
            return;
        }
        lastLines.put(player.getUniqueId(), new ArrayList<>(currentLines));

        Set<String> newEntries = new HashSet<>();
        int score = currentLines.size();
        for (String line : currentLines) {
            String entry = line;
            if (entry.isEmpty() || entry.trim().isEmpty()) {
                entry = ChatColor.values()[score % 15].toString() + ChatColor.RESET;
            }

            if (entry.length() > 128)
                entry = entry.substring(0, 128);

            newEntries.add(entry);
            obj.getScore(entry).setScore(score--);
        }

        for (String entry : board.getEntries()) {
            if (!newEntries.contains(entry)) {
                board.resetScores(entry);
            }
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private String replacePlaceholders(String line, Player player, Arena arena) {
        me.horiciastko.bedwars.models.PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId());
        if (stats == null)
            stats = new me.horiciastko.bedwars.models.PlayerStats(player.getUniqueId());

        String result = line
                .replace("%date%", LocalDateTime.now().format(dateFormatter))
                .replace("%server_ip%", plugin.getConfig().getString("server.ip", "play.bedwars.pl"))
                .replace("%version%", plugin.getConfig().getString("server.version", "1.0.0"))
                .replace("%global_players%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%rank%", stats.getRank())
                .replace("%level%", String.valueOf(stats.getLevel()))
                .replace("%progress_bar%", color(stats.getProgressBar()))
                .replace("%xp%", String.valueOf(stats.getExperience()))
                .replace("%current_xp%", String.valueOf(stats.getCurrentLevelXp()))
                .replace("%required_xp%", String.valueOf(stats.getRequiredXpForNextLevel()))
                .replace("%progress_percent%", String.format("%.1f", stats.getProgressPercent()))
                .replace("%coins%", String.valueOf(stats.getCoins()))
                .replace("%kills%", String.valueOf(stats.getKills()))
                .replace("%final_kills%", String.valueOf(stats.getFinalKills()))
                .replace("%beds_broken%", String.valueOf(stats.getBedsBroken()))
                .replace("%wins%", String.valueOf(stats.getWins()))
                .replace("%deaths%", String.valueOf(stats.getDeaths()))
                .replace("%lobby_id%", plugin.getConfig().getString("server.id", "1"))
                .replace("%server_id%", plugin.getConfig().getString("server.id", "1"));

        if (arena != null) {
            String statusStr;
            switch (arena.getState()) {
                case WAITING:
                    statusStr = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                            "scoreboard-status-waiting");
                    break;
                case STARTING:
                    statusStr = plugin.getLanguageManager()
                            .getMessage(player.getUniqueId(), "scoreboard-status-starting")
                            .replace("%time%", String.valueOf(plugin.getGameManager().getStartTimer(arena)));
                    break;
                case IN_GAME:
                    statusStr = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                            "scoreboard-status-ingame");
                    break;
                case ENDING:
                    statusStr = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                            "scoreboard-status-ending");
                    break;
                default:
                    statusStr = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                            "scoreboard-status-unknown");
                    break;
            }

            org.bukkit.configuration.file.FileConfiguration gConfig = plugin.getConfigManager().getGeneratorConfig();
            java.util.List<java.util.Map<?, ?>> events = gConfig.getMapList("events");

            String nextEvent = "Ended";
            int nextTime = 0;

            if (arena.getEventIndex() < events.size()) {
                java.util.Map<?, ?> currentEvent = events.get(arena.getEventIndex());
                String eName = (String) currentEvent.get("name");
                nextEvent = plugin.getLanguageManager().getMessage(player.getUniqueId(),
                        "scoreboard-event-" + eName.toLowerCase().replace(" ", "-"));
                if (nextEvent.startsWith("§cMissing"))
                    nextEvent = eName;
                nextTime = arena.getEventTimer();
            }

            String timeStr = String.format("%d:%02d", Math.max(0, nextTime) / 60, Math.max(0, nextTime) % 60);

            result = result
                    .replace("%map%", arena.getName())
                    .replace("%players%", String.valueOf(arena.getPlayers().size()))
                    .replace("%max_players%", String.valueOf(arena.getMaxPlayers()))
                    .replace("%mode%", arena.getMode().getDisplayName())
                    .replace("%status%", statusStr)
                    .replace("%event_name%", nextEvent)
                    .replace("%event_time%", timeStr)
                    .replace("%time%", timeStr);
        }
        return color(result);
    }

    private List<String> formatTeamsStatus(Player player, Arena arena) {
        List<String> statusLines = new ArrayList<>();

        String bedAlive = plugin.getConfig().getString("scoreboard.symbols.bed-alive", "&a&l✔");
        String bedBroken = plugin.getConfig().getString("scoreboard.symbols.bed-broken", "&a%count%");
        String teamDead = plugin.getConfig().getString("scoreboard.symbols.team-dead", "&c&l✘");
        String youIndicator = plugin.getConfig().getString("scoreboard.symbols.you-indicator", " &b[YOU]");

        Team playerTeam = plugin.getGameManager().getPlayerTeam(arena, player);

        for (Team team : arena.getTeams()) {
            int alive = (int) team.getMembers().stream()
                    .filter(m -> m.getGameMode() != org.bukkit.GameMode.SPECTATOR).count();

            boolean isDead = team.isEliminated() || (team.isBedBroken() && alive == 0)
                    || (arena.getState() == Arena.GameState.IN_GAME && team.getMembers().isEmpty());
            String status;

            if (isDead) {
                status = teamDead;
            } else if (!team.isBedBroken()) {
                status = bedAlive;
            } else {
                status = bedBroken.replace("%count%", String.valueOf(alive));
            }

            ChatColor teamColor = team.getColor();
            String colorCode = (teamColor != null) ? teamColor.toString() : "§f";
            String firstLetter = (!team.getDisplayName().isEmpty())
                    ? team.getDisplayName().substring(0, 1).toUpperCase()
                    : "?";

            String teamNameDisplay = team.getDisplayName();
            String formatting = colorCode;

            String line = colorCode + "§l" + firstLetter + " " + formatting + teamNameDisplay + "§f: " + status;

            if (playerTeam != null && playerTeam.getName().equals(team.getName())) {
                line += " " + youIndicator;
            }

            statusLines.add(color(line));
        }
        return statusLines;
    }

    private String color(String text) {
        if (text == null)
            return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
