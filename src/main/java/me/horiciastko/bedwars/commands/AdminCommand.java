package me.horiciastko.bedwars.commands;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements SubCommand {

    private final BedWars plugin;

    public AdminCommand(BedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public String getDescription() {
        return "Administrator commands";
    }

    @Override
    public String getSyntax() {
        return "/bw admin arena create <name>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (!player.hasPermission("bedwars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-no-permission"));
            return;
        }

        if (args.length < 2) {
            sendAdminHelp(player);
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reloadAll();
            plugin.getScoreboardManager().reloadConfig();
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-reload-success"));
            return;
        }

        if (args[1].equalsIgnoreCase("migrate") || args[1].equalsIgnoreCase("migrate-split")) {
            player.sendMessage("§eMigrating legacy arena/NPC data to split databases...");
            me.horiciastko.bedwars.logic.DatabaseManager.SplitMigrationResult result = plugin.getDatabaseManager()
                    .migrateLegacyDataToSplit();

            if (result.isSuccess()) {
                player.sendMessage("§a" + result.getMessage());
                player.sendMessage("§7Arenas copied: §f" + result.getArenasCopied() + " §8| §7NPCs copied: §f"
                        + result.getNpcsCopied());
                player.sendMessage("§7Use §f/bw admin reload §7to refresh runtime config after migration.");
            } else {
                player.sendMessage("§c" + result.getMessage());
            }
            return;
        }

        if (args[1].equalsIgnoreCase("setlobby")) {
            org.bukkit.Location loc = player.getLocation();
            plugin.getGameManager().setMainLobbyLocation(loc);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-setlobby-success")
                .replace("%world%", loc.getWorld().getName())
                .replace("%x%", String.format("%.1f", loc.getX()))
                .replace("%y%", String.format("%.1f", loc.getY()))
                .replace("%z%", String.format("%.1f", loc.getZ())));
            return;
        }

        if (args[1].equalsIgnoreCase("lang")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-lang-usage"));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-lang-available")
                        .replace("%languages%", String.join(", ", plugin.getLanguageManager().getAvailableLanguages())));
                return;
            }
            String targetLang = args[2];
            if (!plugin.getLanguageManager().isLanguageAvailable(targetLang)) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-lang-not-loaded")
                        .replace("%lang%", targetLang));
                return;
            }
            plugin.getConfig().set("default-language", targetLang);
            plugin.saveConfig();

            plugin.getConfigManager().reloadAll();
            plugin.getLanguageManager().reloadDefaultLanguage();
            plugin.getLanguageManager().setPlayerLanguage(player.getUniqueId(), targetLang);
            plugin.getNpcManager().refreshAllNPCs();
            plugin.getVisualizationManager().refreshLeaderboardHolograms();
            
            for (Arena arena : plugin.getArenaManager().getArenas()) {
                if (arena.getState() == Arena.GameState.IN_GAME) {
                    plugin.getVisualizationManager().spawnGeneratorVisuals(arena);
                }
            }

            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-lang-set")
                .replace("%lang%", targetLang));
            return;
        }

        if (args[1].equalsIgnoreCase("build")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-build-usage"));
                return;
            }
            boolean enable = args[2].equalsIgnoreCase("on");
            plugin.getGameManager().setBuildMode(player, enable);
            String status = enable ? plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-build-enabled")
                                  : plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-build-disabled");
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-build-toggled")
                .replace("%status%", status));
            return;
        }

        if (args[1].equalsIgnoreCase("settings")) {
            handleAdminSettings(player, args);
            return;
        }

        if (args[1].equalsIgnoreCase("npc")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-usage"));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-types-list")
                        .replace("%types%", String.join(", ", plugin.getNpcManager().getAvailableTypes())));
                return;
            }

            if (args[2].equalsIgnoreCase("remove")) {
                if (args.length >= 4) {
                    try {
                        int npcId = Integer.parseInt(args[3]);
                        boolean removed = plugin.getNpcManager().removeNPCById(npcId);
                        
                        if (removed) {
                            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-removed-by-id")
                                .replace("%id%", String.valueOf(npcId)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-id-not-found")
                                .replace("%id%", String.valueOf(npcId)));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-invalid-id"));
                    }
                    return;
                }
                
                me.horiciastko.bedwars.npc.BedWarsNPC nearest = null;

                org.bukkit.entity.Entity target = player.getTargetEntity(5);
                if (target != null) {
                    nearest = plugin.getNpcManager().getNPCByEntity(target.getUniqueId());
                }

                if (nearest == null) {
                    org.bukkit.util.Vector direction = player.getLocation().getDirection();
                    org.bukkit.Location eyeLoc = player.getEyeLocation();
                    double maxDistance = 5.0;
                    org.bukkit.entity.Entity nearestEntity = null;
                    
                    for (org.bukkit.entity.Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
                        me.horiciastko.bedwars.npc.BedWarsNPC npc = plugin.getNpcManager().getNPCByEntity(entity.getUniqueId());
                        if (npc != null) {
                            org.bukkit.util.Vector toEntity = entity.getLocation().toVector().subtract(eyeLoc.toVector());
                            if (toEntity.normalize().dot(direction) > 0.98) {
                                if (nearestEntity == null || eyeLoc.distance(entity.getLocation()) < eyeLoc.distance(nearestEntity.getLocation())) {
                                    nearestEntity = entity;
                                    nearest = npc;
                                }
                            }
                        }
                    }
                }

                if (nearest == null) {
                    nearest = plugin.getNpcManager().getNearestNPC(player, 5.0);
                }

                if (nearest != null) {
                    plugin.getNpcManager().removeNPC(nearest);
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-removed"));
                } else {
                    java.util.List<me.horiciastko.bedwars.logic.DatabaseManager.StandaloneNPCRecord> allNPCs = 
                        plugin.getNpcManager().getAllStandaloneNPCsFromDB();
                    
                    if (allNPCs.isEmpty()) {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-none-in-db"));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-list-header"));
                        for (me.horiciastko.bedwars.logic.DatabaseManager.StandaloneNPCRecord record : allNPCs) {
                            org.bukkit.Location loc = me.horiciastko.bedwars.utils.SerializationUtils.stringToLocation(record.getLocation());
                            String locationStr = loc != null 
                                ? String.format("%s: %.1f, %.1f, %.1f", 
                                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ())
                                : record.getLocation();
                            
                            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-list-entry")
                                .replace("%id%", String.valueOf(record.getId()))
                                .replace("%type%", record.getType())
                                .replace("%location%", locationStr));
                        }
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-list-footer"));
                    }
                }
                return;
            }

            String type = args[2].toLowerCase();
            List<String> validTypes = plugin.getNpcManager().getAvailableTypes();
            if (!validTypes.contains(type)) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-npc-invalid-type")
                    .replace("%types%", String.join(", ", validTypes)));
                return;
            }
            plugin.getSupportManager().createCitizensNPC(player, type);
            return;
        }

        if (args[1].equalsIgnoreCase("leaderboard")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-usage"));
                return;
            }

            if (args[2].equalsIgnoreCase("create")) {
                if (args.length < 5) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-create-usage"));
                    return;
                }

                String statType = args[3].toLowerCase();
                String period = args[4].toLowerCase();
                int lines = 10;
                if (args.length >= 6) {
                    try {
                        lines = Integer.parseInt(args[5]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-lines-invalid"));
                        return;
                    }
                }

                String id = plugin.getVisualizationManager().createLeaderboardHologram(player.getLocation(), statType, period,
                        lines, "");
                if (id == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-create-failed"));
                    return;
                }

                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-created")
                        .replace("%id%", id));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-created-info")
                        .replace("%stat%", statType)
                        .replace("%period%", period)
                        .replace("%lines%", String.valueOf(Math.max(1, Math.min(15, lines)))));
                return;
            }

            if (args[2].equalsIgnoreCase("remove")) {
                if (args.length >= 4) {
                    boolean removed = plugin.getVisualizationManager().removeLeaderboardHologram(args[3]);
                    if (removed) {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-removed")
                                .replace("%id%", args[3]));
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-id-not-found")
                                .replace("%id%", args[3]));
                    }
                    return;
                }

                String removedId = plugin.getVisualizationManager().removeNearestLeaderboardHologram(player.getLocation(), 6.0);
                if (removedId == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-none-nearby"));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-nearest-removed")
                            .replace("%id%", removedId));
                }
                return;
            }

            if (args[2].equalsIgnoreCase("list")) {
                List<me.horiciastko.bedwars.logic.VisualizationManager.LeaderboardHologram> holograms = plugin
                        .getVisualizationManager().getLeaderboardHolograms();
                if (holograms.isEmpty()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-empty"));
                    return;
                }

                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-list-header")
                        .replace("%count%", String.valueOf(holograms.size())));
                for (me.horiciastko.bedwars.logic.VisualizationManager.LeaderboardHologram hologram : holograms) {
                    org.bukkit.Location loc = hologram.getLocation();
                    String world = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : "unknown";
                    String coords = (loc != null)
                            ? String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())
                            : "unknown";
                    player.sendMessage("§e" + hologram.getId() + " §8- §f" + hologram.getStatType() + " §7/ §f"
                            + hologram.getPeriod() + " §8- §bTOP " + hologram.getLines() + " §8- §7" + world + " §8@ §f"
                            + coords);
                }
                return;
            }

            if (args[2].equalsIgnoreCase("refresh")) {
                plugin.getVisualizationManager().refreshLeaderboardHolograms();
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-refreshed"));
                return;
            }

            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-leaderboard-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("db")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-usage"));
                return;
            }

            if (args[2].equalsIgnoreCase("status")) {
                boolean splitEnabled = plugin.getDatabaseManager().isSplitModeEnabled();
                boolean migrationAvailable = plugin.getDatabaseManager().isSplitMigrationAvailable();

                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-status-header"));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(),
                        splitEnabled ? "admin-db-split-enabled" : "admin-db-split-disabled"));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(),
                        migrationAvailable ? "admin-db-migration-yes" : "admin-db-migration-no"));
                if (splitEnabled && migrationAvailable) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migration-hint"));
                }
                return;
            }

            if (args[2].equalsIgnoreCase("migrate-split") || args[2].equalsIgnoreCase("migrate")) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migrate-starting"));
                me.horiciastko.bedwars.logic.DatabaseManager.SplitMigrationResult result = plugin.getDatabaseManager()
                        .migrateLegacyDataToSplit();

                if (result.isSuccess()) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migrate-success")
                            .replace("%message%", result.getMessage()));
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migrate-details")
                            .replace("%arenas%", String.valueOf(result.getArenasCopied()))
                            .replace("%npcs%", String.valueOf(result.getNpcsCopied())));
                    // Reload arenas and NPCs into memory so they appear in GUI without a restart
                    plugin.getArenaManager().loadArenas();
                    plugin.getNpcManager().loadStandaloneNPCsFromDatabase();
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migrate-reloaded"));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-migrate-failed")
                            .replace("%message%", result.getMessage()));
                }
                return;
            }

            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-db-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                new me.horiciastko.bedwars.gui.ArenaListGUI().open(player);
                return;
            }
        }

        if (args.length < 3) {
            sendAdminHelp(player);
            return;
        }

        if (args[1].equalsIgnoreCase("arena")) {
            if (args[2].equalsIgnoreCase("create")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-create-usage"));
                    return;
                }
                String name = args[3];
                if (plugin.getArenaManager().getArena(name) != null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-exists"));
                    return;
                }
                plugin.getArenaManager().addArena(new Arena(name));
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-created")
                    .replace("%name%", name));
            } else if (args[2].equalsIgnoreCase("edit")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-edit-usage"));
                    return;
                }
                String name = args[3];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", name));
                    return;
                }
                plugin.getArenaManager().setEditArena(player, arena);
                new me.horiciastko.bedwars.gui.ArenaSettingsGUI(arena).open(player);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-edit-opened")
                    .replace("%name%", name));
            } else if (args[2].equalsIgnoreCase("enable")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-enable-usage"));
                    return;
                }
                String name = args[3];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", name));
                    return;
                }
                arena.setEnabled(true);
                plugin.getArenaManager().saveArena(arena);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-enabled")
                    .replace("%name%", name));
            } else if (args[2].equalsIgnoreCase("disable")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-disable-usage"));
                    return;
                }
                String name = args[3];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", name));
                    return;
                }
                arena.setEnabled(false);
                plugin.getArenaManager().saveArena(arena);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-disabled")
                    .replace("%name%", name));
            } else if (args[2].equalsIgnoreCase("delete")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-delete-usage"));
                    return;
                }
                String name = args[3];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", name));
                    return;
                }
                plugin.getArenaManager().deleteArena(arena);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-deleted")
                    .replace("%name%", name));
            } else if (args[2].equalsIgnoreCase("group")) {
                if (args.length < 5) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-group-usage"));
                    return;
                }
                String name = args[3];
                String group = args[4];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", name));
                    return;
                }

                if (!plugin.getConfig().contains("groups." + group)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-group-warning")
                        .replace("%group%", group));
                }

                arena.setGroup(group);
                plugin.getArenaManager().saveArena(arena);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-group-set")
                    .replace("%name%", name)
                    .replace("%group%", group));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-usage"));
            }
        } else if (args[1].equalsIgnoreCase("start")) {
            if (args.length < 3) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-start-usage"));
                return;
            }
            String arenaName = args[2];
            Arena arena = plugin.getArenaManager().getArena(arenaName);
            if (arena == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                    .replace("%name%", arenaName));
                return;
            }
            if (arena.getPlayers().isEmpty()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-start-no-players")
                    .replace("%name%", arenaName));
                return;
            }
            plugin.getGameManager().forceStart(arena);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-start-forced")
                .replace("%name%", arena.getName()));
        } else if (args[1].equalsIgnoreCase("sign")) {
            if (args[2].equalsIgnoreCase("join")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-sign-join-usage"));
                    return;
                }
                String arenaName = args[3];
                Arena arena = plugin.getArenaManager().getArena(arenaName);
                if (arena == null) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-arena-not-found")
                        .replace("%name%", arenaName));
                    return;
                }

                org.bukkit.block.Block block = player.getTargetBlockExact(5);
                if (block == null || !block.getType().name().contains("SIGN")) {
                    player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-sign-not-looking"));
                    return;
                }

                arena.getJoinSigns().add(block.getLocation());
                plugin.getArenaManager().saveArena(arena);
                plugin.getSignManager().updateSigns(arena);
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-sign-created")
                    .replace("%name%", arena.getName()));
            } else {
                sendAdminHelp(player);
            }
        } else {
            sendAdminHelp(player);
        }
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (!player.hasPermission("bedwars.admin"))
            return new ArrayList<>();

        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("arena");
            options.add("sign");
            options.add("reload");
            options.add("start");
            options.add("npc");
            options.add("build");
            options.add("lang");
            options.add("setlobby");
            options.add("leaderboard");
            options.add("db");
            options.add("settings");
            options.add("migrate");
            options.add("migrate-split");
            return filter(options, args[1]);
        }

        if (args.length == 3) {
            List<String> options = new ArrayList<>();
            if (args[1].equalsIgnoreCase("arena")) {
                options.add("create");
                options.add("edit");
                options.add("enable");
                options.add("disable");
                options.add("delete");
                options.add("group");
            } else if (args[1].equalsIgnoreCase("sign")) {
                options.add("join");
            } else if (args[1].equalsIgnoreCase("build")) {
                options.add("on");
                options.add("off");
            } else if (args[1].equalsIgnoreCase("npc")) {
                options.add("remove");
                options.addAll(plugin.getNpcManager().getAvailableTypes());
            } else if (args[1].equalsIgnoreCase("lang")) {
                options.addAll(plugin.getLanguageManager().getAvailableLanguages());
            } else if (args[1].equalsIgnoreCase("leaderboard")) {
                options.add("create");
                options.add("remove");
                options.add("list");
                options.add("refresh");
            } else if (args[1].equalsIgnoreCase("db")) {
                options.add("status");
                options.add("migrate");
                options.add("migrate-split");
            } else if (args[1].equalsIgnoreCase("settings")) {
                options.add("show");
                options.add("multimode");
                options.add("randomteams");
            }
            return filter(options, args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("settings")) {
            return filter(java.util.Arrays.asList("on", "off"), args[3]);
        }

        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("leaderboard") && args[2].equalsIgnoreCase("create")) {
                List<String> stats = new ArrayList<>();
                stats.add("wins");
                stats.add("final_kills");
                stats.add("beds_broken");
                return filter(stats, args[3]);
            }

            if (args[1].equalsIgnoreCase("leaderboard") && args[2].equalsIgnoreCase("remove")) {
                List<String> ids = plugin.getVisualizationManager().getLeaderboardHolograms().stream()
                        .map(me.horiciastko.bedwars.logic.VisualizationManager.LeaderboardHologram::getId)
                        .collect(Collectors.toList());
                return filter(ids, args[3]);
            }

            List<String> arenaNames = plugin.getArenaManager().getArenas().stream().map(Arena::getName)
                    .collect(Collectors.toList());
            return filter(new ArrayList<>(arenaNames), args[3]);
        }

        if (args.length == 5) {
            if (args[1].equalsIgnoreCase("leaderboard") && args[2].equalsIgnoreCase("create")) {
                List<String> periods = new ArrayList<>();
                periods.add("daily");
                periods.add("weekly");
                periods.add("monthly");
                periods.add("alltime");
                return filter(periods, args[4]);
            }

            if (args[1].equalsIgnoreCase("arena") && args[2].equalsIgnoreCase("group")) {
                org.bukkit.configuration.ConfigurationSection groups = plugin.getConfig()
                        .getConfigurationSection("groups");
                if (groups != null) {
                    return filter(new ArrayList<>(groups.getKeys(false)), args[4]);
                }
                return filter(java.util.Collections.singletonList("Default"), args[4]);
            }
        }

        if (args.length == 6) {
            if (args[1].equalsIgnoreCase("leaderboard") && args[2].equalsIgnoreCase("create")) {
                List<String> lines = new ArrayList<>();
                lines.add("5");
                lines.add("10");
                lines.add("15");
                return filter(lines, args[5]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(" ");
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-header"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-arena-create"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-arena-edit"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-arena-toggle"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-npc-create"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-npc-remove"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-build"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-sign"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-separator"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-reload"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-start"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-lang"));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "admin-help-setlobby"));
        player.sendMessage("§7/bw admin settings <show|multimode|randomteams> [on|off] §8- §fSzybkie ustawienia globalne");
        player.sendMessage("§7/bw admin leaderboard <create|remove|list|refresh> §8- §fLeaderboard hologramy");
        player.sendMessage("§7/bw admin migrate §8- §fSzybka migracja legacy DB do split DB");
        player.sendMessage("§7/bw admin db <status|migrate-split> §8- §fMigracja danych do split DB");
        player.sendMessage(" ");
    }

    private void handleAdminSettings(Player player, String[] args) {
        if (args.length == 2 || args[2].equalsIgnoreCase("show")) {
            boolean multiMode = plugin.getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true);
            boolean randomTeams = plugin.getConfig().getBoolean("game.random-team-assignment", true);
            player.sendMessage(" ");
            player.sendMessage("§6§lBedWars Settings");
            player.sendMessage("§7multimode: " + (multiMode ? "§aON" : "§cOFF") + " §8- reuse empty waiting arenas for selected mode");
            player.sendMessage("§7randomteams: " + (randomTeams ? "§aON" : "§cOFF") + " §8- random/fair tie-break team assignment");
            player.sendMessage("§8Use: §f/bw admin settings <multimode|randomteams> <on|off>");
            player.sendMessage(" ");
            return;
        }

        if (args.length < 4) {
            player.sendMessage("§cUsage: /bw admin settings <multimode|randomteams> <on|off>");
            return;
        }

        String key = args[2].toLowerCase();
        String value = args[3].toLowerCase();
        if (!value.equals("on") && !value.equals("off")) {
            player.sendMessage("§cValue must be on/off.");
            return;
        }
        boolean enabled = value.equals("on");

        if (key.equals("multimode")) {
            plugin.getConfig().set("join-gui.allow-multi-mode-arenas", enabled);
            plugin.saveConfig();
            player.sendMessage("§7[BedWars] multimode: " + (enabled ? "§aON" : "§cOFF"));
            return;
        }

        if (key.equals("randomteams")) {
            plugin.getConfig().set("game.random-team-assignment", enabled);
            plugin.saveConfig();
            player.sendMessage("§7[BedWars] randomteams: " + (enabled ? "§aON" : "§cOFF"));
            return;
        }

        player.sendMessage("§cUnknown setting. Use multimode or randomteams.");
    }
}
