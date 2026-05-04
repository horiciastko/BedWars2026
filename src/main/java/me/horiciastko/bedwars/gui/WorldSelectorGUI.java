package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.utils.ItemBuilder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class WorldSelectorGUI extends BaseGUI {

    private final Arena arena;
    private final List<String> worldNames;

    public WorldSelectorGUI(Arena arena) {
        super("§8Set World: §1" + arena.getName(), 6);
        this.arena = arena;
        this.worldNames = findWorlds();
    }

    private List<String> findWorlds() {
        List<String> names = new ArrayList<>();
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File levelDat = new File(file, "level.dat");
                    if (levelDat.exists()) {
                        names.add(file.getName());
                    }
                }
            }
        }
        return names;
    }

    @Override
    public void setContents(Player player) {
        int slot = 0;
        for (String name : worldNames) {
            if (slot >= rows * 9)
                break;

            World world = Bukkit.getWorld(name);
            com.cryptomorin.xseries.XMaterial material = com.cryptomorin.xseries.XMaterial.GRASS_BLOCK;
            String status = "§cNot Loaded";

            if (world != null) {
                status = "§aLoaded";
                if (world.getEnvironment() == World.Environment.NETHER)
                    material = com.cryptomorin.xseries.XMaterial.NETHERRACK;
                else if (world.getEnvironment() == World.Environment.THE_END)
                    material = com.cryptomorin.xseries.XMaterial.END_STONE;
            }

            ItemStack worldItem = new ItemBuilder(material)
                    .setName("§a" + name)
                    .setLore(
                            "§7Status: " + status,
                            "§7Click to set this world for arena.",
                            "§7Current: §e" + (arena.getWorldName() != null ? arena.getWorldName() : "None"))
                    .build();

            org.bukkit.inventory.meta.ItemMeta meta = worldItem.getItemMeta();
            if (meta != null) {
                worldItem.setItemMeta(meta);
                me.horiciastko.bedwars.utils.ItemTagUtils.setTag(worldItem, "world_name", name);
            }

            inventory.setItem(slot, worldItem);
            slot++;
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, new ItemBuilder(com.cryptomorin.xseries.XMaterial.GRAY_STAINED_GLASS_PANE)
                        .setName(" ").build());
            }
        }
    }

    @Override
    public void handleAction(Player player, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE)
            return;

        String worldName = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "world_name");
        if (worldName == null)
            return;
        arena.setWorldName(worldName);
        me.horiciastko.bedwars.BedWars.getInstance().getArenaManager().saveArenas();

        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-loading").replace("%world%", worldName));
            targetWorld = Bukkit.createWorld(new WorldCreator(worldName));
        }

        if (targetWorld != null) {
            player.teleport(targetWorld.getHighestBlockAt(targetWorld.getSpawnLocation()).getLocation().add(0, 1, 0));
            // Create initial backup if one does not exist yet for this world
            final String worldNameForBackup = worldName;
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(me.horiciastko.bedwars.BedWars.getInstance(), () -> {
                if (!me.horiciastko.bedwars.utils.WorldBackupUtils.hasBackup(worldNameForBackup)) {
                    boolean ok = me.horiciastko.bedwars.utils.WorldBackupUtils.createBackup(worldNameForBackup);
                    if (ok) {
                        me.horiciastko.bedwars.BedWars.getInstance().getLogger()
                                .info("Created initial world backup for: " + worldNameForBackup);
                    }
                }
            });
        }

        player.closeInventory();

        player.sendMessage(" ");
        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-separator"));
        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-selected").replace("%world%", worldName));

        TextComponent confirm = new TextComponent(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-confirm-button"));
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bw admin arena edit " + arena.getName()));
        confirm.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-confirm-hover")).create()));

        player.spigot().sendMessage(confirm);
        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "world-separator"));
        player.sendMessage(" ");
    }
}
