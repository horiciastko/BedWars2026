package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.npc.BedWarsNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CitizensNPCListener implements Listener {

    private final BedWars plugin;

    public CitizensNPCListener(BedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        net.citizensnpcs.api.npc.NPC npc = event.getNPC();
        Player player = event.getClicker();

        String bedwarsType = resolveBedWarsType(npc);

        if (bedwarsType != null) {
            event.setCancelled(true);
            
            BedWarsNPC bedwarsNPC = null;
            if (npc.getEntity() != null) {
                bedwarsNPC = plugin.getNpcManager().getNPCByEntity(npc.getEntity().getUniqueId());
            }

            if (bedwarsNPC != null) {
                bedwarsNPC.onClick(player);
            } else {
                handleNpcClick(player, bedwarsType);
            }
        }
    }
    @EventHandler
    public void onNPCLeftClick(net.citizensnpcs.api.event.NPCLeftClickEvent event) {
        net.citizensnpcs.api.npc.NPC npc = event.getNPC();
        Player player = event.getClicker();

        String bedwarsType = resolveBedWarsType(npc);

        if (bedwarsType != null) {
            event.setCancelled(true);
            
            BedWarsNPC bedwarsNPC = null;
            if (npc.getEntity() != null) {
                bedwarsNPC = plugin.getNpcManager().getNPCByEntity(npc.getEntity().getUniqueId());
            }

            if (bedwarsNPC != null) {
                bedwarsNPC.onClick(player);
            } else {
                handleNpcClick(player, bedwarsType);
            }
        }
    }

    @EventHandler
    public void onNPCSpawn(net.citizensnpcs.api.event.NPCSpawnEvent event) {
        net.citizensnpcs.api.npc.NPC npc = event.getNPC();

        String type = resolveBedWarsType(npc);
        if (type == null) {
            return;
        }

        me.horiciastko.bedwars.npc.CitizensNPCImpl impl =
                plugin.getNpcManager().getCitizensImpl(npc.getId());
        if (impl == null) {
            impl = new me.horiciastko.bedwars.npc.CitizensNPCImpl(plugin, type);
            impl.attachToExistingNPC(npc);
            plugin.getNpcManager().registerCitizensImpl(npc.getId(), impl);
        }

        final me.horiciastko.bedwars.npc.CitizensNPCImpl finalImpl = impl;
        if (impl != null) {
            final net.citizensnpcs.api.npc.NPC finalNpc = npc;
            if (finalNpc.getEntity() != null) {
                try {
                    finalNpc.getEntity().addScoreboardTag("bw_npc");
                    finalNpc.getEntity().addScoreboardTag("bw_npc_type_" + type);
                } catch (NoSuchMethodError | Exception ignored) {
                }
                plugin.getNpcManager().registerEntity(finalNpc.getEntity().getUniqueId(), finalImpl);
            }
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (finalNpc.isSpawned() && finalNpc.getEntity() != null) {
                        try {
                            finalNpc.getEntity().addScoreboardTag("bw_npc");
                            finalNpc.getEntity().addScoreboardTag("bw_npc_type_" + type);
                        } catch (NoSuchMethodError | Exception ignored) {
                        }
                        plugin.getNpcManager().registerEntity(finalNpc.getEntity().getUniqueId(), finalImpl);
                        finalImpl.respawnHologram(finalNpc.getEntity());
                    }
                }
            }.runTaskLater(plugin, 2L);
            return;
        }
    }

    private String resolveBedWarsType(net.citizensnpcs.api.npc.NPC npc) {
        if (npc == null) {
            return null;
        }

        Object rawType = npc.data().get("bw_npc_type");
        String type = rawType instanceof String ? (String) rawType : null;
        if (type != null && !type.trim().isEmpty()) {
            return type;
        }

        for (net.citizensnpcs.api.trait.Trait trait : npc.getTraits()) {
            String traitName = trait.getName();
            if (traitName != null && traitName.startsWith("bw_")) {
                return traitName.substring(3);
            }
        }

        return null;
    }

    private void handleNpcClick(Player player, String type) {
        String actionType = type.toLowerCase();
        
        if (actionType.equalsIgnoreCase("shop")) {
            new me.horiciastko.bedwars.gui.ShopGUI().open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("upgrades")) {
            new me.horiciastko.bedwars.gui.UpgradeGUI().open(player);
            return;
        }
        
        if (actionType.equalsIgnoreCase("join") || actionType.equalsIgnoreCase("play")) {
            new me.horiciastko.bedwars.gui.JoinGUI().open(player);
            return;
        }
        
        if (actionType.equalsIgnoreCase("solo")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.SOLO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("duo") || actionType.equalsIgnoreCase("doubles")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.DUO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("trio") || actionType.equalsIgnoreCase("3v3v3v3")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.TRIO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("quad") || actionType.equalsIgnoreCase("squad") || actionType.equalsIgnoreCase("4v4v4v4")) {
            new me.horiciastko.bedwars.gui.ArenaSelectorGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.SQUAD).open(player);
            return;
        }
        
        String modeStr = plugin.getNpcManager().getConfig().getString("types." + type + ".mode");
        if (modeStr != null) {
            try {
                me.horiciastko.bedwars.models.Arena.ArenaMode mode = me.horiciastko.bedwars.models.Arena.ArenaMode
                        .valueOf(modeStr.toUpperCase());
                new me.horiciastko.bedwars.gui.ArenaSelectorGUI(mode).open(player);
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "npc-invalid-mode").replace("%mode%", modeStr));
            }
        }
    }
}
