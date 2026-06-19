package me.horiciastko.bedwars.utils;

import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

@SuppressWarnings("deprecation")
public class TeamDetector {

    public static int detectTeams(Arena arena) {
        if (arena.getPos1() == null || arena.getPos2() == null)
            return 0;

        Location min = new Location(arena.getPos1().getWorld(),
                Math.min(arena.getPos1().getX(), arena.getPos2().getX()),
                Math.min(arena.getPos1().getY(), arena.getPos2().getY()),
                Math.min(arena.getPos1().getZ(), arena.getPos2().getZ()));

        Location max = new Location(arena.getPos1().getWorld(),
                Math.max(arena.getPos1().getX(), arena.getPos2().getX()),
                Math.max(arena.getPos1().getY(), arena.getPos2().getY()),
                Math.max(arena.getPos1().getZ(), arena.getPos2().getZ()));

        int found = 0;
        arena.getTeams().clear();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);

                    String bName = block.getType().name();
                    if (bName.endsWith("_BED") || bName.equals("BED_BLOCK")) {
                        boolean isFoot = false;
                        try {
                            Object data = block.getClass().getMethod("getBlockData").invoke(block);
                            if (data instanceof org.bukkit.block.data.type.Bed) {
                                org.bukkit.block.data.type.Bed bed = (org.bukkit.block.data.type.Bed) data;
                                if (bed.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT)
                                    isFoot = true;
                            }
                        } catch (Exception e) {
                            if (block.getData() < 8) {
                                if (block.getState().getData() instanceof org.bukkit.material.Bed) {
                                    org.bukkit.material.Bed bed = (org.bukkit.material.Bed) block.getState().getData();
                                    if (!bed.isHeadOfBed())
                                        isFoot = true;
                                }
                            }
                        }
                        if (isFoot)
                            continue;

                        ChatColor color = getTeamColorFromMaterial(block.getType());
                        if (color == null)
                            continue;

                        String internalName = color.name().substring(0, 1).toUpperCase()
                                + color.name().substring(1).toLowerCase();
                        String displayName = internalName;
                        Material woolMat = com.cryptomorin.xseries.XMaterial.matchXMaterial(color.name() + "_WOOL")
                                .orElse(com.cryptomorin.xseries.XMaterial.WHITE_WOOL).parseMaterial();

                        org.bukkit.configuration.file.FileConfiguration tConfig = me.horiciastko.bedwars.BedWars
                                .getInstance().getConfigManager().getTeamConfig();
                        if (tConfig != null && tConfig.getConfigurationSection("teams") != null) {
                            for (String key : tConfig.getConfigurationSection("teams").getKeys(false)) {
                                String cfgColor = tConfig.getString("teams." + key + ".color", "");
                                if (cfgColor.equalsIgnoreCase(color.name())) {
                                    internalName = key;
                                    displayName = tConfig.getString("teams." + key + ".display_name", key);
                                    woolMat = com.cryptomorin.xseries.XMaterial
                                            .matchXMaterial(
                                                    tConfig.getString("teams." + key + ".material", woolMat.name()))
                                            .orElse(com.cryptomorin.xseries.XMaterial.WHITE_WOOL).parseMaterial();
                                    break;
                                }
                            }
                        }

                        Team team = new Team(internalName, displayName, color, woolMat);
                        team.setBedLocation(block.getLocation());
                        team.setSpawnLocation(block.getLocation().clone().add(0.5, 2, 0.5));

                        findNpcsNearby(team, block.getLocation());
                        findGeneratorsNearby(team, block.getLocation());

                        arena.getTeams().add(team);
                        found++;
                    }
                }
            }
        }
        return found;
    }

    private static ChatColor getTeamColorFromMaterial(Material mat) {
        String name = mat.name();
        for (ChatColor color : ChatColor.values()) {
            if (name.startsWith(color.name()))
                return color;
        }
        return null;
    }

    private static void findNpcsNearby(Team team, Location bedLoc) {
        team.setShopLocation(team.getSpawnLocation().clone().add(2, 0, 0));
        team.setUpgradeLocation(team.getSpawnLocation().clone().add(-2, 0, 0));
    }

    private static void findGeneratorsNearby(Team team, Location bedLoc) {
    }
}
