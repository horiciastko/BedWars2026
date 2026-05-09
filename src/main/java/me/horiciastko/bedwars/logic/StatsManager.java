package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final BedWars plugin;
    private final Map<UUID, PlayerStats> statsCache = new HashMap<>();

    public StatsManager(BedWars plugin) {
        this.plugin = plugin;
    }
    
    private int getXpPerKill() {
        return plugin.getLevelsManager().getXpForAction("kill");
    }
    
    private int getXpPerFinalKill() {
        return plugin.getLevelsManager().getXpForAction("final-kill");
    }
    
    private int getXpPerBedBroken() {
        return plugin.getLevelsManager().getXpForAction("bed-broken");
    }
    
    private int getXpPerWin() {
        return plugin.getLevelsManager().getXpForAction("win");
    }
    
    private int getXpPerLoss() {
        return plugin.getLevelsManager().getXpForAction("loss");
    }

    public PlayerStats getStats(UUID uuid) {
        if (statsCache.containsKey(uuid)) {
            return statsCache.get(uuid);
        }
        PlayerStats stats = plugin.getDatabaseManager().getPlayerStats(uuid);
        statsCache.put(uuid, stats);
        return stats;
    }

    public void saveStats(UUID uuid) {
        if (!statsCache.containsKey(uuid))
            return;

        PlayerStats stats = statsCache.get(uuid);
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null)
            name = "Unknown";

        plugin.getDatabaseManager().updatePlayerStats(uuid, name, stats);
    }

    public void unloadStats(UUID uuid) {
        saveStats(uuid);
        statsCache.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : new java.util.ArrayList<>(statsCache.keySet())) {
            saveStats(uuid);
        }
    }

    public void addKill(UUID uuid) {
        PlayerStats stats = getStats(uuid);
        stats.setKills(stats.getKills() + 1);
        grantExperience(uuid, getXpPerKill(), "Kill");
    }

    public void addDeath(UUID uuid) {
        PlayerStats stats = getStats(uuid);
        stats.setDeaths(stats.getDeaths() + 1);
    }

    public void addWin(UUID uuid) {
        PlayerStats stats = getStats(uuid);
        stats.setWins(stats.getWins() + 1);
        plugin.getDatabaseManager().recordStatEvent(uuid, "wins", 1);
        grantExperience(uuid, getXpPerWin(), "Win");
    }

    public void addFinalKill(UUID uuid) {
        PlayerStats stats = getStats(uuid);
        stats.setFinalKills(stats.getFinalKills() + 1);
        plugin.getDatabaseManager().recordStatEvent(uuid, "final_kills", 1);
        grantExperience(uuid, getXpPerFinalKill(), "Final Kill");
    }

    public void addBedBroken(UUID uuid) {
        PlayerStats stats = getStats(uuid);
        stats.setBedsBroken(stats.getBedsBroken() + 1);
        plugin.getDatabaseManager().recordStatEvent(uuid, "beds_broken", 1);
        grantExperience(uuid, getXpPerBedBroken(), "Bed Broken");
    }
    
    public void addLoss(UUID uuid) {
        int xp = getXpPerLoss();
        if (xp > 0) {
            grantExperience(uuid, xp, "Game Played");
        }
    }
    
    public void grantExperience(UUID uuid, int xp, String reason) {
        PlayerStats stats = getStats(uuid);
        int newLevel = stats.addExperience(xp);
        
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updateXpBar(player);
            
            String xpMsg = plugin.getLanguageManager().getMessage(uuid, "xp-gained")
                .replace("%xp%", String.valueOf(xp))
                .replace("%reason%", reason);
            player.sendMessage(xpMsg);
            
            if (newLevel > 0) {
                String levelUpMsg = plugin.getLanguageManager().getMessage(uuid, "level-up")
                    .replace("%level%", String.valueOf(newLevel));
                String rankMsg = plugin.getLanguageManager().getMessage(uuid, "level-up-rank")
                    .replace("%rank%", stats.getRank());
                    
                player.sendMessage(levelUpMsg);
                player.sendMessage(rankMsg);
                
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } catch (Exception e) {
                }
                
                saveStats(uuid);
            }
        }
    }

    public void updateXpBar(Player player) {
        PlayerStats stats = getStats(player.getUniqueId());
        
        player.setLevel(stats.getLevel());
        
        float progress = (float) (stats.getProgressPercent() / 100.0);
        player.setExp(Math.max(0.0f, Math.min(1.0f, progress)));
    }
}
