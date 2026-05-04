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

        String bedwarsType = null;
        for (net.citizensnpcs.api.trait.Trait trait : npc.getTraits()) {
            String traitName = trait.getName();
            if (traitName != null && traitName.startsWith("bw_")) {
                bedwarsType = traitName.substring(3);
                break;
            }
        }

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

        String bedwarsType = null;
        for (net.citizensnpcs.api.trait.Trait trait : npc.getTraits()) {
            String traitName = trait.getName();
            if (traitName != null && traitName.startsWith("bw_")) {
                bedwarsType = traitName.substring(3);
                break;
            }
        }

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
        
        boolean isBedWarsNPC = false;
        for (net.citizensnpcs.api.trait.Trait trait : npc.getTraits()) {
            String traitName = trait.getName();
            if (traitName != null && traitName.startsWith("bw_")) {
                isBedWarsNPC = true;
                break;
            }
        }
        
        if (!isBedWarsNPC) {
            return;
        }

        // Re-spawn the custom ArmorStand hologram for CitizensNPCImpl instances.
        // Citizens re-spawns NPCs (chunk reload, server restart, etc.) and fires this event,
        // but the hologram is not automatically recreated — we must do it here.
        me.horiciastko.bedwars.npc.CitizensNPCImpl impl =
                plugin.getNpcManager().getCitizensImpl(npc.getId());
        if (impl != null) {
            final net.citizensnpcs.api.npc.NPC finalNpc = npc;
            if (finalNpc.getEntity() != null) {
                plugin.getNpcManager().registerEntity(finalNpc.getEntity().getUniqueId(), impl);
            }
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (finalNpc.isSpawned() && finalNpc.getEntity() != null) {
                        plugin.getNpcManager().registerEntity(finalNpc.getEntity().getUniqueId(), impl);
                        impl.respawnHologram(finalNpc.getEntity());
                    }
                }
            }.runTaskLater(plugin, 2L);
            return;
        }

        if (npc.hasTrait(net.citizensnpcs.trait.HologramTrait.class)) {
            net.citizensnpcs.trait.HologramTrait holo = npc.getOrAddTrait(net.citizensnpcs.trait.HologramTrait.class);
            holo.clear();
            
            String type = null;
            for (net.citizensnpcs.api.trait.Trait trait : npc.getTraits()) {
                String traitName = trait.getName();
                if (traitName != null && traitName.startsWith("bw_")) {
                    type = traitName.substring(3);
                    break;
                }
            }
            
            if (type != null) {
                java.util.List<String> descriptionLines = plugin.getNpcManager().getConfig()
                        .getStringList("types." + type + ".description");
                for (String line : descriptionLines) {
                    holo.addLine(line);
                }
            }
        }
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
