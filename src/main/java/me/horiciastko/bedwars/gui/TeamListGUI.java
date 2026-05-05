package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import me.horiciastko.bedwars.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TeamListGUI extends BaseGUI {

    private final Arena arena;

    public TeamListGUI(Arena arena) {
        super("§8Manage Teams", 6);
        this.arena = arena;
    }

    @Override
    public void setContents(Player player) {
        int slot = 0;
        for (Team team : arena.getTeams()) {
            // Row 6 (slots 45-53) is reserved for navigation buttons — skip it.
            if (slot >= 45) {
                slot = -1; // sentinel: no more room
                break;
            }
            ItemStack teamItem = new ItemBuilder(team.getMaterial())
                    .setName(team.getColor() + team.getDisplayName())
                    .setLore(
                            "§7Color: " + team.getColor().name(),
                            "§7Spawn: " + (team.getSpawnLocation() != null ? "§aSet" : "§cNot Set"),
                            "§7Bed: " + (team.getBedLocation() != null ? "§aSet" : "§cNot Set"),
                            "",
                            "§eLeft-Click to Edit",
                            "§cRight-Click to Delete")
                    .build();

            if (teamItem.getItemMeta() != null) {
                me.horiciastko.bedwars.utils.ItemTagUtils.setTag(teamItem, "team_name", team.getName());
            }
            inventory.setItem(slot++, teamItem);
        }

        inventory.setItem(53, new ItemBuilder(Material.EMERALD)
                .setName("§aAdd New Team")
                .setLore("§7Click to create a new team")
                .build());

        inventory.setItem(45, new ItemBuilder(Material.ARROW).setName("§cBack").build());
    }

    @Override
    public void handleAction(Player player, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (item == null || !item.hasItemMeta())
            return;

        if (slot == 45) { 
            new ArenaSettingsGUI(arena).open(player);
            return;
        }

        if (slot == 53) {
            new TeamColorSelectorGUI(arena).open(player);
            return;
        }

        String teamName = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "team_name");

        if (teamName != null) {
            Team team = arena.getTeams().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(teamName))
                    .findFirst()
                    .orElse(null);

            if (team != null) {
                if (clickType.isRightClick()) {
                    arena.getTeams().remove(team);
                    BedWars.getInstance().getArenaManager().saveArena(arena);
                    setContents(player);
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "team-removed-short"));
                } else {
                    new TeamSettingsGUI(arena, team).open(player);
                }
            }
        }
    }
}
