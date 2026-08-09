package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.gui.ShopGUI;
import me.horiciastko.bedwars.gui.UpgradeGUI;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GameListener - Paper API версия
 * Обработка всех событий игры на арене
 */
public class GameListener implements Listener {

    private final Set<UUID> hiddenArmorPlayers = new HashSet<>();

    public GameListener() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                syncInvisibleArmorState();
            }
        }.runTaskTimer(BedWars.getInstance(), 1L, 20L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        if (event.getCause() != org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }
        if (!(event.getIgnitingEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getIgnitingEntity();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena != null && arena.getState() == Arena.GameState.IN_GAME) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInHand();
        
        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "special_item");
            if ("base_selection_tool".equals(special)) {
                event.setCancelled(true);
                Arena editArena = BedWars.getInstance().getArenaManager().getEditArena(player);
                if (editArena != null) {
                    Team targetTeam = findClosestTeam(editArena, event.getBlock().getLocation());
                    if (targetTeam == null) {
                        player.sendMessage(Component.text(
                            BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "wand-no-team-nearby")
                        ));
                    } else {
                        targetTeam.setBasePos1(event.getBlock().getLocation());
                        String location = event.getBlock().getX() + ", " + event.getBlock().getY() + ", "
                                + event.getBlock().getZ();
                        player.sendMessage(Component.text(
                            BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "wand-base-pos1-set")
                            .replace("%color%", targetTeam.getColor().toString())
                            .replace("%team%", targetTeam.getDisplayName())
                            .replace("%location%", location)
                        ));
                        BedWars.getInstance().getArenaManager().saveArena(editArena);
                    }
                }
                return;
            }
        }

        if (BedWars.getInstance().getGameManager().isInBuildMode(player)) {
            return;
        }

        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME) {
            if (player.hasPermission("bedwars.admin")) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        String blockName = block.getType().name();
        
        if (blockName.endsWith("_BED") || blockName.equals("BED_BLOCK")) {
            handleBedBreak(event, player, arena);
            return;
        } else {
            for (Team t : arena.getTeams()) {
                if (isInside(block.getLocation(), t.getBasePos1(), t.getBasePos2())) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (arena.getPlacedBlocks().contains(block.getLocation())) {
                arena.getPlacedBlocks().remove(block.getLocation());
                if (block.getType() == Material.SPONGE || block.getType() == Material.WET_SPONGE) {
                    event.setDropItems(false);
                    block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SPONGE, 1));
                }
            } else {
                event.setCancelled(true);
                String msg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "interact-cant-break");
                player.sendMessage(Component.text(msg));
            }
        }
    }

    private void handleBedBreak(BlockBreakEvent event, Player player, Arena arena) {
        Team victimTeam = null;
        for (Team team : arena.getTeams()) {
            if (isBedBlock(team.getBedLocation(), event.getBlock())) {
                victimTeam = team;
                break;
            }
        }

        if (victimTeam == null) {
            event.setCancelled(true);
            String msg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "interact-cant-break");
            player.sendMessage(Component.text(msg));
            return;
        }

        if (victimTeam.isBedBroken()) {
            event.setCancelled(true);
            clearBedBlocks(event.getBlock());
            return;
        }

        Team attackerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
        boolean adminBreak = BedWars.getInstance().getGameManager().isInBuildMode(player);

        if (attackerTeam == null && !adminBreak) {
            event.setCancelled(true);
            return;
        }

        if (!adminBreak && attackerTeam.getName().equals(victimTeam.getName())) {
            event.setCancelled(true);
            String msg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "interact-cant-destroy-bed");
            player.sendMessage(Component.text(msg));
        } else {
            event.setCancelled(true);
            clearBedBlocks(event.getBlock());

            victimTeam.setBedBroken(true);

            String attackerColor = (attackerTeam != null && attackerTeam.getColor() != null ? attackerTeam.getColor().toString() : "§f");
            String bedMsg = BedWars.getInstance().getLanguageManager().getMessage(null, "interact-bed-destroy-chat");
            String formattedMsg = bedMsg
                    .replace("{TeamColor}", victimTeam.getColor() != null ? victimTeam.getColor().toString() : "§f")
                    .replace("{TeamName}", victimTeam.getName())
                    .replace("{PlayerColor}", attackerColor)
                    .replace("{PlayerName}", player.getName());

            for (Player p : arena.getPlayers()) {
                p.sendMessage(Component.text(formattedMsg));
                if (p.getUniqueId().equals(player.getUniqueId()) || (attackerTeam != null && attackerTeam.getMembers().contains(p))) {
                    BedWars.getInstance().getSoundManager().playSound(p, "bed-destroy");
                } else if (victimTeam.getMembers().contains(p)) {
                    BedWars.getInstance().sendTitle(p,
                            BedWars.getInstance().getLanguageManager().getMessage(p.getUniqueId(),
                                    "interact-bed-destroy-title"),
                            BedWars.getInstance().getLanguageManager().getMessage(p.getUniqueId(),
                                    "interact-bed-destroy-subtitle"),
                            10, 70, 20);
                    BedWars.getInstance().getSoundManager().playSound(p, "bed-destroy-own");
                } else {
                    BedWars.getInstance().getSoundManager().playSound(p, "bed-destroy");
                }
            }

            BedWars.getInstance().getStatsManager().addBedBroken(player.getUniqueId());
            BedWars.getInstance().getGameManager().getOrCreateSessionStats(arena, player.getUniqueId()).bedsBroken++;
            BedWars.getInstance().getVisualizationManager().spawnGameHolograms(arena);

            for (Player p : arena.getPlayers()) {
                BedWars.getInstance().getScoreboardManager().updateScoreboard(p);
            }
        }
    }

    private void syncInvisibleArmorState() {
        Set<UUID> currentlyHidden = new HashSet<>();

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!shouldHideArmor(player)) {
                continue;
            }
            hideArmorForViewers(player);
            spawnInvisibilityParticles(player);
            currentlyHidden.add(player.getUniqueId());
        }

        Set<UUID> toReveal = new HashSet<>(hiddenArmorPlayers);
        toReveal.removeAll(currentlyHidden);

        for (UUID uuid : toReveal) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                showArmorForViewers(player);
            }
        }

        hiddenArmorPlayers.clear();
        hiddenArmorPlayers.addAll(currentlyHidden);
    }

    private boolean shouldHideArmor(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        return arena != null
                && arena.getState() == Arena.GameState.IN_GAME
                && player.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }

    private void hideArmorForViewers(Player target) {
        ItemStack empty = new ItemStack(Material.AIR);
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target) || !viewer.getWorld().equals(target.getWorld())) {
                continue;
            }
            viewer.sendEquipmentChange(target, EquipmentSlot.HEAD, empty);
            viewer.sendEquipmentChange(target, EquipmentSlot.CHEST, empty);
            viewer.sendEquipmentChange(target, EquipmentSlot.LEGS, empty);
            viewer.sendEquipmentChange(target, EquipmentSlot.FEET, empty);
        }
    }

    private void showArmorForViewers(Player target) {
        org.bukkit.inventory.PlayerInventory inventory = target.getInventory();
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target) || !viewer.getWorld().equals(target.getWorld())) {
                continue;
            }
            viewer.sendEquipmentChange(target, EquipmentSlot.HEAD, normalizeEquipment(inventory.getHelmet()));
            viewer.sendEquipmentChange(target, EquipmentSlot.CHEST, normalizeEquipment(inventory.getChestplate()));
            viewer.sendEquipmentChange(target, EquipmentSlot.LEGS, normalizeEquipment(inventory.getLeggings()));
            viewer.sendEquipmentChange(target, EquipmentSlot.FEET, normalizeEquipment(inventory.getBoots()));
        }
    }

    private void spawnInvisibilityParticles(Player invisiblePlayer) {
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(invisiblePlayer);
        if (arena == null) return;
        org.bukkit.Location loc = invisiblePlayer.getLocation().add(0, 1.0, 0);
        for (Player viewer : arena.getPlayers()) {
            if (viewer.equals(invisiblePlayer)) continue;
            viewer.spawnParticle(org.bukkit.Particle.SPELL_MOB, loc, 5, 0.25, 0.4, 0.25, 0);
        }
    }

    private ItemStack normalizeEquipment(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private boolean isBedBlock(org.bukkit.Location bedLoc, Block block) {
        if (bedLoc == null) return false;
        return block.getLocation().distance(bedLoc) <= 1.5;
    }

    private void clearBedBlocks(Block block) {
        block.setType(Material.AIR);
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST }) {
            Block adjacent = block.getRelative(face);
            String adjName = adjacent.getType().name();
            if (adjName.endsWith("_BED") || adjName.equals("BED_BLOCK")) {
                adjacent.setType(Material.AIR);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInHand();
        
        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "special_item");
            if ("base_selection_tool".equals(special)) {
                event.setCancelled(true);
                return;
            }
        }

        if (BedWars.getInstance().getGameManager().isInBuildMode(player)) {
            return;
        }

        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME) {
            if (player.hasPermission("bedwars.admin")) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        for (Team t : arena.getTeams()) {
            if (t.getBasePos1() != null && t.getBasePos2() != null) {
                if (isInside(event.getBlock().getLocation(), t.getBasePos1(), t.getBasePos2())) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text(
                        BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "interact-cant-build-in-base")
                        .replace("%color%", t.getColor().toString())
                        .replace("%team%", t.getDisplayName())
                    ));
                    return;
                }
            }
        }

        arena.getPlacedBlocks().add(event.getBlock().getLocation());
    }

    private boolean isInside(org.bukkit.Location loc, org.bukkit.Location p1, org.bukkit.Location p2) {
        if (p1 == null || p2 == null || loc == null) return false;
        try {
            if (loc.getWorld() == null || p1.getWorld() == null) return false;
            if (!loc.getWorld().getName().equals(p1.getWorld().getName())) return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double maxY = Math.max(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);

        if (arena == null || arena.getState() != Arena.GameState.IN_GAME) {
            int voidY = BedWars.getInstance().getConfig().getInt("game.void-y-level", 0);
            if (player.getLocation().getY() < voidY) {
                Location lobby = BedWars.getInstance().getGameManager().getMainLobbyLocation();
                if (lobby != null) {
                    player.teleport(lobby);
                    player.sendMessage(Component.text(
                        BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "void-teleport-lobby")
                    ));
                }
            }
            return;
        }

        int voidY = BedWars.getInstance().getConfig().getInt("game.void-y-level", 0);
        if (player.getLocation().getY() < voidY) {
            if (player.getHealth() > 0) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                player.damage(0.1);
                BedWars.getInstance().getGameManager().handleDeath(player, arena, "fell into the void.");
            }
        }
    }

    private Team findClosestTeam(Arena arena, org.bukkit.Location loc) {
        Team closest = null;
        double distSq = Double.MAX_VALUE;
        for (Team t : arena.getTeams()) {
            double d = Double.MAX_VALUE;
            try {
                if (t.getSpawnLocation() != null && t.getSpawnLocation().getWorld() != null)
                    d = Math.min(d, loc.distanceSquared(t.getSpawnLocation()));
                if (t.getBedLocation() != null && t.getBedLocation().getWorld() != null)
                    d = Math.min(d, loc.distanceSquared(t.getBedLocation()));
            } catch (IllegalArgumentException e) {
            }
            if (d < distSq && d < 10000) {
                distSq = d;
                closest = t;
            }
        }
        return closest;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null) return;
        if (arena.getState() != Arena.GameState.IN_GAME) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
            return;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena != null && arena.getState() == Arena.GameState.IN_GAME) {
            event.setCancelled(true);
            if (player.getFoodLevel() < 20) {
                player.setFoodLevel(20);
            }
            player.setSaturation(20f);
            player.setExhaustion(0f);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        for (Arena arena : BedWars.getInstance().getArenaManager().getArenas()) {
            if (arena.getWorldName() != null && arena.getWorldName().equals(event.getWorld().getName())) {
                if (event.toWeatherState()) {
                    event.setCancelled(true);
                }
                break;
            }
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        for (Arena arena : BedWars.getInstance().getArenaManager().getArenas()) {
            if (arena.getWorldName() != null && arena.getWorldName().equals(event.getLocation().getWorld().getName())) {
                if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                        || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                        || event.getEntityType() == EntityType.VILLAGER
                        || event.getEntityType() == EntityType.ARMOR_STAND
                        || event.getEntityType() == EntityType.ENDER_DRAGON
                        || event.getEntityType() == EntityType.SILVERFISH
                        || event.getEntityType() == EntityType.IRON_GOLEM) {
                    return;
                }
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == org.bukkit.entity.EntityType.IRON_GOLEM
                || event.getEntityType() == org.bukkit.entity.EntityType.SILVERFISH) {
            if (hasScoreboardTag(event.getEntity(), "bw_mob")) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }
    }

    private boolean hasScoreboardTag(org.bukkit.entity.Entity entity, String tag) {
        try {
            return entity.getScoreboardTags().contains(tag);
        } catch (NoSuchMethodError e) {
            return false;
        }
    }
}
