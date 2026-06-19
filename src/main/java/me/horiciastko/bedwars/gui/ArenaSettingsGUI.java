package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("deprecation")
public class ArenaSettingsGUI extends BaseGUI {

        private final Arena arena;

        public ArenaSettingsGUI(Arena arena) {
                super("§8§l✦ §9" + arena.getName() + " §8§l✦ §7Setup", 5);
                this.arena = arena;
        }

        @Override
        public void open(Player player) {
                super.open(player);
                BedWars.getInstance().getArenaManager().setEditArena(player, arena);
                BedWars.getInstance().getVisualizationManager().showHolograms(player, arena);
        }

        @Override
        public void setContents(Player player) {
                inventory.setItem(0, new ItemBuilder(com.cryptomorin.xseries.XMaterial.BLUE_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(1, new ItemBuilder(com.cryptomorin.xseries.XMaterial.CYAN_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(2, new ItemBuilder(com.cryptomorin.xseries.XMaterial.LIGHT_BLUE_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(3, new ItemBuilder(com.cryptomorin.xseries.XMaterial.WHITE_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(4, new ItemBuilder(com.cryptomorin.xseries.XMaterial.YELLOW_STAINED_GLASS_PANE)
                                .setName("§e§l" + arena.getName()).build());
                inventory.setItem(5, new ItemBuilder(com.cryptomorin.xseries.XMaterial.WHITE_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(6, new ItemBuilder(com.cryptomorin.xseries.XMaterial.LIGHT_BLUE_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(7, new ItemBuilder(com.cryptomorin.xseries.XMaterial.CYAN_STAINED_GLASS_PANE)
                                .setName(" ").build());
                inventory.setItem(8, new ItemBuilder(com.cryptomorin.xseries.XMaterial.BLUE_STAINED_GLASS_PANE)
                                .setName(" ").build());

                boolean worldSet = arena.getWorldName() != null;
                inventory.setItem(10,
                                new ItemBuilder(worldSet ? com.cryptomorin.xseries.XMaterial.GRASS_BLOCK
                                                : com.cryptomorin.xseries.XMaterial.DIRT)
                                                .setName(worldSet ? "§a§l✓ §fWorld Selected" : "§c§l✗ §fSelect World")
                                                .setLore(
                                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                                "§7Current: " + (worldSet ? "§e" + arena.getWorldName()
                                                                                : "§cNone"),
                                                                "",
                                                                worldSet ? "§a✓ World is configured!"
                                                                                : "§c✗ Choose a world first!",
                                                                "",
                                                                "§e▸ Click to select world",
                                                                "§7▸ Right-click to clear",
                                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                                .build());

                boolean boundsSet = arena.getPos1() != null && arena.getPos2() != null;
                boolean lobbySet = arena.getLobbyLocation() != null;
                boolean step2Done = boundsSet && lobbySet;
                inventory.setItem(11, new ItemBuilder(step2Done ? com.cryptomorin.xseries.XMaterial.EMERALD_ORE : com.cryptomorin.xseries.XMaterial.STONE)
                                .setName(step2Done ? "§a§l✓ §fArena Bounds" : "§6§l⚠ §fArena Bounds")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Pos 1: " + (arena.getPos1() != null ? "§a✓ Set" : "§c✗ Not set"),
                                                "§7Pos 2: " + (arena.getPos2() != null ? "§a✓ Set" : "§c✗ Not set"),
                                                "§7Lobby: " + (lobbySet ? "§a✓ Set" : "§c✗ Not set"),
                                                "",
                                                step2Done ? "§a✓ Bounds configured!" : "§6⚠ Complete this step!",
                                                "",
                                                "§e▸ Click to setup locations",
                                                "§7▸ Right-click to clear all",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                int teamCount = arena.getTeams().size();
                boolean teamsReady = teamCount >= 2 && arena.getTeams().stream()
                                .allMatch(t -> t.getSpawnLocation() != null && t.getBedLocation() != null);
                inventory.setItem(12, new ItemBuilder(teamsReady ? com.cryptomorin.xseries.XMaterial.WHITE_BANNER
                                : com.cryptomorin.xseries.XMaterial.BLACK_BANNER)
                                .setName(teamsReady ? "§a§l✓ §fTeams" : "§6§l⚠ §fTeams")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Teams: §e" + teamCount,
                                                "",
                                                step2Done
                                                                ? (teamsReady ? "§a✓ All teams ready!"
                                                                                : "§6⚠ Set spawns & beds!")
                                                                : "§c✗ Complete Step 2 first!",
                                                "",
                                                step2Done ? "§e▸ Click to manage teams"
                                                                : "§c▸ Locked - complete Step 2!",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                int diamondCount = arena.getDiamondGenerators().size();
                int emeraldCount = arena.getEmeraldGenerators().size();
                boolean gensSet = diamondCount > 0 || emeraldCount > 0;
                inventory.setItem(13, new ItemBuilder(com.cryptomorin.xseries.XMaterial.DIAMOND)
                                .setName(gensSet ? "§b§l✓ §fGlobal Generators" : "§6§l⚠ §fGlobal Generators")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§bDiamond: §f" + diamondCount,
                                                "§aEmerald: §f" + emeraldCount,
                                                "",
                                                gensSet ? "§a✓ Generators configured!" : "§6⚠ Add some generators!",
                                                "",
                                                "§e▸ Click to manage generators",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                long shopsSet = arena.getTeams().stream().filter(t -> t.getShopLocation() != null).count();
                inventory.setItem(19, new ItemBuilder(com.cryptomorin.xseries.XMaterial.VILLAGER_SPAWN_EGG)
                                .setName("§a§lShop NPCs")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Configured: §e" + shopsSet + "/" + teamCount,
                                                "",
                                                "§e▸ Click to manage shops",
                                                "§7▸ Auto-detect or manual setup",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                inventory.setItem(21, new ItemBuilder(com.cryptomorin.xseries.XMaterial.COMPARATOR)
                                .setName("§6§l⚙ §fGame Settings")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Min Players: §e" + arena.getMinPlayers(),
                                                "§7Max Players: §e" + arena.getMaxPlayers(),
                                                "§7Mode: §b" + arena.getMode().getDisplayName(),
                                                "§7PvP: §a" + arena.getPvpMode().getDisplayName(),
                                                "",
                                                "§e▸ Click to configure",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                boolean multiModeEnabled = BedWars.getInstance().getConfig()
                                .getBoolean("join-gui.allow-multi-mode-arenas", true);
                inventory.setItem(22, new ItemBuilder(multiModeEnabled ? com.cryptomorin.xseries.XMaterial.LIME_CONCRETE : com.cryptomorin.xseries.XMaterial.GRAY_CONCRETE)
                                .setName("§b§lJoin Mode Reuse")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Empty waiting arenas can",
                                                "§7be reused for selected mode.",
                                                "",
                                                "§7Current: " + (multiModeEnabled ? "§aON" : "§cOFF"),
                                                "",
                                                "§e▸ Click to toggle globally",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                boolean enabled = arena.isEnabled();
                inventory.setItem(23,
                                new ItemBuilder(enabled ? com.cryptomorin.xseries.XMaterial.LIME_DYE
                                                : com.cryptomorin.xseries.XMaterial.GRAY_DYE)
                                                .setName(enabled ? "§a§l✓ §fArena Enabled" : "§c§l✗ §fArena Disabled")
                                                .setLore(
                                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                                "§7Status: " + (enabled ? "§aENABLED" : "§cDISABLED"),
                                                                "",
                                                                enabled
                                                                                ? "§aPlayers can join this arena"
                                                                                : "§cPlayers cannot join",
                                                                "",
                                                                "§e▸ Click to toggle",
                                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                                .build());

                inventory.setItem(25, new ItemBuilder(com.cryptomorin.xseries.XMaterial.STICK)
                                .setName("§e§l⚡ §fSelection Wand")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                "§7Use to select team base regions",
                                                "",
                                                "§aLeft-click: §7Set Pos 1",
                                                "§aRight-click: §7Set Pos 2",
                                                "",
                                                "§e▸ Click to get wand",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                boolean isReady = worldSet && step2Done && teamsReady;
                inventory.setItem(31, new ItemBuilder(isReady ? com.cryptomorin.xseries.XMaterial.EMERALD_BLOCK : com.cryptomorin.xseries.XMaterial.COAL_BLOCK)
                                .setName(isReady ? "§a§l✓ §fFinalize Arena" : "§c§l✗ §fNot Ready")
                                .setLore(
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━",
                                                isReady ? "§aAll requirements met!" : "§cComplete setup first:",
                                                "",
                                                worldSet ? "§a✓ §7World selected" : "§c✗ §7World not selected",
                                                step2Done ? "§a✓ §7Bounds & lobby set" : "§c✗ §7Bounds/lobby missing",
                                                teamsReady ? "§a✓ §7Teams configured" : "§c✗ §7Teams incomplete",
                                                "",
                                                isReady ? "§e▸ Click to finish setup" : "§cComplete required steps!",
                                                "§8━━━━━━━━━━━━━━━━━━━━━━━")
                                .build());

                inventory.setItem(33, new ItemBuilder(com.cryptomorin.xseries.XMaterial.BARRIER)
                                .setName("§c§l✖ §fExit Setup")
                                .setLore(
                                                "§7Close without saving new changes",
                                                "",
                                                "§c▸ Click to exit")
                                .build());

                for (int i = 36; i < 45; i++) {
                        inventory.setItem(i, new ItemBuilder(com.cryptomorin.xseries.XMaterial.BLACK_STAINED_GLASS_PANE)
                                        .setName(" ").build());
                }

                for (int i = 0; i < inventory.getSize(); i++) {
                        if (inventory.getItem(i) == null) {
                                inventory.setItem(i,
                                                new ItemBuilder(com.cryptomorin.xseries.XMaterial.GRAY_STAINED_GLASS_PANE)
                                                                .setName(" ").build());
                        }
                }
        }

        @Override
        public void handleAction(Player player, int slot, ItemStack item,
                        org.bukkit.event.inventory.ClickType clickType) {
                if (item == null || !item.hasItemMeta())
                        return;

                switch (slot) {
                        case 10:
                                if (clickType.isRightClick()) {
                                        arena.setWorldName(null);
                                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-world-cleared"));
                                        BedWars.getInstance().getArenaManager().saveArena(arena);
                                        open(player);
                                } else {
                                        new WorldSelectorGUI(arena).open(player);
                                }
                                break;
                        case 11:
                                if (clickType.isRightClick()) {
                                        arena.setPos1(null);
                                        arena.setPos2(null);
                                        arena.setLobbyLocation(null);
                                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-positions-cleared"));
                                        BedWars.getInstance().getArenaManager().saveArena(arena);
                                        open(player);
                                } else {
                                        new ArenaLocationsGUI(arena).open(player);
                                }
                                break;
                        case 12:
                                boolean b2 = arena.getPos1() != null && arena.getPos2() != null
                                                && arena.getLobbyLocation() != null;
                                if (b2) {
                                        new TeamManagementGUI(arena).open(player);
                                } else {
                                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-bounds-required"));
                                }
                                break;
                        case 13: 
                                new GeneratorManagementGUI(arena).open(player);
                                break;
                        case 19:
                                new ShopManagementGUI(arena).open(player);
                                break;
                        case 21:
                                new ArenaGeneralSettingsGUI(arena).open(player);
                                break;
                        case 22:
                                boolean current = BedWars.getInstance().getConfig()
                                                .getBoolean("join-gui.allow-multi-mode-arenas", true);
                                BedWars.getInstance().getConfig().set("join-gui.allow-multi-mode-arenas", !current);
                                BedWars.getInstance().saveConfig();
                                player.sendMessage("§7[BedWars] Join mode reuse: " + (!current ? "§aON" : "§cOFF"));
                                open(player);
                                break;
                        case 23:
                                arena.setEnabled(!arena.isEnabled());
                                player.sendMessage(arena.isEnabled()
                                                ? BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "setup-arena-enabled")
                                                : BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "setup-arena-disabled"));
                                BedWars.getInstance().getArenaManager().saveArena(arena);
                                open(player);
                                break;
                        case 25:
                                giveSelectionWand(player);
                                break;
                        case 31: 
                                boolean wSet = arena.getWorldName() != null;
                                boolean sReady = arena.getPos1() != null && arena.getPos2() != null
                                                && arena.getLobbyLocation() != null;
                                boolean tReady = arena.getTeams().size() >= 2 && arena.getTeams().stream()
                                                .allMatch(t -> t.getSpawnLocation() != null
                                                                && t.getBedLocation() != null);

                                if (wSet && sReady && tReady) {
                                        arena.setEnabled(true);
                                        BedWars.getInstance().getArenaManager().saveArena(arena);
                                        BedWars.getInstance().getArenaManager().stopEditing(player);
                                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-ready")
                                                        .replace("%name%", arena.getName()));
                                        player.closeInventory();
                                } else {
                                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-setup-incomplete"));
                                }
                                break;
                        case 33:
                                BedWars.getInstance().getArenaManager().stopEditing(player);
                                player.closeInventory();
                                break;
                }
        }

        private void giveSelectionWand(Player player) {
                ItemStack stick = new ItemStack(Material.STICK);
                org.bukkit.inventory.meta.ItemMeta meta = stick.getItemMeta();
                if (meta != null) {
                        meta.setDisplayName("§e§lBase Selection Wand");
                        java.util.List<String> lore = new java.util.ArrayList<>();
                        lore.add("§7Arena: §b" + arena.getName());
                        lore.add("");
                        lore.add("§aLeft-click: §7Set Pos 1");
                        lore.add("§aRight-click: §7Set Pos 2");
                        lore.add("§7(Auto-detects closest team)");
                        meta.setLore(lore);
                        stick.setItemMeta(meta);
                }
                me.horiciastko.bedwars.utils.ItemTagUtils.setTag(stick, "special_item", "base_selection_tool");
                player.getInventory().addItem(stick);
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "arena-wand-received"));
                player.closeInventory();
        }
}
