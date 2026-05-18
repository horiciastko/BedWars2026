package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.npc.BedWarsNPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

public class NPCListener implements Listener {

    private final BedWars plugin;

    public NPCListener(BedWars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        try {
            if (event.getHand() != EquipmentSlot.HAND)
                return;
        } catch (NoSuchMethodError ignored) {
        }

        Entity entity = event.getRightClicked();

        boolean isBwNpc = false;
        try {
            Set<String> tags = entity.getScoreboardTags();
            isBwNpc = tags.contains("bw_npc");
        } catch (NoSuchMethodError ignored) {
            isBwNpc = plugin.getNpcManager().getNPCByEntity(entity.getUniqueId()) != null;
        }

        if (isBwNpc) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            BedWarsNPC npc = plugin.getNpcManager().getNPCByEntity(entity.getUniqueId());
            if (npc != null) {
                npc.onClick(player);
            } else {
                try {
                    for (String tag : entity.getScoreboardTags()) {
                        if (tag.startsWith("bw_npc_type_")) {
                            String type = tag.substring("bw_npc_type_".length());
                            handleNpcClick(player, type);
                            return;
                        }
                    }
                } catch (NoSuchMethodError ignored) {
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
            new me.horiciastko.bedwars.gui.ModeJoinGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.SOLO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("duo") || actionType.equalsIgnoreCase("doubles")) {
            new me.horiciastko.bedwars.gui.ModeJoinGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.DUO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("trio") || actionType.equalsIgnoreCase("3v3v3v3")) {
            new me.horiciastko.bedwars.gui.ModeJoinGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.TRIO).open(player);
            return;
        }
        if (actionType.equalsIgnoreCase("quad") || actionType.equalsIgnoreCase("squad") || actionType.equalsIgnoreCase("4v4v4v4")) {
            new me.horiciastko.bedwars.gui.ModeJoinGUI(me.horiciastko.bedwars.models.Arena.ArenaMode.SQUAD).open(player);
            return;
        }
        
        String modeStr = plugin.getNpcManager().getConfig().getString("types." + type + ".mode");
        if (modeStr != null) {
            try {
                me.horiciastko.bedwars.models.Arena.ArenaMode mode = me.horiciastko.bedwars.models.Arena.ArenaMode
                        .valueOf(modeStr.toUpperCase());
                new me.horiciastko.bedwars.gui.ModeJoinGUI(mode).open(player);
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "npc-invalid-mode").replace("%mode%", modeStr));
            }
        }
    }
}
