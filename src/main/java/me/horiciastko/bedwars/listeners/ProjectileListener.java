package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

public class ProjectileListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Snowball) {
            org.bukkit.entity.Snowball snowball = (org.bukkit.entity.Snowball) event.getEntity();
            if (!snowball.getScoreboardTags().contains("bw_bedbug"))
                return;
            if (!(snowball.getShooter() instanceof Player))
                return;
            Player player = (Player) snowball.getShooter();
            me.horiciastko.bedwars.models.Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
            if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
                return;
            me.horiciastko.bedwars.models.Team playerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);

            org.bukkit.Location spawnLoc;
            if (event.getHitBlock() != null) {
                spawnLoc = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
            } else {
                spawnLoc = snowball.getLocation();
            }

            org.bukkit.entity.Silverfish silverfish = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.Silverfish.class);
            silverfish.setCustomName(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "entity-bedbug"));
            silverfish.setCustomNameVisible(true);
            silverfish.addScoreboardTag("bw_mob");
            if (playerTeam != null) {
                silverfish.addScoreboardTag("team_" + playerTeam.getName());
            }
            // Despawn timer hologram above silverfish
            final int despawnSeconds = 30;
            org.bukkit.entity.ArmorStand timerStand = spawnLoc.getWorld().spawn(
                    spawnLoc.clone().add(0, 0.8, 0), org.bukkit.entity.ArmorStand.class, as -> {
                        as.setVisible(false);
                        as.setGravity(false);
                        as.setSmall(true);
                        as.setMarker(true);
                        as.setCustomNameVisible(true);
                        as.setCustomName("§c§l" + despawnSeconds + "s");
                    });

            new org.bukkit.scheduler.BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!silverfish.isValid() || silverfish.isDead() || ticks >= despawnSeconds * 20) {
                        if (silverfish.isValid() && !silverfish.isDead()) silverfish.setHealth(0);
                        timerStand.remove();
                        this.cancel();
                        return;
                    }
                    timerStand.teleport(silverfish.getLocation().add(0, 0.8, 0));
                    if (ticks % 20 == 0) {
                        int remaining = despawnSeconds - (ticks / 20);
                        timerStand.setCustomName("§c§l" + remaining + "s");
                    }
                    ticks++;
                }
            }.runTaskTimer(BedWars.getInstance(), 1L, 1L);
            return;
        }

        if (event.getEntity() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getEntity();
            if (!(fireball.getShooter() instanceof Player))
                return;
            Player player = (Player) fireball.getShooter();

            Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
            if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
                return;
        }
    }
}
