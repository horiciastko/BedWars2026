package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class CleanupListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Automatically grant build mode to OPs and bedwars.admin holders so they
        // can build/break blocks in any world without running /bw admin build on manually.
        if (event.getPlayer().hasPermission("bedwars.admin")) {
            BedWars.getInstance().getGameManager().setBuildMode(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BedWars.getInstance().getVisualizationManager().hideHolograms(event.getPlayer());
        BedWars.getInstance().getArenaManager().setEditArena(event.getPlayer(), null);
        BedWars.getInstance().getArenaManager().leaveArena(event.getPlayer());
        BedWars.getInstance().getStatsManager().unloadStats(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        BedWars.getInstance().getVisualizationManager().clearAll();
    }
}
