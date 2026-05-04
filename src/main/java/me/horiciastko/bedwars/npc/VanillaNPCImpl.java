package me.horiciastko.bedwars.npc;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.gui.JoinGUI;
import me.horiciastko.bedwars.gui.ShopGUI;
import me.horiciastko.bedwars.gui.UpgradeGUI;
import me.horiciastko.bedwars.models.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VanillaNPCImpl implements BedWarsNPC {

    private final BedWars plugin;
    private final String type;
    private Entity npcEntity;
    private final List<ArmorStand> hologramLines = new ArrayList<>();
    private UUID entityUUID;

    private Object nmsPlayer;
    private boolean isPlayerNPC = false;
    private String nmsVersion;

    public VanillaNPCImpl(BedWars plugin, String type) {
        this.plugin = plugin;
        this.type = type;
        detectNMSVersion();
    }

    private void detectNMSVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String[] parts = packageName.split("\\.");
        if (parts.length >= 4) {
            nmsVersion = parts[3];
        } else {
            nmsVersion = null;
        }
    }

    @Override
    public void spawn(Location location) {
        String title = plugin.getNpcManager().getConfig().getString("types." + type + ".title", type);
        String skin = plugin.getNpcManager().getConfig().getString("types." + type + ".skin", "");
        String entityTypeStr = plugin.getNpcManager().getConfig().getString("types." + type + ".entity-type", "PLAYER");

        List<String> descriptionLines = new ArrayList<>();
        if (plugin.getNpcManager().getConfig().isList("types." + type + ".description")) {
            descriptionLines = plugin.getNpcManager().getConfig().getStringList("types." + type + ".description");
        } else {
            String s = plugin.getNpcManager().getConfig().getString("types." + type + ".description", "");
            if (!s.isEmpty())
                descriptionLines.add(s);
        }

        EntityType entType = EntityType.PLAYER;
        try {
            entType = EntityType.valueOf(entityTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Invalid entity type '" + entityTypeStr + "' for NPC " + type + ". Using PLAYER.");
        }

        if (entType == EntityType.PLAYER) {
            boolean success = trySpawnPlayerNPC(location, title, skin);
            if (success) {
                isPlayerNPC = true;
                spawnHologram(location, title, descriptionLines);
                return;
            }
            plugin.getLogger().info("Player NPC not available, falling back to Villager for: " + type);
            entType = EntityType.VILLAGER;
        }

        spawnVillagerNPC(location, entType, title, descriptionLines);
    }

    private boolean trySpawnPlayerNPC(Location location, String title, String skinName) {
        try {
            String[] skinData = getSkinData(skinName);

            if (nmsVersion == null) {
                return trySpawnModernPlayerNPC(location, title, skinData);
            }

            return trySpawnLegacyPlayerNPC(location, title, skinData);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create Player NPC: " + e.getMessage());
            return false;
        }
    }

    private boolean trySpawnModernPlayerNPC(Location location, String title, String[] skinData) {
        try {
            Object minecraftServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());

            Object craftWorld = location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(),
                            ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title)));

            if (skinData != null && skinData.length >= 2 && skinData[0] != null) {
                applySkinToProfile(gameProfile, skinData);
            }

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");

            Constructor<?> playerConstructor = serverPlayerClass.getConstructor(
                    minecraftServerClass, serverLevelClass, gameProfileClass);

            nmsPlayer = playerConstructor.newInstance(minecraftServer, craftWorld, gameProfile);

            Method setPos = serverPlayerClass.getMethod("setPos", double.class, double.class, double.class);
            setPos.invoke(nmsPlayer, location.getX(), location.getY(), location.getZ());

            Method setRot = serverPlayerClass.getMethod("setRot", float.class, float.class);
            setRot.invoke(nmsPlayer, location.getYaw(), location.getPitch());

            Method getUUID = serverPlayerClass.getMethod("getUUID");
            entityUUID = (UUID) getUUID.invoke(nmsPlayer);

            plugin.getNpcManager().registerEntity(entityUUID, this);

            sendSpawnPackets();

            schedulePacketUpdates();

            return true;

        } catch (Exception e) {
            plugin.getLogger().fine("Modern NPC creation failed: " + e.getMessage());
            return false;
        }
    }

    private boolean trySpawnLegacyPlayerNPC(Location location, String title, String[] skinData) {
        try {
            String nmsPackage = "net.minecraft.server." + nmsVersion;
            String craftPackage = "org.bukkit.craftbukkit." + nmsVersion;

            Class<?> craftServerClass = Class.forName(craftPackage + ".CraftServer");
            Object craftServer = craftServerClass.cast(Bukkit.getServer());
            Method getServerMethod = craftServerClass.getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(craftServer);

            Class<?> craftWorldClass = Class.forName(craftPackage + ".CraftWorld");
            Object craftWorld = craftWorldClass.cast(location.getWorld());
            Method getHandleMethod = craftWorldClass.getMethod("getHandle");
            Object worldServer = getHandleMethod.invoke(craftWorld);

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(),
                            ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title)));

            if (skinData != null && skinData.length >= 2 && skinData[0] != null) {
                applySkinToProfile(gameProfile, skinData);
            }

            Class<?> entityPlayerClass = Class.forName(nmsPackage + ".EntityPlayer");
            Class<?> minecraftServerClass = Class.forName(nmsPackage + ".MinecraftServer");
            Class<?> worldServerClass = Class.forName(nmsPackage + ".WorldServer");
            Class<?> playerInteractManagerClass = Class.forName(nmsPackage + ".PlayerInteractManager");

            Constructor<?> pimConstructor = playerInteractManagerClass.getConstructor(worldServerClass);
            Object playerInteractManager = pimConstructor.newInstance(worldServer);

            Constructor<?> entityPlayerConstructor = entityPlayerClass.getConstructor(
                    minecraftServerClass, worldServerClass, gameProfileClass, playerInteractManagerClass);
            nmsPlayer = entityPlayerConstructor.newInstance(minecraftServer, worldServer, gameProfile,
                    playerInteractManager);

            Method setLocation = entityPlayerClass.getMethod("setLocation",
                    double.class, double.class, double.class, float.class, float.class);
            setLocation.invoke(nmsPlayer, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());

            Method getUniqueID = entityPlayerClass.getMethod("getUniqueID");
            entityUUID = (UUID) getUniqueID.invoke(nmsPlayer);

            plugin.getNpcManager().registerEntity(entityUUID, this);

            sendSpawnPacketsLegacy();

            schedulePacketUpdates();

            return true;

        } catch (Exception e) {
            plugin.getLogger().fine("Legacy NPC creation failed: " + e.getMessage());
            return false;
        }
    }

    private void applySkinToProfile(Object gameProfile, String[] skinData) {
        try {
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Method getProperties = gameProfile.getClass().getMethod("getProperties");
            Object propertyMap = getProperties.invoke(gameProfile);

            Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
            Object skinProperty = propertyConstructor.newInstance("textures", skinData[0], skinData[1]);

            Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(propertyMap, "textures", skinProperty);

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to apply skin: " + e.getMessage());
        }
    }

    private String[] getSkinData(String skinName) {
        if (skinName == null || skinName.isEmpty()) {
            return null;
        }

        try {
            URL uuidUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + skinName);
            HttpURLConnection uuidConn = (HttpURLConnection) uuidUrl.openConnection();
            uuidConn.setConnectTimeout(3000);
            uuidConn.setReadTimeout(3000);

            if (uuidConn.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(uuidConn.getInputStream());
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
                reader.close();

                String response = sb.toString();
                String uuid = parseJsonValue(response, "id");

                if (uuid != null) {
                    URL skinUrl = new URL(
                            "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                    HttpURLConnection skinConn = (HttpURLConnection) skinUrl.openConnection();
                    skinConn.setConnectTimeout(3000);
                    skinConn.setReadTimeout(3000);

                    if (skinConn.getResponseCode() == 200) {
                        reader = new InputStreamReader(skinConn.getInputStream());
                        sb = new StringBuilder();
                        while ((c = reader.read()) != -1) {
                            sb.append((char) c);
                        }
                        reader.close();

                        response = sb.toString();
                        String value = parseNestedJsonValue(response, "value");
                        String signature = parseNestedJsonValue(response, "signature");

                        if (value != null) {
                            return new String[] { value, signature };
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to fetch skin for " + skinName + ": " + e.getMessage());
        }

        return null;
    }

    private String parseJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1)
            return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1)
            return null;
        return json.substring(start, end);
    }

    private String parseNestedJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            search = "\"" + key + "\" : \"";
            start = json.indexOf(search);
        }
        if (start == -1)
            return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1)
            return null;
        return json.substring(start, end);
    }

    private void sendSpawnPackets() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendSpawnPacketToPlayer(player);
        }
    }

    private void sendSpawnPacketsLegacy() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendSpawnPacketToPlayerLegacy(player);
        }
    }

    private void sendSpawnPacketToPlayer(Player player) {
        try {
            Object connection = getPlayerConnection(player);
            if (connection == null)
                return;

            Class<?> clientboundPlayerInfoPacket = Class
                    .forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");

            Method of = clientboundPlayerInfoPacket.getMethod("createPlayerInitializing", java.util.Collection.class);
            Object infoPacket = of.invoke(null, java.util.Collections.singletonList(nmsPlayer));

            sendPacket(connection, infoPacket);

            Class<?> addEntityPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Constructor<?> addEntityConstructor = addEntityPacket.getConstructor(
                    Class.forName("net.minecraft.world.entity.Entity"));
            Object spawnPacket = addEntityConstructor.newInstance(nmsPlayer);

            sendPacket(connection, spawnPacket);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
            }, 20L);

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to send spawn packet: " + e.getMessage());
        }
    }

    private void sendSpawnPacketToPlayerLegacy(Player player) {
        try {
            String nmsPackage = "net.minecraft.server." + nmsVersion;
            Object connection = getPlayerConnectionLegacy(player);
            if (connection == null)
                return;

            Class<?> packetPlayOutPlayerInfo = Class.forName(nmsPackage + ".PacketPlayOutPlayerInfo");
            Class<?> enumPlayerInfoAction = Class.forName(nmsPackage + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
            Object addPlayer = enumPlayerInfoAction.getField("ADD_PLAYER").get(null);

            Constructor<?> playerInfoConstructor = packetPlayOutPlayerInfo.getConstructor(
                    enumPlayerInfoAction, Class.forName("[L" + nmsPackage + ".EntityPlayer;"));
            Object playerInfoPacket = playerInfoConstructor.newInstance(addPlayer,
                    java.lang.reflect.Array.newInstance(Class.forName(nmsPackage + ".EntityPlayer"), 1));
            java.lang.reflect.Array.set(
                    java.lang.reflect.Array.newInstance(Class.forName(nmsPackage + ".EntityPlayer"), 1),
                    0, nmsPlayer);

            sendPacketLegacy(connection, playerInfoPacket);

            Class<?> packetPlayOutNamedEntitySpawn = Class.forName(nmsPackage + ".PacketPlayOutNamedEntitySpawn");
            Constructor<?> namedSpawnConstructor = packetPlayOutNamedEntitySpawn.getConstructor(
                    Class.forName(nmsPackage + ".EntityHuman"));
            Object spawnPacket = namedSpawnConstructor.newInstance(nmsPlayer);

            sendPacketLegacy(connection, spawnPacket);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Object removePlayer = enumPlayerInfoAction.getField("REMOVE_PLAYER").get(null);
                    Object removePacket = playerInfoConstructor.newInstance(removePlayer,
                            java.lang.reflect.Array.newInstance(Class.forName(nmsPackage + ".EntityPlayer"), 1));
                    sendPacketLegacy(connection, removePacket);
                } catch (Exception ignored) {
                }
            }, 40L);

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to send legacy spawn packet: " + e.getMessage());
        }
    }

    private Object getPlayerConnection(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = craftPlayer.getClass().getField("connection");
            return connectionField.get(craftPlayer);
        } catch (Exception e) {
            return null;
        }
    }

    private Object getPlayerConnectionLegacy(Player player) {
        try {
            String craftPackage = "org.bukkit.craftbukkit." + nmsVersion;
            Class<?> craftPlayerClass = Class.forName(craftPackage + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Method getHandle = craftPlayerClass.getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(craftPlayer);
            Field playerConnection = entityPlayer.getClass().getField("playerConnection");
            return playerConnection.get(entityPlayer);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendPacket(Object connection, Object packet) {
        try {
            Method send = connection.getClass().getMethod("send",
                    Class.forName("net.minecraft.network.protocol.Packet"));
            send.invoke(connection, packet);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to send packet: " + e.getMessage());
        }
    }

    private void sendPacketLegacy(Object connection, Object packet) {
        try {
            String nmsPackage = "net.minecraft.server." + nmsVersion;
            Method sendPacket = connection.getClass().getMethod("sendPacket", Class.forName(nmsPackage + ".Packet"));
            sendPacket.invoke(connection, packet);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to send legacy packet: " + e.getMessage());
        }
    }

    private void schedulePacketUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (nmsPlayer == null) {
                    cancel();
                    return;
                }

            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    private void spawnVillagerNPC(Location location, EntityType entType, String title, List<String> descriptionLines) {
        if (entType == EntityType.VILLAGER) {
            Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setCanPickupItems(false);
            villager.setRemoveWhenFarAway(false);
            villager.setGravity(false);

            try {
                villager.setAware(false);
            } catch (NoSuchMethodError | Exception ignored) {
            }

            if (type.equalsIgnoreCase("shop")) {
                try {
                    villager.setProfession(Villager.Profession.WEAPONSMITH);
                } catch (Exception e) {
                }
            } else if (type.equalsIgnoreCase("upgrades")) {
                try {
                    villager.setProfession(Villager.Profession.LIBRARIAN);
                } catch (Exception e) {
                }
            }

            try {
                villager.addScoreboardTag("bw_npc");
                villager.addScoreboardTag("bw_npc_type_" + type);
            } catch (NoSuchMethodError ignored) {
            }

            npcEntity = villager;
        } else {
            Entity entity = location.getWorld().spawnEntity(location, entType);
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
                living.setAI(false);
                living.setInvulnerable(true);
                living.setSilent(true);
                living.setCollidable(false);
                living.setCanPickupItems(false);
                living.setRemoveWhenFarAway(false);
                try {
                    living.setGravity(false);
                } catch (NoSuchMethodError | Exception ignored) {
                }
            }
            try {
                entity.addScoreboardTag("bw_npc");
                entity.addScoreboardTag("bw_npc_type_" + type);
            } catch (NoSuchMethodError ignored) {
            }
            npcEntity = entity;
        }

        entityUUID = npcEntity.getUniqueId();

        plugin.getNpcManager().registerEntity(entityUUID, this);

        spawnHologram(npcEntity.getLocation(), title, descriptionLines);
    }

    private void spawnHologram(Location baseLocation, String title, List<String> descriptionLines) {
        double entityHeight = isPlayerNPC ? 1.8 : 1.95;
        double lineHeight = 0.25;

        Location holoLoc = baseLocation.clone().add(0, entityHeight + 0.3, 0);

        if (title != null && !title.isEmpty()) {
            for (int i = descriptionLines.size() - 1; i >= 0; i--) {
                ArmorStand line = spawnHologramLine(holoLoc,
                        ChatColor.translateAlternateColorCodes('&', descriptionLines.get(i)));
                hologramLines.add(line);
                holoLoc = holoLoc.add(0, lineHeight, 0);
            }

            ArmorStand titleStand = spawnHologramLine(holoLoc, ChatColor.translateAlternateColorCodes('&', title));
            hologramLines.add(titleStand);
        }
    }

    private ArmorStand spawnHologramLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);

        try {
            stand.addScoreboardTag("bw_npc_hologram");
        } catch (NoSuchMethodError ignored) {
        }

        return stand;
    }

    @Override
    public void remove() {
        for (ArmorStand stand : hologramLines) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        hologramLines.clear();

        Location npcLoc = getLocation();
        if (npcLoc != null && npcLoc.getWorld() != null) {
            npcLoc.getWorld().getNearbyEntities(npcLoc, 2.0, 3.0, 2.0).forEach(entity -> {
                if (entity instanceof ArmorStand) {
                    ArmorStand stand = (ArmorStand) entity;
                    try {
                        if (stand.getScoreboardTags().contains("bw_npc_hologram")) {
                            stand.remove();
                        }
                    } catch (NoSuchMethodError ignored) {
                    }
                }
            });
        }

        if (entityUUID != null) {
            plugin.getNpcManager().unregisterEntity(entityUUID);
        }

        if (npcEntity != null && !npcEntity.isDead()) {
            npcEntity.remove();
        }

        if (isPlayerNPC && nmsPlayer != null) {
            sendDestroyPackets();
        }

        nmsPlayer = null;
    }

    private void sendDestroyPackets() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Object connection = nmsVersion == null ? getPlayerConnection(player)
                        : getPlayerConnectionLegacy(player);
                if (connection == null)
                    continue;

                int entityId = getEntityId();
                if (entityId == -1)
                    continue;

                if (nmsVersion == null) {
                    Class<?> destroyPacketClass = Class
                            .forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
                    Constructor<?> destroyConstructor = destroyPacketClass.getConstructor(int[].class);
                    Object destroyPacket = destroyConstructor.newInstance(new int[] { entityId });
                    sendPacket(connection, destroyPacket);
                } else {
                    String nmsPackage = "net.minecraft.server." + nmsVersion;
                    Class<?> destroyPacketClass = Class.forName(nmsPackage + ".PacketPlayOutEntityDestroy");
                    Constructor<?> destroyConstructor = destroyPacketClass.getConstructor(int[].class);
                    Object destroyPacket = destroyConstructor.newInstance(new int[] { entityId });
                    sendPacketLegacy(connection, destroyPacket);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private int getEntityId() {
        try {
            Method getId = nmsPlayer.getClass().getMethod("getId");
            return (int) getId.invoke(nmsPlayer);
        } catch (Exception e) {
            try {
                Method ae = nmsPlayer.getClass().getMethod("ae");
                return (int) ae.invoke(nmsPlayer);
            } catch (Exception e2) {
                return -1;
            }
        }
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Location getLocation() {
        if (npcEntity != null && !npcEntity.isDead()) {
            return npcEntity.getLocation();
        }
        if (!hologramLines.isEmpty()) {
            ArmorStand stand = hologramLines.get(0);
            if (stand != null && !stand.isDead()) {
                return stand.getLocation().subtract(0, 2.0, 0);
            }
        }
        return null;
    }

    @Override
    public void onClick(Player player) {
        String actionType = type.toLowerCase();

        if (actionType.equalsIgnoreCase("shop")) {
            new ShopGUI().open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("upgrades")) {
            new UpgradeGUI().open(player);
            return;
        }

        if (actionType.equalsIgnoreCase("join") || actionType.equalsIgnoreCase("play")) {
            new JoinGUI().open(player);
            return;
        }

        if (actionType.equalsIgnoreCase("solo")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(Arena.ArenaMode.SOLO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("duo") || actionType.equalsIgnoreCase("doubles")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(Arena.ArenaMode.DUO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("trio") || actionType.equalsIgnoreCase("3v3v3v3")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(Arena.ArenaMode.TRIO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("quad") || actionType.equalsIgnoreCase("squad")
                || actionType.equalsIgnoreCase("4v4v4v4")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(Arena.ArenaMode.SQUAD).open(player);
            return;
        }

        String modeStr = plugin.getNpcManager().getConfig().getString("types." + type + ".mode");
        if (modeStr != null) {
            try {
                Arena.ArenaMode mode = Arena.ArenaMode.valueOf(modeStr.toUpperCase());
                new me.horiciastko.bedwars.gui.ArenaSelectorGUI(mode).open(player);
                return;
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "npc-invalid-mode").replace("%mode%", modeStr));
            }
        }
    }

    @Override
    public UUID getEntityUUID() {
        return entityUUID;
    }

    public Entity getNpcEntity() {
        return npcEntity;
    }

    public boolean isPlayerNPC() {
        return isPlayerNPC;
    }
}
