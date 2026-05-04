package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class LobbyListener implements Listener {

    private final BedWars plugin;

    public LobbyListener(BedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Location lobby = plugin.getGameManager().getMainLobbyLocation();
        if (isWorldLoaded(lobby)) {
            event.getPlayer().teleport(lobby);
        }

        restoreLobbyStats(event.getPlayer());

        plugin.getStatsManager().updateXpBar(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location lobby = plugin.getGameManager().getMainLobbyLocation();
        if (isWorldLoaded(lobby)) {
            event.setRespawnLocation(lobby);
        }

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> restoreLobbyStats(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID)
            return;

        Player player = (Player) event.getEntity();
        Arena arena = plugin.getArenaManager().getPlayerArena(player);

        if (arena != null && arena.getState() == Arena.GameState.IN_GAME)
            return;

        event.setCancelled(true);
        Location lobby = plugin.getGameManager().getMainLobbyLocation();
        if (isWorldLoaded(lobby)) {
            player.teleport(lobby);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "void-teleport-lobby"));
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
        }

        restoreLobbyStats(player);
    }

    private void restoreLobbyStats(Player player) {
        // Only reset gamemode for players not currently tracked in any arena
        if (plugin.getArenaManager().getPlayerArena(player) == null) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
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
