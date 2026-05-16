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
            String saved = BedWars.getInstance().getDatabaseManager().getSetting("build_mode_" + event.getPlayer().getUniqueId());
            // First time joining (null): default ON. Otherwise restore the last known state.
            boolean enable = saved == null || "1".equals(saved);
            BedWars.getInstance().getGameManager().setBuildMode(event.getPlayer(), enable);

            org.bukkit.Bukkit.getScheduler().runTaskLater(BedWars.getInstance(), () -> {
                if (!event.getPlayer().isOnline()) {
                    return;
                }
                if (BedWars.getInstance().getDatabaseManager().isSplitMigrationAvailable()) {
                    event.getPlayer().sendMessage("§6§l[BedWars] §eLegacy data migration is available.");
                    event.getPlayer().sendMessage("§7Run §f/bw admin db migrate-split §7to move arenas/NPCs to split DB.");
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BedWars.getInstance().getVisualizationManager().hideHolograms(event.getPlayer());
        BedWars.getInstance().getArenaManager().setEditArena(event.getPlayer(), null);
        BedWars.getInstance().getArenaManager().leaveArena(event.getPlayer());
        BedWars.getInstance().getStatsManager().unloadStats(event.getPlayer().getUniqueId());
        BedWars.getInstance().getPartyManager().declineInvite(event.getPlayer().getUniqueId());
        BedWars.getInstance().getPartyManager().leaveParty(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        BedWars.getInstance().getVisualizationManager().clearAll();
    }
}
