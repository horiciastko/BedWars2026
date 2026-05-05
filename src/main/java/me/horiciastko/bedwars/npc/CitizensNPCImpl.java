package me.horiciastko.bedwars.npc;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.gui.JoinGUI;
import me.horiciastko.bedwars.gui.ShopGUI;
import me.horiciastko.bedwars.gui.UpgradeGUI;
import me.horiciastko.bedwars.models.Arena;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;

public class CitizensNPCImpl implements BedWarsNPC {

    private final BedWars plugin;
    private final String type;
    private NPC npc;
    private int citizensNpcId = -1;

    public CitizensNPCImpl(BedWars plugin, String type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public void spawn(Location location) {
        String title = plugin.getNpcManager().getConfig().getString("types." + type + ".title", type);
        title = org.bukkit.ChatColor.translateAlternateColorCodes('&', title);
        String skin = plugin.getNpcManager().getConfig().getString("types." + type + ".skin", "");
        String entityTypeStr = plugin.getNpcManager().getConfig().getString("types." + type + ".entity-type", "PLAYER");

        EntityType entType = EntityType.PLAYER;
        try {
            entType = EntityType.valueOf(entityTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Invalid entity type '" + entityTypeStr + "' for NPC " + type + ". Defaulting to PLAYER.");
        }

        StringBuilder invisibleName = new StringBuilder();
        String colorChars = "0123456789abcdef";
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            invisibleName.append("§").append(colorChars.charAt(rand.nextInt(colorChars.length())));
        }
        String npcName = invisibleName.toString();
        plugin.getScoreboardManager().hideName(npcName);

        npc = CitizensAPI.getNPCRegistry().createNPC(entType, npcName);
        citizensNpcId = npc.getId();
        plugin.getNpcManager().registerCitizensImpl(citizensNpcId, this);
        npc.data().set("nameplate-visible", false);

        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(false);
        try {
            lookClose.setRange(0);
        } catch (NoSuchMethodError | Exception ignored) {
        }

        if (entType == EntityType.PLAYER && !skin.isEmpty()) {
            npc.getOrAddTrait(net.citizensnpcs.trait.SkinTrait.class).setSkinName(skin);
        }

        List<String> descriptionLines = new java.util.ArrayList<>();
        if (plugin.getNpcManager().getConfig().isList("types." + type + ".description")) {
            descriptionLines = plugin.getNpcManager().getConfig().getStringList("types." + type + ".description");
        } else {
            String s = plugin.getNpcManager().getConfig().getString("types." + type + ".description", "");
            if (!s.isEmpty())
                descriptionLines.add(s);
        }

        npc.spawn(location);
        
        if (npc.getEntity() != null) {
            try {
                npc.getEntity().setGravity(false);
            } catch (NoSuchMethodError | Exception ignored) {
            }

            if (entType == EntityType.VILLAGER && npc.getEntity() instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) npc.getEntity();
                villager.setAI(false);
                villager.setCanPickupItems(false);
                try {
                    villager.setAware(false);
                } catch (NoSuchMethodError | Exception ignored) {
                }
                if (type.equalsIgnoreCase("shop")) {
                    try {
                        villager.setProfession(org.bukkit.entity.Villager.Profession.WEAPONSMITH);
                    } catch (Exception ignored) {}
                } else if (type.equalsIgnoreCase("upgrades")) {
                    try {
                        villager.setProfession(org.bukkit.entity.Villager.Profession.LIBRARIAN);
                    } catch (Exception ignored) {}
                }
            }

            if (npc.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) npc.getEntity();
                living.setAI(false);
                living.setCanPickupItems(false);
                living.setCollidable(false);
                living.setSilent(true);
                living.setRemoveWhenFarAway(false);
                try {
                    living.setInvulnerable(true);
                } catch (NoSuchMethodError | Exception ignored) {
                }
            }
            
            try {
                npc.getEntity().addScoreboardTag("bw_npc");
                npc.getEntity().addScoreboardTag("bw_npc_type_" + type);
            } catch (NoSuchMethodError | Exception ignored) {
            }
            plugin.getNpcManager().registerEntity(npc.getEntity().getUniqueId(), this);
            
            spawnHologram(npc.getEntity(), title, descriptionLines);
        }

        // Persist a marker so Citizens saves it to disk — used by cleanupOrphanedNPCEntities
        // on next server start to remove these NPCs before new ones are created.
        // (Do NOT use an anonymous Trait here — Citizens cannot deserialize anonymous classes
        //  and logs "The trait bw_xxx failed to load" every restart.)
        npc.data().set("bw_npc", true);
        npc.data().set("bw_npc_type", type);
    }

    private final java.util.List<org.bukkit.entity.ArmorStand> hologramLines = new java.util.ArrayList<>();

    private void spawnHologram(org.bukkit.entity.Entity entity, String title, List<String> descriptionLines) {
        double entityHeight = entity instanceof org.bukkit.entity.Player ? 1.8 : 1.95;
        double lineHeight = 0.25;

        Location holoLoc = entity.getLocation().clone().add(0, entityHeight + 0.3, 0);

        if (title != null && !title.isEmpty()) {
            for (int i = descriptionLines.size() - 1; i >= 0; i--) {
                org.bukkit.entity.ArmorStand line = spawnHologramLine(holoLoc,
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', descriptionLines.get(i)));
                hologramLines.add(line);
                holoLoc = holoLoc.add(0, lineHeight, 0);
            }

            org.bukkit.entity.ArmorStand titleStand = spawnHologramLine(holoLoc, 
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', title));
            hologramLines.add(titleStand);
        }
    }

    private org.bukkit.entity.ArmorStand spawnHologramLine(Location location, String text) {
        org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        try {
            stand.addScoreboardTag("bw_hologram");
            stand.addScoreboardTag("bw_npc_hologram");
        } catch (NoSuchMethodError e) {
        }
        return stand;
    }

    public void respawnHologram(org.bukkit.entity.Entity entity) {
        for (org.bukkit.entity.ArmorStand stand : hologramLines) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        hologramLines.clear();

        // Remove any orphaned hologram armor stands near the entity that aren't tracked
        // in hologramLines (e.g. left over from a previous session / new CitizensNPCImpl instance).
        if (entity != null && entity.isValid() && entity.getWorld() != null) {
            entity.getWorld().getNearbyEntities(entity.getLocation(), 1.5, 6.0, 1.5).forEach(nearby -> {
                if (nearby instanceof org.bukkit.entity.ArmorStand) {
                    try {
                        java.util.Set<String> tags = nearby.getScoreboardTags();
                        if (tags.contains("bw_hologram") || tags.contains("bw_npc_hologram")) {
                            nearby.remove();
                        }
                    } catch (NoSuchMethodError ignored) {
                    }
                }
            });
        }

        String title = plugin.getNpcManager().getConfig().getString("types." + type + ".title", type);
        title = org.bukkit.ChatColor.translateAlternateColorCodes('&', title);

        List<String> descriptionLines = new java.util.ArrayList<>();
        if (plugin.getNpcManager().getConfig().isList("types." + type + ".description")) {
            descriptionLines = plugin.getNpcManager().getConfig().getStringList("types." + type + ".description");
        } else {
            String s = plugin.getNpcManager().getConfig().getString("types." + type + ".description", "");
            if (!s.isEmpty()) descriptionLines.add(s);
        }

        spawnHologram(entity, title, descriptionLines);
    }

    public int getCitizensNpcId() {
        return citizensNpcId;
    }

    public void attachToExistingNPC(NPC existingNpc) {
        this.npc = existingNpc;
        this.citizensNpcId = existingNpc != null ? existingNpc.getId() : -1;
    }

    @Override
    public void remove() {
        if (citizensNpcId >= 0) {
            plugin.getNpcManager().unregisterCitizensImpl(citizensNpcId);
        }
        for (org.bukkit.entity.ArmorStand stand : hologramLines) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        hologramLines.clear();

        if (npc != null) {
            if (npc.getEntity() != null) {
                plugin.getNpcManager().unregisterEntity(npc.getEntity().getUniqueId());
            }
            npc.destroy();
        }
    }

    @Override
    public java.util.UUID getEntityUUID() {
        if (npc != null && npc.getEntity() != null) {
            return npc.getEntity().getUniqueId();
        }
        return null;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Location getLocation() {
        if (npc != null && npc.getEntity() != null) {
            return npc.getEntity().getLocation();
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
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "npc-invalid-mode").replace("%mode%", modeStr));
            }
        }
    }
}
