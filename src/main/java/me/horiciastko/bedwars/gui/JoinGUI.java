package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.utils.ItemBuilder;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class JoinGUI extends BaseGUI {

    private final BedWars plugin;

    public JoinGUI() {
        super(BedWars.getInstance().getLanguageManager().getMessage(null, "join-gui-title"),
                BedWars.getInstance().getConfig().getInt("join-gui.rows", 3));
        this.plugin = BedWars.getInstance();
    }

    @Override
    public void setContents(Player player) {
        ItemStack filler = new ItemBuilder(XMaterial.BLACK_STAINED_GLASS_PANE)
                .setName(" ")
                .build();
        fillBorders(filler);

        ConfigurationSection modesSection = plugin.getConfig().getConfigurationSection("join-gui.modes");
        if (modesSection == null)
            return;

        for (String key : modesSection.getKeys(false)) {
            ConfigurationSection modeData = modesSection.getConfigurationSection(key);
            if (modeData == null)
                continue;

            String displayKey = "join-gui-" + key + "-display";
            String display = plugin.getLanguageManager().getMessage(player.getUniqueId(), displayKey);
            int slot = modeData.getInt("slot", 0);
            String materialName = modeData.getString("material", "WHITE_BED");
            int playersPerTeam = modeData.getInt("players-per-team", 1);

            Arena.ArenaMode mode = findMode(playersPerTeam);
            int online = mode != null ? getOnlineCount(mode) : 0;
            int availableArenas = mode != null ? getAvailableArenaCount(mode) : 0;

            List<String> processedLore = java.util.Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getLanguageManager().getMessage(player.getUniqueId(), "config-join-lore-players-" + playersPerTeam)),
                    " ",
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getLanguageManager().getMessage(player.getUniqueId(), "config-join-lore-online") + " &f" + online),
                        ChatColor.translateAlternateColorCodes('&', "&7Available Arenas: &f" + availableArenas),
                    " ",
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getLanguageManager().getMessage(player.getUniqueId(), "config-join-lore-quick-join")),
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.getLanguageManager().getMessage(player.getUniqueId(), "config-join-lore-select-map"))
            );

            XMaterial xmat = XMaterial.matchXMaterial(materialName).orElse(XMaterial.WHITE_BED);

            inventory.setItem(slot, new ItemBuilder(xmat)
                    .setName(ChatColor.translateAlternateColorCodes('&', display))
                    .setLore(processedLore)
                    .build());
        }
    }

    private Arena.ArenaMode findMode(int playersPerTeam) {
        for (Arena.ArenaMode mode : Arena.ArenaMode.values()) {
            if (mode.getPlayersPerTeam() == playersPerTeam)
                return mode;
        }
        return null;
    }

    private int getOnlineCount(Arena.ArenaMode mode) {
        return (int) plugin.getArenaManager().getArenas().stream()
                .filter(a -> canUseArenaForMode(a, mode))
                .flatMap(a -> a.getPlayers().stream())
                .count();
    }

    private int getAvailableArenaCount(Arena.ArenaMode mode) {
        return (int) plugin.getArenaManager().getArenas().stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getState() == Arena.GameState.WAITING || a.getState() == Arena.GameState.STARTING)
                .filter(a -> a.getPlayers().size() < a.getMaxPlayers())
                .filter(a -> canUseArenaForMode(a, mode))
                .count();
    }

    @Override
    public void handleAction(Player player, int slot, ItemStack item, ClickType clickType) {
        if (item == null || !item.hasItemMeta())
            return;

        ConfigurationSection modesSection = plugin.getConfig().getConfigurationSection("join-gui.modes");
        if (modesSection == null)
            return;

        for (String key : modesSection.getKeys(false)) {
            if (modesSection.getInt(key + ".slot") == slot) {
                int playersPerTeam = modesSection.getInt(key + ".players-per-team");
                Arena.ArenaMode mode = findMode(playersPerTeam);

                if (mode != null) {
                    new ModeJoinGUI(mode).open(player);
                }
                return;
            }
        }
    }

    private void joinQuickGame(Player player, Arena.ArenaMode mode) {
        List<Arena> available = plugin.getArenaManager().getArenas().stream()
                .filter(a -> a.isEnabled())
                .filter(a -> a.getState() == Arena.GameState.WAITING || a.getState() == Arena.GameState.STARTING)
                .filter(a -> a.getPlayers().size() < a.getMaxPlayers())
                .filter(a -> canUseArenaForMode(a, mode))
                .sorted((a1, a2) -> {
                    int statePrio1 = a1.getState() == Arena.GameState.STARTING ? 0 : 1;
                    int statePrio2 = a2.getState() == Arena.GameState.STARTING ? 0 : 1;
                    if (statePrio1 != statePrio2) {
                        return Integer.compare(statePrio1, statePrio2);
                    }
                    return Integer.compare(a2.getPlayers().size(), a1.getPlayers().size());
                })
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            String message = plugin.getLanguageManager().getMessage(player.getUniqueId(), "join-no-arenas-mode")
                    .replace("%mode%", mode.getDisplayName());
            player.sendMessage(message);
            return;
        }

        Arena target = available.get(0);
        if (plugin.getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true)
                && target.getState() == Arena.GameState.WAITING && target.getPlayers().isEmpty()) {
            target.setMode(mode);
        }
        plugin.getArenaManager().joinArena(player, target);
    }

    private boolean canUseArenaForMode(Arena arena, Arena.ArenaMode requestedMode) {
        if (arena == null || requestedMode == null) {
            return false;
        }

        if (!plugin.getConfig().getBoolean("join-gui.allow-multi-mode-arenas", true)) {
            return arena.getMode() == requestedMode;
        }

        if (arena.getState() == Arena.GameState.WAITING && arena.getPlayers().isEmpty()) {
            return true;
        }

        return arena.getMode() == requestedMode;
    }
}
