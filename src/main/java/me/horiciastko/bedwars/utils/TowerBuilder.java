package me.horiciastko.bedwars.utils;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TowerBuilder {

    public static boolean build(Player player, Arena arena, Location origin) {
        Team team = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
        if (team == null)
            return false;

        Material woolMat = getWoolMaterial(team);
        BlockFace facing = player.getFacing();

        Location start = origin.clone();

        int ox = 0, oz = 0;
        switch (facing) {
            case NORTH:
                ox = -1;
                oz = -3;
                break;
            case SOUTH:
                ox = -2;
                oz = 0;
                break;
            case EAST:
                ox = 0;
                oz = -1;
                break;
            case WEST:
                ox = -3;
                oz = -2;
                break;
            default:
                ox = -1;
                oz = -1;
                break;
        }

        final Location base = start.clone().add(ox, 0, oz);
        final int height = 8;

        if (!canBuildAt(base, height, facing, arena)) {
            return false;
        }

        new BukkitRunnable() {
            int y = 0;

            @Override
            public void run() {
                if (y >= height) {
                    this.cancel();
                    return;
                }

                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 4; z++) {
                        Location loc = base.clone().add(x, y, z);
                        Block block = loc.getBlock();

                        boolean isEdge = (x == 0 || x == 3 || z == 0 || z == 3);

                        if (isEdge) {
                            if (y < 2) {
                                boolean isDoor = false;
                                switch (facing) {
                                    case NORTH:
                                        if (z == 3 && (x == 1 || x == 2))
                                            isDoor = true;
                                        break;
                                    case SOUTH:
                                        if (z == 0 && (x == 1 || x == 2))
                                            isDoor = true;
                                        break;
                                    case EAST:
                                        if (x == 0 && (z == 1 || z == 2))
                                            isDoor = true;
                                        break;
                                    case WEST:
                                        if (x == 3 && (z == 1 || z == 2))
                                            isDoor = true;
                                        break;
                                    default:
                                        break;
                                }
                                if (isDoor) {
                                    continue;
                                }
                            }

                            if (isReplaceable(block)) {
                                block.setType(woolMat);
                                arena.getPlacedBlocks().add(loc);
                            }
                        } else {
                            if (y == height - 1) {
                                if (isReplaceable(block)) {
                                    block.setType(woolMat);
                                    arena.getPlacedBlocks().add(loc);
                                }
                            } else {
                                boolean countsAsLadder = false;
                                BlockFace ladderFace = null;
                                switch (facing) {
                                    case NORTH:
                                        if (z == 0 && x == 1) {
                                            countsAsLadder = true;
                                            ladderFace = BlockFace.SOUTH;
                                        }
                                        break;
                                    case SOUTH:
                                        if (z == 3 && x == 1) {
                                            countsAsLadder = true;
                                            ladderFace = BlockFace.NORTH;
                                        }
                                        break;
                                    case EAST:
                                        if (x == 3 && z == 1) {
                                            countsAsLadder = true;
                                            ladderFace = BlockFace.WEST;
                                        }
                                        break;
                                    case WEST:
                                        if (x == 0 && z == 1) {
                                            countsAsLadder = true;
                                            ladderFace = BlockFace.EAST;
                                        }
                                        break;
                                    default:
                                        break;
                                }

                                if (countsAsLadder) {
                                    if (isReplaceable(block)) {
                                        block.setType(Material.LADDER);
                                        if (block.getBlockData() instanceof Ladder) {
                                            Ladder data = (Ladder) block.getBlockData();
                                            data.setFacing(ladderFace);
                                            block.setBlockData(data);
                                        }
                                        arena.getPlacedBlocks().add(loc);
                                    }
                                }
                            }
                        }
                    }
                }

                y++;
            }
        }.runTaskTimer(BedWars.getInstance(), 0L, 2L);

        BedWars.getInstance().getSoundManager().playSound(origin, "tower-build");
        return true;
    }

    private static boolean canBuildAt(Location base, int height, BlockFace facing, Arena arena) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    if (!willPlaceBlock(x, y, z, height, facing)) {
                        continue;
                    }
                    Block block = base.clone().add(x, y, z).getBlock();
                    if (!isReplaceable(block) || arena.getPlacedBlocks().contains(block.getLocation())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean willPlaceBlock(int x, int y, int z, int height, BlockFace facing) {
        boolean isEdge = (x == 0 || x == 3 || z == 0 || z == 3);

        if (isEdge) {
            if (y < 2) {
                boolean isDoor = false;
                switch (facing) {
                    case NORTH:
                        isDoor = z == 3 && (x == 1 || x == 2);
                        break;
                    case SOUTH:
                        isDoor = z == 0 && (x == 1 || x == 2);
                        break;
                    case EAST:
                        isDoor = x == 0 && (z == 1 || z == 2);
                        break;
                    case WEST:
                        isDoor = x == 3 && (z == 1 || z == 2);
                        break;
                    default:
                        break;
                }
                if (isDoor) {
                    return false;
                }
            }
            return true;
        }

        if (y == height - 1) {
            return true;
        }

        switch (facing) {
            case NORTH:
                return z == 0 && x == 1;
            case SOUTH:
                return z == 3 && x == 1;
            case EAST:
                return x == 3 && z == 1;
            case WEST:
                return x == 0 && z == 1;
            default:
                return false;
        }
    }

    private static boolean isReplaceable(Block block) {
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR
                || type == Material.WATER || type == Material.LAVA;
    }

    private static Material getWoolMaterial(Team team) {
        String color = team.getColor().name();
        return com.cryptomorin.xseries.XMaterial.matchXMaterial(color + "_WOOL")
                .orElse(com.cryptomorin.xseries.XMaterial.WHITE_WOOL).parseMaterial();
    }
}
