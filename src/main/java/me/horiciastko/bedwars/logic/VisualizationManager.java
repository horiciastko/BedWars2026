package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class VisualizationManager {

    private final Map<UUID, List<ArmorStand>> activeHolograms = new ConcurrentHashMap<>();
    private final Map<String, List<ArmorStand>> activeGameHolograms = new ConcurrentHashMap<>();
    private final Map<String, List<ArmorStand>> generatorVisuals = new ConcurrentHashMap<>();
    private final BedWars plugin;

    public VisualizationManager(BedWars plugin) {
        this.plugin = plugin;
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

        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getScoreboardTags().contains("bw_hologram")
                        || entity.getScoreboardTags().contains("bw_game_hologram")
                        || entity.getScoreboardTags().contains("bw_gen_visual")
                        || entity.getScoreboardTags().contains("bw_gen_hologram")
                        || entity.getScoreboardTags().contains("bw_gen_timer")) {
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
