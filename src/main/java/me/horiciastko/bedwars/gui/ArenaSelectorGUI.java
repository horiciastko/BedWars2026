package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class ArenaSelectorGUI extends BaseGUI {

    private final Arena.ArenaMode filterMode;
    private final Map<Integer, Arena> slotArenaMap = new HashMap<>();

    public ArenaSelectorGUI() {
        this(null);
    }

    public ArenaSelectorGUI(Arena.ArenaMode mode) {
        super("&8Bed Wars " + (mode != null ? ModeJoinGUI.readableMode(mode) : "Maps"), 3);
        this.filterMode = mode;
    }

    private static String getModeDisplay(Arena.ArenaMode mode) {
        BedWars plugin = BedWars.getInstance();
        org.bukkit.configuration.ConfigurationSection modesSection = plugin.getConfig()
                .getConfigurationSection("join-gui.modes");

        if (modesSection != null) {
            for (String key : modesSection.getKeys(false)) {
                int playersPerTeam = modesSection.getInt(key + ".players-per-team");
                if (mode.getPlayersPerTeam() == playersPerTeam) {
                    String displayKey = "join-gui-" + key + "-display";
                    String display = plugin.getLanguageManager().getMessage(null, displayKey);
                    return org.bukkit.ChatColor.stripColor(
                            org.bukkit.ChatColor.translateAlternateColorCodes('&', display));
                }
            }
        }
        return mode.getDisplayName();
    }

    @Override
    public void setContents(Player player) {
        slotArenaMap.clear();
        List<Arena> arenas = BedWars.getInstance().getArenaManager().getArenas();

        if (!player.hasPermission("bedwars.admin")) {
            arenas = arenas.stream()
                    .filter(Arena::isEnabled)
                    .collect(Collectors.toList());
        }

        if (filterMode != null) {
            arenas = arenas.stream()
                .filter(a -> canUseArenaForMode(a, filterMode))
                    .collect(Collectors.toList());
        }

        arenas = arenas.stream()
            .sorted((a1, a2) -> {
                int statePrio1 = a1.getState() == Arena.GameState.STARTING ? 0
                    : (a1.getState() == Arena.GameState.WAITING ? 1 : 2);
                int statePrio2 = a2.getState() == Arena.GameState.STARTING ? 0
                    : (a2.getState() == Arena.GameState.WAITING ? 1 : 2);
                if (statePrio1 != statePrio2) {
                return Integer.compare(statePrio1, statePrio2);
                }
                return Integer.compare(a2.getPlayers().size(), a1.getPlayers().size());
            })
            .collect(Collectors.toList());

        int[] mapSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25 };
        int idx = 0;
        for (Arena arena : arenas) {
            if (idx >= mapSlots.length) {
                break;
            }
            int slot = mapSlots[idx++];
            slotArenaMap.put(slot, arena);
            inventory.setItem(slot, createArenaItem(arena));
        }

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }

        if (filterMode != null) {
            inventory.setItem(23, new ItemBuilder(com.cryptomorin.xseries.XMaterial.FEATHER)
                    .setName("§aRandom Join")
                    .setLore("§7Join random maps", "", "§eClick to play!")
                    .build());

            inventory.setItem(25, new ItemBuilder(com.cryptomorin.xseries.XMaterial.FIREWORK_ROCKET)
                    .setName("§fGo Back")
                    .setLore("§7Back to " + ModeJoinGUI.readableMode(filterMode) + " menu")
                    .build());
        }
    }

    private ItemStack createArenaItem(Arena arena) {
        java.util.UUID viewer = null;
        Material icon;
        String stateName;
        if (!arena.isEnabled()) {
            icon = com.cryptomorin.xseries.XMaterial.GRAY_TERRACOTTA.parseMaterial();
            stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-disabled");
        } else {
            switch (arena.getState()) {
                case WAITING:
                    icon = com.cryptomorin.xseries.XMaterial.LIME_TERRACOTTA.parseMaterial();
                stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-waiting");
                    break;
                case STARTING:
                    icon = com.cryptomorin.xseries.XMaterial.YELLOW_TERRACOTTA.parseMaterial();
                stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-starting");
                    break;
                case IN_GAME:
                    icon = com.cryptomorin.xseries.XMaterial.RED_TERRACOTTA.parseMaterial();
                stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-ingame");
                    break;
                case ENDING:
                    icon = com.cryptomorin.xseries.XMaterial.ORANGE_TERRACOTTA.parseMaterial();
                stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-ending");
                    break;
                default:
                    icon = com.cryptomorin.xseries.XMaterial.TERRACOTTA.parseMaterial();
                stateName = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-state-unknown");
                    break;
            }
        }

        String name = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-item-name")
            .replace("%arena%", arena.getName());
        String loreGroup = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-lore-group")
            .replace("%group%", arena.getGroup());
        String loreMode = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-lore-mode")
            .replace("%mode%", getModeDisplay(filterMode != null ? filterMode : arena.getMode()));
        String lorePlayers = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-lore-players")
            .replace("%current%", String.valueOf(arena.getPlayers().size()))
            .replace("%max%", String.valueOf(arena.getMaxPlayers()));
        String loreStatus = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-lore-status")
            .replace("%status%", stateName);
        String loreClick = BedWars.getInstance().getLanguageManager().getMessage(viewer, "arena-selector-lore-click");

        String modeLine = ModeJoinGUI.readableMode(filterMode != null ? filterMode : arena.getMode());
        String availableMaps = String.valueOf(BedWars.getInstance().getArenaManager().getArenas().stream()
            .filter(a -> canUseArenaForMode(a, filterMode != null ? filterMode : arena.getMode()))
            .count());

        return new ItemBuilder(com.cryptomorin.xseries.XMaterial.MAP)
            .setName("§a" + arena.getName())
            .setLore(
            "§f" + modeLine,
            "",
            "§7Available maps: §a" + availableMaps,
            "§7Status: §f" + stateName,
            "§7Current Player: §f" + arena.getPlayers().size() + "/" + arena.getMaxPlayers(),
            "",
            "§eClick to Join")
                .build();
    }

    @Override
    public void handleAction(Player player, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (item == null || item.getType().name().contains("GLASS_PANE"))
            return;

        if (slot == 23 && filterMode != null) {
            joinQuickFromSelector(player);
            return;
        }

        if (slot == 25 && filterMode != null) {
            new ModeJoinGUI(filterMode).open(player);
            return;
        }

        Arena arena = slotArenaMap.get(slot);

        if (arena != null) {
            if (filterMode != null
                    && BedWars.getInstance().getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true)
                    && arena.getState() == Arena.GameState.WAITING && arena.getPlayers().isEmpty()) {
                arena.setMode(filterMode);
            }
            BedWars.getInstance().getArenaManager().joinArena(player, arena);
            player.closeInventory();
        }
    }

    private void joinQuickFromSelector(Player player) {
        if (filterMode == null) {
            return;
        }

        List<Arena> available = BedWars.getInstance().getArenaManager().getArenas().stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getState() == Arena.GameState.WAITING || a.getState() == Arena.GameState.STARTING)
                .filter(a -> a.getPlayers().size() < a.getMaxPlayers())
                .filter(a -> canUseArenaForMode(a, filterMode))
                .sorted((a1, a2) -> Integer.compare(a2.getPlayers().size(), a1.getPlayers().size()))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "join-no-arenas-mode")
                    .replace("%mode%", filterMode.getDisplayName()));
            return;
        }

        Arena target = available.get(0);
        if (BedWars.getInstance().getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true)
                && target.getState() == Arena.GameState.WAITING && target.getPlayers().isEmpty()) {
            target.setMode(filterMode);
        }
        BedWars.getInstance().getArenaManager().joinArena(player, target);
        player.closeInventory();
    }

    private boolean canUseArenaForMode(Arena arena, Arena.ArenaMode requestedMode) {
        if (arena == null || requestedMode == null) {
            return false;
        }

        if (!BedWars.getInstance().getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true)) {
            return arena.getMode() == requestedMode;
        }

        if (arena.getState() == Arena.GameState.WAITING && arena.getPlayers().isEmpty()) {
            return true;
        }

        return arena.getMode() == requestedMode;
    }
}
