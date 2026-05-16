package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

@SuppressWarnings("deprecation")
public class GeneratorTask extends BukkitRunnable {

    private final BedWars plugin;

    public GeneratorTask(BedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.getState() != Arena.GameState.IN_GAME)
                continue;

            arena.setTicks(arena.getTicks() + 1);
            if (arena.getTicks() >= 20) {
                arena.setTicks(0);
                arena.setGameTime(arena.getGameTime() + 1);
                handleEvents(arena);
            }

            handleTeamGenerators(arena);
            handleGlobalGenerators(arena);
            handleTraps(arena);
            handleMobAI(arena);
            updateGeneratorVisuals(arena);
            refreshTeamUpgrades(arena);
        }
    }

    private void handleTraps(Arena arena) {
        if (arena.getTicks() % 5 != 0)
            return;

        for (Team team : arena.getTeams()) {
            if (team.getActiveTraps().isEmpty())
                continue;
            if (team.getBasePos1() == null || team.getBasePos2() == null)
                continue;

            for (Player p : arena.getPlayers()) {
                if (team.getMembers().contains(p))
                    continue;
                if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL)
                    continue;
                if (plugin.getGameManager().hasTrapImmunity(p.getUniqueId()))
                    continue;

                if (isInside(p.getLocation(), team.getBasePos1(), team.getBasePos2(), 2)) {
                    String trapKey = team.getActiveTraps().remove(0);
                    triggerTrap(arena, team, p, trapKey);
                    break;
                }
            }
        }
    }

    private void triggerTrap(Arena arena, Team team, Player triggerer, String trapKey) {
        ConfigurationSection trapData = plugin.getConfigManager().getUpgradesConfig()
                .getConfigurationSection("trap-types." + trapKey);
        if (trapData == null)
            return;

        String trapName = org.bukkit.ChatColor.translateAlternateColorCodes('&', trapData.getString("name", "Trap"));
        String alertMsg = plugin.getLanguageManager().getMessage(null, "trap-triggered")
                .replace("%trap%", trapName);

        team.getMembers().forEach(m -> {
            m.sendMessage(alertMsg);
            m.playSound(m.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            plugin.sendTitle(m,
                    plugin.getLanguageManager().getMessage(m.getUniqueId(), "trap-triggered-title"),
                    plugin.getLanguageManager().getMessage(m.getUniqueId(), "trap-triggered-subtitle")
                            .replace("%trap%", trapName),
                    5, 40, 5);
        });

        String effects = trapData.getString("effect", "");
        if (effects.equals("ALARM")) {
            triggerer.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            triggerer.getWorld().playSound(triggerer.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 2f, 0.5f);
        } else {
            for (String effectPart : effects.split(",")) {
                String[] parts = effectPart.split(":");
                if (parts.length < 3)
                    continue;

                org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(parts[0]);
                int duration = Integer.parseInt(parts[1]) * 20;
                int amplifier = Integer.parseInt(parts[2]) - 1;

                if (type == null)
                    continue;

                if (trapKey.equals("counter_offensive")) {
                    team.getMembers().forEach(
                            m -> m.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier)));
                } else {
                    triggerer.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier));
                }
            }
        }

        plugin.getGameManager().setTrapImmunity(triggerer.getUniqueId(), 30);
    }

    private void handleEvents(Arena arena) {
        if (arena.getEventTimer() < 0) {
            return;
        }

        if (arena.getEventTimer() > 0) {
            arena.setEventTimer(arena.getEventTimer() - 1);
        } else {
            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGeneratorConfig();
            java.util.List<java.util.Map<?, ?>> events = config.getMapList("events");

            // Defensive guard: if the first event timer is unexpectedly zero very early in a new game,
            // reinitialize it from config instead of immediately firing an upgrade.
            if (arena.getEventIndex() == 0 && !events.isEmpty()) {
                int expectedFirstDuration = resolveEventDurationSeconds(events, 0, 360);
                if (expectedFirstDuration >= 30
                        && arena.getGameTime() < Math.max(5, expectedFirstDuration / 2)
                        && arena.getEventTimer() <= 0) {
                    arena.setEventTimer(expectedFirstDuration);
                    plugin.getLogger().warning("Detected invalid first event timer in arena " + arena.getName()
                            + ", reinitializing to " + expectedFirstDuration + " seconds");
                    return;
                }
            }

            if (arena.getEventIndex() < events.size()) {
                java.util.Map<?, ?> currentEvent = events.get(arena.getEventIndex());
                String eventId = resolveEventId(currentEvent);

                if (eventId != null) {
                    plugin.getGameManager().triggerGameEvent(arena, eventId);
                }

                arena.setEventIndex(arena.getEventIndex() + 1);

                if (arena.getEventIndex() < events.size()) {
                    arena.setEventTimer(resolveEventDurationSeconds(events, arena.getEventIndex(), 360));
                } else {
                    arena.setEventTimer(-1);
                }
            }
        }
    }

    private String resolveEventId(java.util.Map<?, ?> event) {
        if (event == null) {
            return null;
        }

        Object idObj = event.get("id");
        if (idObj instanceof String) {
            String id = ((String) idObj).trim().toLowerCase();
            if (!id.isEmpty()) {
                return id;
            }
        }

        // Legacy format support (type + tier).
        Object typeObj = event.get("type");
        if (!(typeObj instanceof String)) {
            return null;
        }

        String type = ((String) typeObj).trim().toLowerCase();
        int tier = parsePositiveInt(event.get("tier"), -1);

        if ("diamond_upgrade".equals(type)) {
            return tier >= 3 ? "diamond_3" : "diamond_2";
        }
        if ("emerald_upgrade".equals(type)) {
            return tier >= 3 ? "emerald_3" : "emerald_2";
        }
        if ("destroy_beds".equals(type) || "bed_gone".equals(type)) {
            return "bed_gone";
        }
        if ("sudden_death".equals(type)) {
            return "sudden_death";
        }
        if ("game_end".equals(type)) {
            return "game_end";
        }

        String byName = resolveEventIdFromName(event.get("name"));
        if (byName != null) {
            return byName;
        }

        return null;
    }

    private String resolveEventIdFromName(Object nameObj) {
        if (!(nameObj instanceof String)) {
            return null;
        }

        String name = ((String) nameObj).trim().toLowerCase();
        if (name.isEmpty()) {
            return null;
        }

        // Check tier III before tier II because "iii" contains "ii".
        if (name.contains("diamond") && (name.contains("iii") || name.contains(" 3"))) {
            return "diamond_3";
        }
        if (name.contains("emerald") && (name.contains("iii") || name.contains(" 3"))) {
            return "emerald_3";
        }
        if (name.contains("diamond") && (name.contains("ii") || name.contains(" 2"))) {
            return "diamond_2";
        }
        if (name.contains("emerald") && (name.contains("ii") || name.contains(" 2"))) {
            return "emerald_2";
        }
        if (name.contains("bed") && name.contains("gone")) {
            return "bed_gone";
        }
        if (name.contains("sudden") && name.contains("death")) {
            return "sudden_death";
        }
        if (name.contains("game") && name.contains("end")) {
            return "game_end";
        }

        return null;
    }

    private int resolveEventDurationSeconds(java.util.List<java.util.Map<?, ?>> events, int index, int fallbackSeconds) {
        if (events == null || index < 0 || index >= events.size()) {
            return fallbackSeconds;
        }

        java.util.Map<?, ?> event = events.get(index);
        int duration = parsePositiveInt(event.get("duration"), -1);
        if (duration > 0) {
            return duration;
        }

        // Legacy format support: "time" as absolute timestamp since game start.
        int absoluteTime = parsePositiveInt(event.get("time"), -1);
        if (absoluteTime > 0) {
            if (index == 0) {
                return absoluteTime;
            }
            int previousAbsolute = parsePositiveInt(events.get(index - 1).get("time"), -1);
            if (previousAbsolute > 0 && absoluteTime > previousAbsolute) {
                return absoluteTime - previousAbsolute;
            }
            return absoluteTime;
        }

        plugin.getLogger().warning("Event at index " + index + " is missing 'duration'/'time' field, using default "
                + fallbackSeconds + " seconds");
        return fallbackSeconds;
    }

    private int parsePositiveInt(Object value, int fallback) {
        if (value instanceof Number) {
            int parsed = ((Number) value).intValue();
            return parsed > 0 ? parsed : fallback;
        }
        if (value instanceof String) {
            try {
                int parsed = Integer.parseInt(((String) value).trim());
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void handleTeamGenerators(Arena arena) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGeneratorConfig();
        for (Team team : arena.getTeams()) {
            if (team.getGenerators().isEmpty())
                continue;

            int forgeLevel = team.getUpgradeLevel("forge");
            String group = arena.getMode().getDisplayName();

            String path = "team_generators." + group + ".levels." + forgeLevel;
            ConfigurationSection levelData = config.getConfigurationSection(path);

            if (levelData == null) {
                path = "team_generators.Default.levels." + forgeLevel;
                levelData = config.getConfigurationSection(path);
            }

            if (levelData == null) {
                levelData = config.getConfigurationSection("team_generators.Default.levels.0");
            }

            if (levelData != null) {
                for (String key : levelData.getKeys(false)) {
                    ConfigurationSection resBounds = levelData.getConfigurationSection(key);
                    if (resBounds == null)
                        continue;

                    String matName = resBounds.getString("item");
                    if (matName == null)
                        continue;
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null)
                        continue;

                    double delay = resBounds.getDouble("delay", 1.0);
                    int limit = resBounds.getInt("limit", 64);
                    int maxTicks = (int) (delay * 20);

                    int currentCd = team.getResourceCooldowns().getOrDefault(mat.name(), 0);
                    currentCd++;

                    if (currentCd >= Math.max(1, maxTicks)) {
                        currentCd = 0;
                        spawnItem(team.getGenerators(), mat, limit);
                    }
                    team.getResourceCooldowns().put(mat.name(), currentCd);
                }
            }

            if (team.getUpgradeLevel("heal_pool") > 0) {
                if (arena.getTicks() == 0) {
                    team.getMembers().forEach(m -> {
                        if (m.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                            if (isInside(m.getLocation(), team.getBasePos1(), team.getBasePos2(), 5)) {
                                m.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                        org.bukkit.potion.PotionEffectType.REGENERATION, 40, 0, false, false));
                            }
                        }
                    });
                }
            }
        }
    }

    private boolean isInside(Location loc, Location p1, Location p2, int buffer) {
        if (p1 == null || p2 == null || loc == null)
            return false;
        if (!loc.getWorld().getName().equals(p1.getWorld().getName()))
            return false;

        double minX = Math.min(p1.getX(), p2.getX()) - buffer;
        double maxX = Math.max(p1.getX(), p2.getX()) + buffer;
        double minY = Math.min(p1.getY(), p2.getY()) - buffer;
        double maxY = Math.max(p1.getY(), p2.getY()) + buffer;
        double minZ = Math.min(p1.getZ(), p2.getZ()) - buffer;
        double maxZ = Math.max(p1.getZ(), p2.getZ()) + buffer;

        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private void handleGlobalGenerators(Arena arena) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGeneratorConfig();
        String group = arena.getMode().getDisplayName();

        int dTier = arena.getDiamondTier();
        String dTierKey = "tier" + (dTier == 1 ? "I" : dTier == 2 ? "II" : "III");
        int dDelaySec = config.getInt(group + ".diamond." + dTierKey + ".delay", -1);
        if (dDelaySec == -1)
            dDelaySec = config.getInt("Default.diamond." + dTierKey + ".delay", 30);
        int dDelay = dDelaySec * 20;

        Material dMat = Material.DIAMOND;
        int dLimit = config.getInt(group + ".diamond." + dTierKey + ".spawn-limit", -1);
        if (dLimit == -1)
            dLimit = config.getInt("Default.diamond." + dTierKey + ".spawn-limit", 4);

        arena.setDiamondCooldown(arena.getDiamondCooldown() + 1);
        if (arena.getDiamondCooldown() >= dDelay) {
            arena.setDiamondCooldown(0);
            spawnItem(arena.getDiamondGenerators(), dMat, dLimit);
        }

        int eTier = arena.getEmeraldTier();
        String eTierKey = "tier" + (eTier == 1 ? "I" : eTier == 2 ? "II" : "III");
        int eDelaySec = config.getInt(group + ".emerald." + eTierKey + ".delay", -1);
        if (eDelaySec == -1)
            eDelaySec = config.getInt("Default.emerald." + eTierKey + ".delay", 60);
        int eDelay = eDelaySec * 20;

        Material eMat = Material.EMERALD;

        int eLimit = config.getInt(group + ".emerald." + eTierKey + ".spawn-limit", -1);
        if (eLimit == -1)
            eLimit = config.getInt("Default.emerald." + eTierKey + ".spawn-limit", 2);

        arena.setEmeraldCooldown(arena.getEmeraldCooldown() + 1);
        if (arena.getEmeraldCooldown() >= eDelay) {
            arena.setEmeraldCooldown(0);
            spawnItem(arena.getEmeraldGenerators(), eMat, eLimit);
        }
    }

    private void updateGeneratorVisuals(Arena arena) {
        java.util.List<org.bukkit.entity.ArmorStand> visuals = plugin.getVisualizationManager()
                .getArenaGeneratorVisuals(arena.getName());
        if (visuals.isEmpty())
            return;

        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGeneratorConfig();
        double rotation = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2;
        org.bukkit.util.EulerAngle angle = new org.bukkit.util.EulerAngle(0, rotation, 0);

        for (org.bukkit.entity.ArmorStand stand : visuals) {
            if (!stand.isValid())
                continue;

            if (stand.getScoreboardTags().contains("bw_gen_visual")) {
                stand.setHeadPose(angle);
            } else if (stand.getScoreboardTags().contains("bw_gen_timer")) {
                boolean isDiamond = false;
                for (Location dLoc : arena.getDiamondGenerators()) {
                    double dX = dLoc.getX() + 0.5 - stand.getLocation().getX();
                    double dZ = dLoc.getZ() + 0.5 - stand.getLocation().getZ();
                    if ((dX * dX + dZ * dZ) < 1.0) {
                        isDiamond = true;
                        break;
                    }
                }

                    int cooldown = isDiamond ? arena.getDiamondCooldown() : arena.getEmeraldCooldown();
                    int tier = isDiamond ? arena.getDiamondTier() : arena.getEmeraldTier();
                    String type = isDiamond ? "diamond" : "emerald";

                    String group = arena.getMode().getDisplayName();
                    String tierKey = "tier" + (tier == 1 ? "I" : tier == 2 ? "II" : "III");

                    int maxDelay = config.getInt(group + "." + type + "." + tierKey + ".delay", -1);
                    if (maxDelay == -1) {
                        maxDelay = config.getInt("Default." + type + "." + tierKey + ".delay", isDiamond ? 30 : 60);
                    }
                    int maxTicks = maxDelay * 20;

                    int limit = config.getInt(group + "." + type + "." + tierKey + ".spawn-limit", -1);
                    if (limit == -1) {
                        limit = config.getInt("Default." + type + "." + tierKey + ".spawn-limit", isDiamond ? 4 : 2);
                    }

                    boolean isFull = false;
                    Location baseLoc = stand.getLocation().getBlock().getLocation();
                    final boolean finalIsDiamond = isDiamond;
                    if (limit > 0) {
                        double sum = stand.getWorld().getNearbyEntities(baseLoc.add(0.5, 0.5, 0.5), 1.5, 4.0, 1.5)
                                .stream()
                                .filter(e -> {
                                    if (!(e instanceof org.bukkit.entity.Item))
                                        return false;
                                    org.bukkit.entity.Item it = (org.bukkit.entity.Item) e;
                                    return it.getItemStack().getType() == (finalIsDiamond ? Material.DIAMOND
                                            : Material.EMERALD);
                                })
                                .map(e -> (org.bukkit.entity.Item) e)
                                .mapToInt(i -> i.getItemStack().getAmount())
                                .sum();
                        if (sum >= limit)
                            isFull = true;
                    }

                    if (isFull) {
                        stand.setCustomName(plugin.getLanguageManager().getMessage(null, "generator-limit-reached"));
                    } else {
                        int remaining = Math.max(0, (maxTicks - cooldown) / 20);
                        String timerFmt = plugin.getLanguageManager().getMessage(null, "generator-spawning-in");
                        stand.setCustomName(timerFmt.replace("%seconds%", String.valueOf(remaining)));
                    }

                updateNameHologram(arena, stand, isDiamond, tier);
            }
        }
    }

    private void updateNameHologram(Arena arena, org.bukkit.entity.ArmorStand timerStand, boolean isDiamond, int tier) {
        String key = isDiamond ? "diamond" : "emerald";
        String baseName = plugin.getLanguageManager().getMessage(null, "generator-" + key + "-name");

        String tierSuffix = "";
        if (tier > 1) {
            String fmt = plugin.getLanguageManager().getMessage(null, "generator-tier-suffix");
            String roman = tier == 2 ? "II" : "III";
            tierSuffix = fmt.replace("%tier%", roman);
        }

        final String fullName = baseName + tierSuffix;

        timerStand.getNearbyEntities(0.5, 2.0, 0.5).stream()
                .filter(e -> {
                    if (!(e instanceof org.bukkit.entity.ArmorStand))
                        return false;
                    org.bukkit.entity.ArmorStand s = (org.bukkit.entity.ArmorStand) e;
                    return s.getScoreboardTags().contains("bw_gen_hologram");
                })
                .map(e -> (org.bukkit.entity.ArmorStand) e)
                .findFirst()
                .ifPresent(s -> s.setCustomName(fullName));
    }

    private void spawnItem(java.util.List<Location> locations, Material material, int limit) {
        java.util.Set<Location> uniqueLocations = new java.util.HashSet<>();
        for (Location loc : locations) {
            if (loc == null || loc.getWorld() == null)
                continue;
            Location blockLoc = loc.getBlock().getLocation();
            if (uniqueLocations.contains(blockLoc))
                continue;
            uniqueLocations.add(blockLoc);

            // Ensure the chunk is loaded so getNearbyEntities and dropItem work correctly
            // even when no player is near the generator.
            int chunkX = blockLoc.getBlockX() >> 4;
            int chunkZ = blockLoc.getBlockZ() >> 4;
            if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                loc.getWorld().loadChunk(chunkX, chunkZ);
            }

            if (limit > 0) {
                if (loc.getWorld().getNearbyEntities(blockLoc.clone().add(0.5, 1.5, 0.5), 1.5, 4.0, 1.5).stream()
                        .filter(e -> {
                            if (!(e instanceof org.bukkit.entity.Item))
                                return false;
                            org.bukkit.entity.Item i = (org.bukkit.entity.Item) e;
                            return i.getItemStack().getType() == material;
                        })
                        .map(e -> (org.bukkit.entity.Item) e)
                        .mapToInt(i -> i.getItemStack().getAmount())
                        .sum() >= limit) {
                    continue;
                }
            }

            ItemStack stack = new ItemStack(material);
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getLanguageManager().getItemName(null, material));
                stack.setItemMeta(meta);
            }
            loc.getWorld().dropItem(blockLoc.clone().add(0.5, 2.0, 0.5), stack)
                    .setVelocity(new Vector(0, 0, 0));
        }
    }

    private void handleMobAI(Arena arena) {
        if (arena.getTicks() % 10 != 0)
            return;

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(arena.getWorldName() != null ? arena.getWorldName() : "");
        if (world == null)
            return;

        for (org.bukkit.entity.IronGolem golem : world.getEntitiesByClass(org.bukkit.entity.IronGolem.class)) {
            if (!golem.getScoreboardTags().contains("bw_mob"))
                continue;

            String mobTeamTag = golem.getScoreboardTags().stream()
                    .filter(t -> t.startsWith("team_")).findFirst().orElse(null);

            for (Team team : arena.getTeams()) {
                if (mobTeamTag != null && mobTeamTag.equals("team_" + team.getName()))
                    continue;

                if (team.getBasePos1() != null && team.getBasePos2() != null) {
                    if (isInside(golem.getLocation(), team.getBasePos1(), team.getBasePos2(), 3)) {
                        Location center = team.getSpawnLocation() != null ? team.getSpawnLocation()
                                : team.getBedLocation();
                        if (center != null) {
                            Vector push = golem.getLocation().toVector().subtract(center.toVector()).normalize()
                                    .multiply(0.8).setY(0.4);
                            golem.setVelocity(push);
                        }
                    }
                }
            }
        }
    }

    private void refreshTeamUpgrades(Arena arena) {
        if (arena.getTicks() % 100 != 0)
            return;

        for (Team team : arena.getTeams()) {
            int hasteLevel = team.getUpgradeLevel("haste");
            if (hasteLevel > 0) {
                for (Player m : team.getMembers()) {
                    if (m.isOnline() && m.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                        m.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.FAST_DIGGING, 400, hasteLevel - 1, false, false));
                    }
                }
            }
        }
    }
}
