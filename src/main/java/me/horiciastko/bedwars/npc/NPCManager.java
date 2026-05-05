package me.horiciastko.bedwars.npc;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.logic.DatabaseManager;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import me.horiciastko.bedwars.utils.SerializationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NPCManager {

    private final BedWars plugin;
    private final Map<Arena, List<BedWarsNPC>> activeNPCs = new HashMap<>();
    private final Map<UUID, BedWarsNPC> npcLookup = new HashMap<>();
    private final Map<BedWarsNPC, Integer> standaloneNpcIds = new HashMap<>();
    private final Map<Integer, CitizensNPCImpl> citizensNpcImplMap = new HashMap<>();

    public NPCManager(BedWars plugin) {
        this.plugin = plugin;
    }

    public void spawnNPCs(Arena arena) {
        removeNPCs(arena);
        cleanupArenaNpcResidue(arena);

        List<BedWarsNPC> npcs = new ArrayList<>();
        Set<String> spawnedLocations = new HashSet<>();

        for (Team team : arena.getTeams()) {
            if (team.getShopLocation() != null) {
                Location loc = team.getShopLocation();
                String key = "shop_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
                if (!spawnedLocations.contains(key)) {
                    cleanupSpawnLocation(loc);
                    BedWarsNPC npc = createNPC(arena, loc, "shop");
                    if (npc != null) {
                        npcs.add(npc);
                        spawnedLocations.add(key);
                    }
                }
            }
            if (team.getUpgradeLocation() != null) {
                Location loc = team.getUpgradeLocation();
                String key = "upgrades_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
                if (!spawnedLocations.contains(key)) {
                    cleanupSpawnLocation(loc);
                    BedWarsNPC npc = createNPC(arena, loc, "upgrades");
                    if (npc != null) {
                        npcs.add(npc);
                        spawnedLocations.add(key);
                    }
                }
            }
        }

        activeNPCs.put(arena, npcs);

        String npcType = plugin.getSupportManager().isCitizensEnabled() ? "Citizens" : "Vanilla";
        plugin.getLogger()
                .info("Spawned " + npcs.size() + " NPCs for arena " + arena.getName() + " using " + npcType + " mode.");
    }

    private void cleanupSpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        // Y-radius of 6.0 is needed to catch all hologram armor stands:
        // holograms start at entityHeight+0.3 above the NPC and stack by 0.25 per line,
        // so with multiple description lines they can reach 3+ blocks above the NPC position.
        location.getWorld().getNearbyEntities(location, 1.25, 6.0, 1.25).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Villager) {
                BedWarsNPC npc = npcLookup.get(entity.getUniqueId());
                if (npc != null) {
                    npc.remove();
                } else {
                    entity.remove();
                }
                return;
            }

            try {
                if (entity.getScoreboardTags().contains("bw_npc")
                        || entity.getScoreboardTags().contains("bw_npc_hologram")) {
                    entity.remove();
                }
            } catch (NoSuchMethodError ignored) {
                BedWarsNPC npc = npcLookup.get(entity.getUniqueId());
                if (npc != null) {
                    npc.remove();
                }
            }
        });
    }

    public void cleanupArenaNpcResidue(Arena arena) {
        if (arena == null) {
            return;
        }

        for (Team team : arena.getTeams()) {
            cleanupSpawnLocation(team.getShopLocation());
            cleanupSpawnLocation(team.getUpgradeLocation());
        }
    }


    private BedWarsNPC createNPC(Arena arena, Location location, String type) {
        BedWarsNPC npc;

        if (plugin.getSupportManager().isCitizensEnabled()) {
            npc = new CitizensNPCImpl(plugin, type);
        } else {
            npc = new VanillaNPCImpl(plugin, type);
        }

        if (npc != null) {
            npc.spawn(location);
        }
        return npc;
    }

    public BedWarsNPC createStandaloneNPC(Location location, String type) {
        return createStandaloneNPC(location, type, true);
    }

    private BedWarsNPC createStandaloneNPC(Location location, String type, boolean persistInDatabase) {
        removeStandaloneDuplicates(location, type);

        BedWarsNPC npc;

        if (plugin.getSupportManager().isCitizensEnabled()) {
            npc = new CitizensNPCImpl(plugin, type);
        } else {
            npc = new VanillaNPCImpl(plugin, type);
        }

        if (npc != null) {
            npc.spawn(location);
            if (persistInDatabase) {
                int id = plugin.getDatabaseManager().saveStandaloneNPC(type,
                        SerializationUtils.locationToString(location));
                if (id > 0) {
                    standaloneNpcIds.put(npc, id);
                }
            }
        }
        return npc;
    }

    private void removeStandaloneDuplicates(Location location, String type) {
        if (location == null || location.getWorld() == null || type == null) {
            return;
        }

        String standaloneKey = getStandaloneKey(location, type);
        for (Map.Entry<BedWarsNPC, Integer> entry : new HashMap<>(standaloneNpcIds).entrySet()) {
            BedWarsNPC npc = entry.getKey();
            if (npc == null || npc.getLocation() == null) {
                continue;
            }
            if (standaloneKey.equals(getStandaloneKey(npc.getLocation(), npc.getType()))) {
                removeNPC(npc);
            }
        }

        location.getWorld().getNearbyEntities(location, 0.35, 1.5, 0.35).forEach(entity -> {
            try {
                if (entity.getScoreboardTags().contains("bw_npc")
                        || entity.getScoreboardTags().contains("bw_npc_hologram")) {
                    entity.remove();
                }
            } catch (NoSuchMethodError ignored) {
                BedWarsNPC npc = npcLookup.get(entity.getUniqueId());
                if (npc != null && standaloneKey.equals(getStandaloneKey(npc.getLocation(), npc.getType()))) {
                    npc.remove();
                }
            }
        });
    }

    private String getStandaloneKey(Location location, String type) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }

        return location.getWorld().getName().toLowerCase() + ':'
                + location.getBlockX() + ':'
                + location.getBlockY() + ':'
                + location.getBlockZ() + ':'
                + (type != null ? type.toLowerCase() : "unknown");
    }

    private BedWarsNPC createStandaloneNPCFromDatabase(int id, Location location, String type) {
        BedWarsNPC npc = createStandaloneNPC(location, type, false);
        if (npc != null) {
            standaloneNpcIds.put(npc, id);
        }
        return npc;
    }

    public void removeNPCs(Arena arena) {
        List<BedWarsNPC> npcs = activeNPCs.remove(arena);
        if (npcs != null) {
            npcs.forEach(BedWarsNPC::remove);
        }
    }

    public void registerEntity(UUID uuid, BedWarsNPC npc) {
        npcLookup.put(uuid, npc);
    }

    public void unregisterEntity(UUID uuid) {
        npcLookup.remove(uuid);
    }

    public void registerCitizensImpl(int npcId, CitizensNPCImpl impl) {
        citizensNpcImplMap.put(npcId, impl);
    }

    public void unregisterCitizensImpl(int npcId) {
        citizensNpcImplMap.remove(npcId);
    }

    public CitizensNPCImpl getCitizensImpl(int npcId) {
        return citizensNpcImplMap.get(npcId);
    }

    public BedWarsNPC getNPCByEntity(UUID uuid) {
        return npcLookup.get(uuid);
    }

    public BedWarsNPC getNearestNPC(org.bukkit.entity.Player player, double maxDistance) {
        org.bukkit.Location eyeLoc = player.getEyeLocation();
        org.bukkit.util.Vector direction = eyeLoc.getDirection();
        
        BedWarsNPC nearest = null;
        double nearestDist = maxDistance;
        
        for (BedWarsNPC npc : npcLookup.values()) {
            org.bukkit.Location npcLoc = npc.getLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(eyeLoc.getWorld())) {
                continue;
            }
            
            double distance = eyeLoc.distance(npcLoc);
            if (distance > maxDistance) {
                continue;
            }
            
            org.bukkit.util.Vector toNPC = npcLoc.toVector().subtract(eyeLoc.toVector()).normalize();
            double dot = toNPC.dot(direction);
            
            if (dot > 0.98 && distance < nearestDist) {
                nearest = npc;
                nearestDist = distance;
            }
        }
        
        return nearest;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfigManager().getNpcConfig();
    }


    public List<String> getAvailableTypes() {
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("types");
        if (section == null)
            return new ArrayList<>();
        return new ArrayList<>(section.getKeys(false));
    }

    public boolean isUsingCitizens() {
        return plugin.getSupportManager().isCitizensEnabled();
    }

    public void removeNPC(BedWarsNPC npc) {
        if (npc == null) {
            return;
        }

        Integer standaloneId = standaloneNpcIds.remove(npc);
        if (standaloneId != null) {
            plugin.getDatabaseManager().deleteStandaloneNPC(standaloneId);
        }

        for (List<BedWarsNPC> npcs : activeNPCs.values()) {
            npcs.remove(npc);
        }

        npc.remove();
    }

    public boolean removeNPCById(int id) {
        BedWarsNPC targetNPC = null;

        for (Map.Entry<BedWarsNPC, Integer> entry : standaloneNpcIds.entrySet()) {
            if (entry.getValue().equals(id)) {
                targetNPC = entry.getKey();
                break;
            }
        }

        if (targetNPC != null) {
            removeNPC(targetNPC);
            return true;
        }

        // NPC may not be loaded in memory (e.g. world was not loaded at startup)
        // Check the database directly and delete if it exists there
        List<DatabaseManager.StandaloneNPCRecord> dbRecords = plugin.getDatabaseManager().loadStandaloneNPCs();
        for (DatabaseManager.StandaloneNPCRecord record : dbRecords) {
            if (record.getId() == id) {
                plugin.getDatabaseManager().deleteStandaloneNPC(id);
                return true;
            }
        }

        return false;
    }

    private void cleanupOrphanedHolograms() {
        int removed = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.ArmorStand) {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) entity;
                    try {
                        java.util.Set<String> tags = stand.getScoreboardTags();
                        // Remove NPC holograms AND setup-visualization holograms (bw_hologram).
                        // Game-bed holograms use bw_game_hologram and are managed separately.
                        if (tags.contains("bw_npc_hologram") || tags.contains("bw_hologram")) {
                            stand.remove();
                            removed++;
                        }
                    } catch (NoSuchMethodError ignored) {
                    }
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleaned up " + removed + " orphaned hologram entities.");
        }
    }

    private void cleanupOrphanedNPCEntities() {
        final java.util.concurrent.atomic.AtomicInteger removed = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                try {
                    if (entity.getScoreboardTags().contains("bw_npc")) {
                        entity.remove();
                        removed.incrementAndGet();
                    }
                } catch (NoSuchMethodError ignored) {
                }
            }
        }
        
        if (plugin.getSupportManager().isCitizensEnabled()) {
            try {
                java.util.List<net.citizensnpcs.api.npc.NPC> npcsToRemove = new java.util.ArrayList<>();

                for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                    try {
                        // Check persistent data first (survives server restarts)
                        boolean isBwNpc = npc.data().get("bw_npc", false);
                        // Fallback: check scoreboard tag on live entity (same session)
                        if (!isBwNpc && npc.getEntity() != null) {
                            isBwNpc = npc.getEntity().getScoreboardTags().contains("bw_npc");
                        }
                        if (isBwNpc) {
                            npcsToRemove.add(npc);
                        }
                    } catch (Exception ignored) {
                    }
                }

                for (net.citizensnpcs.api.npc.NPC npc : npcsToRemove) {
                    npc.destroy();
                    removed.incrementAndGet();
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Could not cleanup Citizens NPCs: " + e.getMessage());
            }
        }
        
        if (removed.get() > 0) {
            plugin.getLogger().info("Cleaned up " + removed.get() + " orphaned NPC entities.");
        }
    }

    public void loadStandaloneNPCsFromDatabase() {
        cleanupOrphanedHolograms();
        cleanupOrphanedNPCEntities();
        
        List<DatabaseManager.StandaloneNPCRecord> records = plugin.getDatabaseManager().loadStandaloneNPCs();
        int loaded = 0;
        Set<String> seenKeys = new HashSet<>();

        for (DatabaseManager.StandaloneNPCRecord record : records) {
            Location location = SerializationUtils.stringToLocation(record.getLocation());
            if (location == null || location.getWorld() == null) {
                plugin.getLogger().warning("Skipping standalone NPC id=" + record.getId()
                        + " due to invalid location/world.");
                continue;
            }

            String standaloneKey = getStandaloneKey(location, record.getType());
            if (!seenKeys.add(standaloneKey)) {
                plugin.getDatabaseManager().deleteStandaloneNPC(record.getId());
                continue;
            }

            BedWarsNPC npc = createStandaloneNPCFromDatabase(record.getId(), location, record.getType());
            if (npc != null) {
                loaded++;
            }
        }

        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " standalone NPCs from database.");
        }
    }

    public void removeAllNPCs() {
        Set<BedWarsNPC> allNpcs = new HashSet<>(npcLookup.values());
        for (List<BedWarsNPC> npcs : activeNPCs.values()) {
            allNpcs.addAll(npcs);
        }
        allNpcs.forEach(BedWarsNPC::remove);
        activeNPCs.clear();
        npcLookup.clear();
        standaloneNpcIds.clear();
    }

    public void refreshAllNPCs() {
        cleanupOrphanedHolograms();
        
        List<Arena> arenasToRespawn = new ArrayList<>(activeNPCs.keySet());

        Set<BedWarsNPC> arenaNpcs = new HashSet<>();
        for (List<BedWarsNPC> npcs : activeNPCs.values()) {
            arenaNpcs.addAll(npcs);
        }

        List<Map.Entry<Integer, Map.Entry<Location, String>>> standaloneSnapshots = new ArrayList<>();
        for (Map.Entry<BedWarsNPC, Integer> entry : new HashMap<>(standaloneNpcIds).entrySet()) {
            BedWarsNPC npc = entry.getKey();
            if (npc != null && !arenaNpcs.contains(npc)) {
                Location loc = npc.getLocation();
                if (loc != null) {
                    standaloneSnapshots.add(new AbstractMap.SimpleEntry<>(entry.getValue(),
                            new AbstractMap.SimpleEntry<>(loc.clone(), npc.getType())));
                }
            }
        }

        removeAllNPCs();

        for (Arena arena : arenasToRespawn) {
            spawnNPCs(arena);
        }

        for (Map.Entry<Integer, Map.Entry<Location, String>> snapshot : standaloneSnapshots) {
            BedWarsNPC npc = createStandaloneNPC(snapshot.getValue().getKey(), snapshot.getValue().getValue(), false);
            if (npc != null) {
                standaloneNpcIds.put(npc, snapshot.getKey());
            }
        }
    }

    public List<DatabaseManager.StandaloneNPCRecord> getAllStandaloneNPCsFromDB() {
        return plugin.getDatabaseManager().loadStandaloneNPCs();
    }

    public Map<BedWarsNPC, Integer> getLoadedStandaloneNPCs() {
        return new HashMap<>(standaloneNpcIds);
    }
}
