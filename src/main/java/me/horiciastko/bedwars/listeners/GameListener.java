package me.horiciastko.bedwars.listeners;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.gui.ShopGUI;
import me.horiciastko.bedwars.gui.UpgradeGUI;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.models.Team;
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

@SuppressWarnings("deprecation")
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

    // Cancel fire-charge block ignitions inside BedWars arenas — prevents the vanilla
    // "right-click block with fire charge = place fire" behaviour from triggering when
    // the player actually wants to throw a fireball.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        if (event.getCause() != org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }
        // Fire charges share the FLINT_AND_STEEL cause when used on a block.
        // Only cancel when a BedWars player triggers it to avoid interfering with
        // flint-and-steel used outside game scope.
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
                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "wand-no-team-nearby"));
                    } else {
                        targetTeam.setBasePos1(event.getBlock().getLocation());
                        String location = event.getBlock().getX() + ", " + event.getBlock().getY() + ", "
                                + event.getBlock().getZ();
                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "wand-base-pos1-set")
                                .replace("%color%", targetTeam.getColor().toString())
                                .replace("%team%", targetTeam.getDisplayName())
                                .replace("%location%", location));
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
            // Allow OPs and admins to break blocks freely outside of a game.
            if (player.hasPermission("bedwars.admin")) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();

        if (block.getType().name().endsWith("_BED")) {
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
                // Sponge/wet_sponge: suppress the default drop (which would be wet_sponge) and
                // give back a regular dry sponge instead
                if (block.getType() == Material.SPONGE || block.getType() == Material.WET_SPONGE) {
                    event.setDropItems(false);
                    block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SPONGE, 1));
                }
            } else {
                event.setCancelled(true);
                String msg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "interact-cant-break");
                player.sendMessage(msg);
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
            player.sendMessage(msg);
            return;
        }

        if (victimTeam.isBedBroken()) {
            event.setCancelled(true);
            clearBedBlocks(event.getBlock());
            return;
        }

        Team attackerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);

        if (attackerTeam == null) {
            event.setCancelled(true);
            return;
        }

        if (attackerTeam.getName().equals(victimTeam.getName())) {
            event.setCancelled(true);
            String msg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "interact-cant-destroy-bed");
            player.sendMessage(msg);
        } else {
            event.setCancelled(true);
            clearBedBlocks(event.getBlock());

            victimTeam.setBedBroken(true);

            String attackerColor = (attackerTeam.getColor() != null ? attackerTeam.getColor().toString() : "§f");

            String bedMsg = BedWars.getInstance().getLanguageManager().getMessage(null, "interact-bed-destroy-chat");
            String formattedMsg = bedMsg
                    .replace("{TeamColor}", victimTeam.getColor() != null ? victimTeam.getColor().toString() : "§f")
                    .replace("{TeamName}", victimTeam.getName())
                    .replace("{PlayerColor}", attackerColor)
                    .replace("{PlayerName}", player.getName());

            for (Player p : arena.getPlayers()) {
                p.sendMessage(formattedMsg);
                if (p.getUniqueId().equals(player.getUniqueId()) || attackerTeam.getMembers().contains(p)) {
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

    private ItemStack normalizeEquipment(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private boolean isBedBlock(org.bukkit.Location bedLoc, Block block) {
        if (bedLoc == null)
            return false;
        return block.getLocation().distance(bedLoc) <= 1.5;
    }

    private void clearBedBlocks(Block block) {
        block.setType(Material.AIR);
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST }) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().name().endsWith("_BED")) {
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
            // Allow OPs and admins to place blocks freely outside of a game.
            if (player.hasPermission("bedwars.admin")) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        // When sneaking and right-clicking a bed while holding a block, Minecraft bypasses
        // the bed interaction and fires BlockPlaceEvent instead — cancel it so the bed GUI opens.
        if (player.isSneaking()) {
            org.bukkit.block.Block against = event.getBlockAgainst();
            if (against != null && against.getType().name().contains("BED")) {
                event.setCancelled(true);
                return;
            }
        }

        for (Team t : arena.getTeams()) {
            if (t.getBasePos1() != null && t.getBasePos2() != null) {
                if (isInside(event.getBlock().getLocation(), t.getBasePos1(), t.getBasePos2())) {
                    event.setCancelled(true);
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "interact-cant-build-in-base")
                            .replace("%color%", t.getColor().toString())
                            .replace("%team%", t.getDisplayName()));
                    return;
                }
            }
        }

        for (org.bukkit.Location loc : arena.getDiamondGenerators()) {
            if (event.getBlock().getLocation().distance(loc) < 1.6) {
                event.setCancelled(true);
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "interact-cant-build-generator"));
                return;
            }
        }
        for (org.bukkit.Location loc : arena.getEmeraldGenerators()) {
            if (event.getBlock().getLocation().distance(loc) < 1.6) {
                event.setCancelled(true);
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "interact-cant-build-generator"));
                return;
            }
        }

        // Also protect per-team iron generators
        for (me.horiciastko.bedwars.models.Team t : arena.getTeams()) {
            for (org.bukkit.Location loc : t.getGenerators()) {
                if (event.getBlock().getLocation().distance(loc) < 1.6) {
                    event.setCancelled(true);
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "interact-cant-build-generator"));
                    return;
                }
            }
        }

        if (event.getBlock().getType() == Material.TNT) {
            Team playerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
            if (playerTeam != null) {
                for (Team t : arena.getTeams()) {
                    if (!t.getName().equals(playerTeam.getName())) {
                        if (isInside(event.getBlock().getLocation(), t.getBasePos1(), t.getBasePos2())) {
                            event.setCancelled(true);
                            player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                    player.getUniqueId(), "interact-cant-build"));
                            return;
                        }
                    }
                }
            }
            
            event.setCancelled(true);
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
            }
            org.bukkit.Location tntLoc = event.getBlock().getLocation().add(0.5, 0, 0.5);
            org.bukkit.entity.TNTPrimed tnt = event.getBlock().getWorld().spawn(tntLoc,
                    org.bukkit.entity.TNTPrimed.class);
            int fuse = 52;
            tnt.setFuseTicks(fuse);
            tnt.setSource(player);

            if (BedWars.getInstance().getConfig().getBoolean("game.tnt.timer-enabled", true)) {
                org.bukkit.entity.ArmorStand timer = tntLoc.getWorld().spawn(tntLoc.clone().add(0, 0.5, 0),
                        org.bukkit.entity.ArmorStand.class, as -> {
                            as.setVisible(false);
                            as.setGravity(false);
                            as.setSmall(true);
                            as.setMarker(true);
                            as.setCustomNameVisible(true);
                            as.setCustomName("§e" + String.format("%.1f", fuse / 20.0));
                        });

                new org.bukkit.scheduler.BukkitRunnable() {
                    int remaining = fuse;

                    @Override
                    public void run() {
                        if (tnt.isDead() || !tnt.isValid()) {
                            timer.remove();
                            this.cancel();
                            return;
                        }
                        remaining--;
                        if (remaining % 2 == 0) {
                            timer.setCustomName("§c" + String.format("%.1f", remaining / 20.0) + "s");
                        }
                        timer.teleport(tnt.getLocation().add(0, 1.2, 0));
                    }
                }.runTaskTimer(BedWars.getInstance(), 0L, 1L);
            }
            return;
        }

        if (event.getBlock().getType() == Material.CHEST) {
            ItemStack chestItem = event.getItemInHand();
            if (chestItem != null && chestItem.hasItemMeta()) {
                String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(chestItem, "special_item");
                if ("tower".equals(special)) {
                    event.setCancelled(true);
                    if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        chestItem.setAmount(chestItem.getAmount() - 1);
                    }
                    me.horiciastko.bedwars.utils.TowerBuilder.build(player, arena, event.getBlock().getLocation());
                    return;
                }
            }
        }

        arena.getPlacedBlocks().add(event.getBlock().getLocation());
    }

    private boolean isInside(org.bukkit.Location loc, org.bukkit.Location p1, org.bukkit.Location p2) {
        if (p1 == null || p2 == null || loc == null)
            return false;

        try {
            if (loc.getWorld() == null || p1.getWorld() == null)
                return false;
            if (!loc.getWorld().getName().equals(p1.getWorld().getName()))
                return false;
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
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "void-teleport-lobby"));
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
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL)
            return;

        Team myTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
        for (Team t : arena.getTeams()) {
            if (myTeam != null && myTeam.getName().equals(t.getName()))
                continue;
            if (t.getActiveTraps().isEmpty())
                continue;

            if (isInside(player.getLocation(), t.getBasePos1(), t.getBasePos2())) {
                if (BedWars.getInstance().getGameManager().hasTrapImmunity(player.getUniqueId())) {
                    continue;
                }

                String trapKey = t.getActiveTraps().remove(0);
                triggerTrap(t, player, trapKey);
                break;
            }
        }
    }

    private void triggerTrap(Team defendingTeam, Player intruder, String trapKey) {
        ConfigurationSection trapData = BedWars.getInstance().getConfigManager().getUpgradesConfig()
                .getConfigurationSection("trap-types." + trapKey);
        if (trapData == null)
            return;

        String name = org.bukkit.ChatColor.translateAlternateColorCodes('&', trapData.getString("name", "Trap"));
        defendingTeam.getMembers().forEach(m -> {
            BedWars.getInstance().sendTitle(m,
                    BedWars.getInstance().getLanguageManager().getMessage(m.getUniqueId(), "trap-triggered-title"),
                    BedWars.getInstance().getLanguageManager().getMessage(m.getUniqueId(), "trap-triggered-subtitle")
                            .replace("%trap%", name),
                    10, 40, 10);
            m.playSound(m.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        });

        String effects = trapData.getString("effect", "");
        if (effects.equals("ALARM")) {
            intruder.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            intruder.addPotionEffect(
                    new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 10 * 20, 0));
        } else {
            for (String eff : effects.split(",")) {
                String[] parts = eff.split(":");
                String effectName = parts[0];
                int duration = Integer.parseInt(parts[1]) * 20;
                int amp = Integer.parseInt(parts[2]) - 1;
                intruder.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.getByName(effectName), duration, amp));
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);

        if (arena == null) {
            return;
        }
        
        if (arena.getState() != Arena.GameState.IN_GAME) {
            event.setCancelled(true);
            return;
        }

        // Prevent dropping items while falling into the void (exploit: drop before death to deny kill loot)
        int voidY = BedWars.getInstance().getConfig().getInt("game.void-y-level", 0);
        if (player.getLocation().getY() < voidY) {
            event.setCancelled(true);
            return;
        }

        Material type = event.getItemDrop().getItemStack().getType();
        String name = type.name();
        int heldSlot = player.getInventory().getHeldItemSlot();

        if (type == Material.WOODEN_SWORD || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_PICKAXE") || name.endsWith("_AXE") || type == Material.SHEARS
                || name.contains("BED")) {
            event.setCancelled(true);
            return;
        }

        if (isBetterSword(event.getItemDrop().getItemStack())) {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    Arena currentArena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
                    if (currentArena == null || currentArena.getState() != Arena.GameState.IN_GAME) {
                        return;
                    }

                    Team team = BedWars.getInstance().getGameManager().getPlayerTeam(currentArena, player);
                    BedWars.getInstance().getGameManager().ensureWoodenSwordInSlot(player, team, heldSlot);
                }
            }.runTask(BedWars.getInstance());
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.player.PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena != null && arena.getState() == Arena.GameState.IN_GAME) {
            BedWars.getInstance().getLanguageManager().localizeItem(player.getUniqueId(),
                    event.getItem().getItemStack());

            if (isBetterSword(event.getItem().getItemStack())) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        Arena currentArena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
                        if (currentArena == null || currentArena.getState() != Arena.GameState.IN_GAME) {
                            return;
                        }

                        Team team = BedWars.getInstance().getGameManager().getPlayerTeam(currentArena, player);
                        moveBetterSwordToWoodenSlot(player);
                        BedWars.getInstance().getGameManager().normalizeSwordInventory(player, team);
                    }
                }.runTask(BedWars.getInstance());
            }
        }

        if (event.getItem().getItemStack().getType().name().contains("BED")) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }

        if (handleWoodenSwordInventoryClick(event, player)) {
            return;
        }

        // Prevent placing permanent kit items into any external inventory (chest, etc.)
        org.bukkit.inventory.Inventory topInv = event.getView().getTopInventory();
        boolean isExternalContainer = topInv != null
                && topInv.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
                && topInv.getType() != org.bukkit.event.inventory.InventoryType.PLAYER
                && !(event.getView().getTopInventory().getHolder() instanceof me.horiciastko.bedwars.gui.BaseGUI);

        if (isExternalContainer) {
            ItemStack moved = null;

            // SHIFT_CLICK from player inventory moves to container
            if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
                moved = event.getCurrentItem();
            }
            // Any click placing cursor into container slots
            else if (event.getClickedInventory() == topInv) {
                moved = event.getCursor();
                
                if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                    moved = player.getInventory().getItem(event.getHotbarButton());
                } else {
                    try {
                        if (event.getClick().name().equals("SWAP_OFFHAND")) {
                            moved = player.getInventory().getItemInOffHand();
                        }
                    } catch (NoSuchMethodError | Exception ignored) {}
                }
            }
            if (isBwPermanentItem(moved)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getCurrentItem() != null) {
            BedWars.getInstance().getLanguageManager().localizeItem(player.getUniqueId(), event.getCurrentItem());
        }
        if (event.getCursor() != null) {
            BedWars.getInstance().getLanguageManager().localizeItem(player.getUniqueId(), event.getCursor());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        org.bukkit.inventory.Inventory topInv = event.getView().getTopInventory();
        boolean isExternalContainer = topInv != null
                && topInv.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
                && topInv.getType() != org.bukkit.event.inventory.InventoryType.PLAYER
                && !(event.getView().getTopInventory().getHolder() instanceof me.horiciastko.bedwars.gui.BaseGUI);

        if (isExternalContainer) {
            if (isBwPermanentItem(event.getOldCursor())) {
                for (int slot : event.getRawSlots()) {
                    if (slot < topInv.getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player player = (Player) event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        scheduleSwordNormalization(player);
    }

    private boolean isBwPermanentItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        // BOW is intentionally NOT in this list — players may freely store bows in chests.
        return name.equals("WOODEN_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("SHEARS") || name.equals("STICK");
    }

    private boolean handleWoodenSwordInventoryClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() != player.getInventory()) {
            return false;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isWoodenSword(current)) {
            if (isBetterSword(cursor)) {
                replaceWoodenSwordSlot(event, cursor, -1);
                return true;
            }

            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (isBetterSword(hotbarItem)) {
                    replaceWoodenSwordSlot(event, hotbarItem, event.getHotbarButton());
                    return true;
                }
            }
        }

        return false;
    }

    private void replaceWoodenSwordSlot(InventoryClickEvent event, ItemStack swordItem, int hotbarButton) {
        event.setCancelled(true);

        ItemStack replacement = swordItem.clone();
        event.getWhoClicked().getInventory().setItem(event.getSlot(), replacement);

        if (hotbarButton >= 0) {
            event.getWhoClicked().getInventory().setItem(hotbarButton, null);
        } else {
            event.setCursor(new ItemStack(Material.AIR));
        }
    }

    private boolean isWoodenSword(ItemStack item) {
        return item != null && item.getType() == Material.WOODEN_SWORD;
    }

    private boolean isBetterSword(ItemStack item) {
        return item != null && item.getType() != Material.WOODEN_SWORD
                && item.getType().name().endsWith("_SWORD");
    }

    private void scheduleSwordNormalization(Player player) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Arena currentArena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
                if (currentArena == null || currentArena.getState() != Arena.GameState.IN_GAME) {
                    return;
                }

                Team team = BedWars.getInstance().getGameManager().getPlayerTeam(currentArena, player);
                BedWars.getInstance().getGameManager().normalizeSwordInventory(player, team);
            }
        }.runTask(BedWars.getInstance());
    }

    private void moveBetterSwordToWoodenSlot(Player player) {
        int woodenSlot = findFirstSwordSlot(player, true);
        int betterSwordSlot = findFirstSwordSlot(player, false);
        if (woodenSlot < 0 || betterSwordSlot < 0 || woodenSlot == betterSwordSlot) {
            return;
        }

        ItemStack betterSword = player.getInventory().getItem(betterSwordSlot);
        if (betterSword == null || !isBetterSword(betterSword)) {
            return;
        }

        player.getInventory().setItem(woodenSlot, betterSword);
        player.getInventory().setItem(betterSwordSlot, null);
    }

    private int findFirstSwordSlot(Player player, boolean woodenOnly) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (woodenOnly && isWoodenSword(item)) {
                return i;
            }
            if (!woodenOnly && isBetterSword(item)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        // Prevent feeding iron ingots to bw_mob golems (repair)
        if (event.getRightClicked() instanceof org.bukkit.entity.IronGolem) {
            if (event.getRightClicked().getScoreboardTags().contains("bw_mob")) {
                ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
                if (inHand.getType() == Material.IRON_INGOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!(event.getRightClicked() instanceof Villager))
            return;
        Villager villager = (Villager) event.getRightClicked();

        if (!villager.getScoreboardTags().contains("bw_npc"))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        String name = villager.getCustomName();
        if (name == null)
            return;
        name = org.bukkit.ChatColor.stripColor(name);

        if (name.contains("SHOP")) {
            new ShopGUI().open(player);
        } else if (name.contains("UPGRADES")) {
            new UpgradeGUI().open(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "special_item");

            if ("base_selection_tool".equals(special)) {
                event.setCancelled(true);
                
                if (event.getClickedBlock() == null) {
                    return;
                }
                
                Arena editArena = BedWars.getInstance().getArenaManager().getEditArena(player);
                if (editArena == null) {
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "interact-edit-mode-required"));
                    return;
                }
                Team targetTeam = findClosestTeam(editArena, event.getClickedBlock().getLocation());

                if (targetTeam == null) {
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "wand-no-team-nearby"));
                    return;
                }

                String location = event.getClickedBlock().getX() + ", " + event.getClickedBlock().getY() + ", "
                        + event.getClickedBlock().getZ();
                String teamColor = targetTeam.getColor().toString();
                String teamName = targetTeam.getDisplayName();

                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    targetTeam.setBasePos1(event.getClickedBlock().getLocation());
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "wand-base-pos1-set")
                            .replace("%color%", teamColor)
                            .replace("%team%", teamName)
                            .replace("%location%", location));
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    targetTeam.setBasePos2(event.getClickedBlock().getLocation());
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "wand-base-pos2-set")
                            .replace("%color%", teamColor)
                            .replace("%team%", teamName)
                            .replace("%location%", location));
                }
                BedWars.getInstance().getArenaManager().saveArena(editArena);
                return;
            }
        }

        if (!BedWars.getInstance().getGameManager().isInBuildMode(player) &&
                (arena != null && arena.getState() != Arena.GameState.IN_GAME)) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
                if (!event.getClickedBlock().getType().name().contains("SIGN")) {
                    event.setCancelled(true);
                }
            }
        }

        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().name().contains("BED")) {
            event.setCancelled(true);
        }

        // Block hopper interaction in-game
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.HOPPER) {
            event.setCancelled(true);
            return;
        }

        // Block placing water in any base region
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)
                && event.getItem() != null && event.getItem().getType() == Material.WATER_BUCKET) {
            if (event.getClickedBlock() != null) {
                org.bukkit.Location placeLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                for (Team t : arena.getTeams()) {
                    if (isInside(placeLoc, t.getBasePos1(), t.getBasePos2())) {
                        event.setCancelled(true);
                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "interact-cant-build"));
                        return;
                    }
                }
            }
        }

        ItemStack itemStack = event.getItem();
        if (itemStack == null)
            return;

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && itemStack.getType() == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            try {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            } catch (NoSuchMethodError ignored) {
            }
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                itemStack.setAmount(itemStack.getAmount() - 1);
            }

            org.bukkit.Location spawnLoc = player.getEyeLocation()
                    .add(player.getEyeLocation().getDirection().multiply(1.0));
            org.bukkit.entity.Fireball fireball = (org.bukkit.entity.Fireball) player.getWorld()
                    .spawnEntity(spawnLoc, org.bukkit.entity.EntityType.FIREBALL);

            fireball.setYield(2.5f);
            fireball.setIsIncendiary(false);
            fireball.setShooter(player);
            fireball.setVelocity(player.getEyeLocation().getDirection().multiply(1.2));

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GHAST_SHOOT, 1f, 1f);
            return;
        }

        if (!itemStack.hasItemMeta())
            return;

        String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(itemStack, "special_item");
        if (special == null)
            special = "";

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (special.equals("tower")) {
                if (event.getClickedBlock() == null)
                    return;
                    
                org.bukkit.Location towerLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();

                // Block tower in any base (own or enemy) and near generators
                for (Team t : arena.getTeams()) {
                    if (isInside(towerLoc, t.getBasePos1(), t.getBasePos2())) {
                        event.setCancelled(true);
                        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                player.getUniqueId(), "interact-cant-build"));
                        return;
                    }
                }
                // Generator proximity check — compute full 4x4 footprint of the tower
                int tOx = 0, tOz = 0;
                switch (player.getFacing()) {
                    case NORTH: tOx = -1; tOz = -3; break;
                    case SOUTH: tOx = -2; tOz =  0; break;
                    case EAST:  tOx =  0; tOz = -1; break;
                    case WEST:  tOx = -3; tOz = -2; break;
                    default:    tOx = -1; tOz = -1; break;
                }
                org.bukkit.Location towerBase = towerLoc.clone().add(tOx, 0, tOz);
                boolean towerNearGen = false;
                outer:
                for (int tx = 0; tx < 4; tx++) {
                    for (int tz = 0; tz < 4; tz++) {
                        org.bukkit.Location footprint = towerBase.clone().add(tx, 0, tz);
                        for (org.bukkit.Location gen : arena.getDiamondGenerators()) {
                            if (footprint.distance(gen) <= 1.6) { towerNearGen = true; break outer; }
                        }
                        for (org.bukkit.Location gen : arena.getEmeraldGenerators()) {
                            if (footprint.distance(gen) <= 1.6) { towerNearGen = true; break outer; }
                        }
                        for (Team genTeam : arena.getTeams()) {
                            for (org.bukkit.Location gen : genTeam.getGenerators()) {
                                if (footprint.distance(gen) <= 1.6) { towerNearGen = true; break outer; }
                            }
                        }
                    }
                }
                if (towerNearGen) {
                    event.setCancelled(true);
                    player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                            player.getUniqueId(), "interact-cant-build"));
                    return;
                }

                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    itemStack.setAmount(itemStack.getAmount() - 1);
                }
                me.horiciastko.bedwars.utils.TowerBuilder.build(player, arena, towerLoc);
            } else if (itemStack.getType() == Material.SNOWBALL) {
                // Bedbug: thrown as a projectile; silverfish spawns on landing (ProjectileListener)
                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    itemStack.setAmount(itemStack.getAmount() - 1);
                }
                org.bukkit.entity.Snowball thrown = player.launchProjectile(org.bukkit.entity.Snowball.class);
                thrown.addScoreboardTag("bw_bedbug");
                Team throwTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                if (throwTeam != null) {
                    thrown.addScoreboardTag("team_" + throwTeam.getName());
                }
            } else if (itemStack.getType().name().contains("SPAWN_EGG")) {
                // Dream Defender: placed on a block surface
                if (event.getClickedBlock() == null)
                    return;

                Team playerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                org.bukkit.Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                if (playerTeam != null) {
                    for (Team t : arena.getTeams()) {
                        if (!t.getName().equals(playerTeam.getName())) {
                            if (isInside(spawnLoc, t.getBasePos1(), t.getBasePos2())) {
                                event.setCancelled(true);
                                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                        player.getUniqueId(), "interact-cant-build"));
                                return;
                            }
                        }
                    }
                }

                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    itemStack.setAmount(itemStack.getAmount() - 1);
                }

                org.bukkit.entity.IronGolem golem = event.getClickedBlock().getWorld().spawn(
                        spawnLoc, org.bukkit.entity.IronGolem.class);
                golem.setCustomName(BedWars.getInstance().getLanguageManager().getMessage(
                        player.getUniqueId(), "entity-dream-defender"));
                golem.setCustomNameVisible(true);
                golem.addScoreboardTag("bw_mob");
                if (playerTeam != null) {
                    golem.addScoreboardTag("team_" + playerTeam.getName());
                }
                // Despawn countdown hologram above golem
                final int despawnSeconds = 120;
                org.bukkit.entity.ArmorStand timerStand = spawnLoc.getWorld().spawn(
                        spawnLoc.clone().add(0, 2.5, 0), org.bukkit.entity.ArmorStand.class, as -> {
                            as.setVisible(false);
                            as.setGravity(false);
                            as.setSmall(true);
                            as.setMarker(true);
                            as.setCustomNameVisible(true);
                            as.setCustomName("§b§l" + despawnSeconds + "s");
                        });
                new org.bukkit.scheduler.BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (!golem.isValid() || golem.isDead() || ticks >= despawnSeconds * 20) {
                            if (golem.isValid() && !golem.isDead()) golem.setHealth(0);
                            timerStand.remove();
                            this.cancel();
                            return;
                        }
                        timerStand.teleport(golem.getLocation().add(0, 2.5, 0));
                        if (ticks % 20 == 0) {
                            int remaining = despawnSeconds - (ticks / 20);
                            timerStand.setCustomName("§b§l" + remaining + "s");
                        }
                        ticks++;
                    }
                }.runTaskTimer(BedWars.getInstance(), 1L, 1L);
            } else if (itemStack.getType() == Material.DRAGON_EGG) {
                if (event.getClickedBlock() == null)
                    return;
                    
                Team playerTeam2 = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                org.bukkit.Location eggLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                if (playerTeam2 != null) {
                    for (Team t : arena.getTeams()) {
                        if (!t.getName().equals(playerTeam2.getName())) {
                            if (isInside(eggLoc, t.getBasePos1(), t.getBasePos2())) {
                                event.setCancelled(true);
                                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(
                                        player.getUniqueId(), "interact-cant-build"));
                                return;
                            }
                        }
                    }
                }
                
                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    itemStack.setAmount(itemStack.getAmount() - 1);
                }

                Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                BedWars.getInstance().getGameManager().spawnTeamDragon(player, arena, spawnLoc);
            }
        }
    }

    // Intercept milk bucket consumption so the 2nd magic milk doesn't act as normal milk
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerConsumeMilk(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET)
            return;
        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        // Save current potion effects before the milk consumes and clears them.
        // Do NOT cancel — let Bukkit handle the animation and bucket replacement naturally.
        // Restore effects + grant immunity 1 tick later to counter the milk clearing effect.
        java.util.Collection<org.bukkit.potion.PotionEffect> savedEffects =
                new java.util.ArrayList<>(player.getActivePotionEffects());

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                // Re-apply effects the milk may have cleared
                for (org.bukkit.potion.PotionEffect effect : savedEffects) {
                    player.addPotionEffect(effect, true);
                }
                BedWars.getInstance().getGameManager().setTrapImmunity(player.getUniqueId(), 30);
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "interact-magic-milk"));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
            }
        }.runTaskLater(BedWars.getInstance(), 1L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConsumePotion(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.POTION)
            return;

        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
                try {
                    if (player.getInventory().getItemInOffHand().getType() == Material.GLASS_BOTTLE) {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    }
                } catch (NoSuchMethodError ignored) {
                }
                player.updateInventory();
            }
        }.runTask(BedWars.getInstance());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onWaterBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET)
            return;

        Player player = event.getPlayer();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME
            || player.getGameMode() == org.bukkit.GameMode.CREATIVE)
            return;

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.getInventory().getItemInMainHand().getType() == Material.BUCKET) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
                try {
                    if (player.getInventory().getItemInOffHand().getType() == Material.BUCKET) {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    }
                } catch (NoSuchMethodError ignored) {
                }
                player.updateInventory();
            }
        }.runTask(BedWars.getInstance());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Villager) {
            org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) event.getEntity();
            if (villager.getScoreboardTags().contains("bw_npc") || villager.getScoreboardTags().contains("bw_npc_shop")
                    || villager.getScoreboardTags().contains("bw_npc_upgrades")) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();

        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent)
                && isPeacefulLikeDamage(event.getCause())) {
            event.setCancelled(true);
            return;
        }

        try {
            if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                if (arena.getPvpMode() == Arena.PvpMode.LEGACY_1_8) {
                    event.setCancelled(true);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            org.bukkit.event.entity.EntityDamageByEntityEvent edbe = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;

            // Friendly fire protection — cancel damage between teammates
            {
                boolean isExplosion = edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed
                        || edbe.getDamager() instanceof org.bukkit.entity.Fireball;
                boolean preventExplosion = BedWars.getInstance().getConfig()
                        .getBoolean("game.prevent-team-explosion-damage", true);

                Player ffAttacker = null;
                if (edbe.getDamager() instanceof Player) {
                    ffAttacker = (Player) edbe.getDamager();
                } else if (edbe.getDamager() instanceof org.bukkit.entity.Projectile) {
                    org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) edbe.getDamager();
                    if (proj.getShooter() instanceof Player) ffAttacker = (Player) proj.getShooter();
                } else if (edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
                    org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) edbe.getDamager();
                    if (tnt.getSource() instanceof Player) ffAttacker = (Player) tnt.getSource();
                } else if (edbe.getDamager() instanceof org.bukkit.entity.Fireball) {
                    org.bukkit.entity.Fireball fb = (org.bukkit.entity.Fireball) edbe.getDamager();
                    if (fb.getShooter() instanceof Player) ffAttacker = (Player) fb.getShooter();
                }

                // For explosions, only block if config says so
                boolean shouldCheck = !isExplosion || preventExplosion;
                if (shouldCheck && ffAttacker != null && !ffAttacker.equals(player)) {
                    Team atkTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, ffAttacker);
                    Team vicTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                    if (atkTeam != null && vicTeam != null && atkTeam.getName().equals(vicTeam.getName())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // Knockback stick: apply manual knockback in 1.21+ where KNOCKBACK enchant on sticks is ignored
            if (edbe.getDamager() instanceof Player) {
                Player attacker = (Player) edbe.getDamager();
                ItemStack weapon = attacker.getInventory().getItemInHand();
                if (weapon != null && weapon.getType() == Material.STICK
                        && weapon.containsEnchantment(org.bukkit.enchantments.Enchantment.KNOCKBACK)) {
                    int lvl = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK);
                    org.bukkit.util.Vector dir = player.getLocation()
                            .subtract(attacker.getLocation()).toVector();
                    dir.setY(0);
                    if (dir.lengthSquared() > 0) dir.normalize();
                    dir.multiply(0.4 + lvl * 0.4).setY(0.35);
                    // Schedule one tick later so it overrides vanilla knockback
                    final org.bukkit.util.Vector finalDir = dir;
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override public void run() {
                            if (player.isOnline()) player.setVelocity(player.getVelocity().add(finalDir));
                        }
                    }.runTaskLater(BedWars.getInstance(), 1L);
                }
            }

            if (edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed
                    || edbe.getDamager() instanceof org.bukkit.entity.Fireball) {

                boolean tntDmgEnabled = BedWars.getInstance().getConfig().getBoolean("game.tnt.damage-enabled", false);
                double tntDmg = BedWars.getInstance().getConfig().getDouble("game.tnt.damage-amount", 2.0);
                double tntTeamDmg = BedWars.getInstance().getConfig().getDouble("game.tnt.team-damage-amount", 1.0);
                boolean fireballDmgEnabled = BedWars.getInstance().getConfig()
                        .getBoolean("game.fireball.damage-enabled", true);
                double fireballDmg = BedWars.getInstance().getConfig().getDouble("game.fireball.damage-amount", 4.0);
                double fireballTeamDmg = BedWars.getInstance().getConfig().getDouble("game.fireball.team-damage-amount", 2.0);

                // Check if victim is a teammate of the explosion source
                boolean isTeammate = false;
                {
                    Player expSource = null;
                    if (edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
                        org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) edbe.getDamager();
                        if (tnt.getSource() instanceof Player) expSource = (Player) tnt.getSource();
                    } else if (edbe.getDamager() instanceof org.bukkit.entity.Fireball) {
                        org.bukkit.entity.Fireball fb = (org.bukkit.entity.Fireball) edbe.getDamager();
                        if (fb.getShooter() instanceof Player) expSource = (Player) fb.getShooter();
                    }
                    if (expSource != null && !expSource.equals(player)) {
                        Team srcTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, expSource);
                        Team vicTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                        if (srcTeam != null && vicTeam != null && srcTeam.getName().equals(vicTeam.getName())) {
                            isTeammate = true;
                        }
                    }
                }

                double tntJump = BedWars.getInstance().getConfig().getDouble("game.tnt.jump-power", 2.5);
                double fbJump = BedWars.getInstance().getConfig().getDouble("game.fireball.jump-power", 2.0);

                double jumpPower = edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed ? tntJump : fbJump;

                if (edbe.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
                    double finalDmg = tntDmgEnabled ? (isTeammate ? tntTeamDmg : tntDmg) : 0;
                    if (player.getHealth() > finalDmg) {
                        event.setDamage(finalDmg);
                    } else if (tntDmgEnabled) {
                        event.setDamage(Math.min(player.getHealth() - 0.1, finalDmg));
                    } else {
                        event.setDamage(0);
                    }
                } else {
                    double finalDmg = fireballDmgEnabled ? (isTeammate ? fireballTeamDmg : fireballDmg) : 0;
                    if (player.getHealth() > finalDmg) {
                        event.setDamage(finalDmg);
                    } else if (fireballDmgEnabled) {
                        event.setDamage(Math.min(player.getHealth() - 0.1, finalDmg));
                    } else {
                        event.setDamage(0);
                    }
                }

                org.bukkit.util.Vector direction = player.getLocation().toVector()
                        .subtract(edbe.getDamager().getLocation().toVector()).normalize();

                direction.setY(0.5);
                direction.normalize().multiply(jumpPower);

                player.setAllowFlight(true);
                player.setVelocity(player.getVelocity().add(direction));

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                                    player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                                player.setAllowFlight(false);
                                player.setFlying(false);
                            }
                        }
                    }
                }.runTaskLater(BedWars.getInstance(), 40L);
            }
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            String reason = "died.";
            if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
                reason = "fell into the void.";
            } else if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.event.entity.EntityDamageByEntityEvent damageByEntity = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;
                if (damageByEntity.getDamager() instanceof Player) {
                    Player killer = (Player) damageByEntity.getDamager();
                    reason = "was killed by " + killer.getName() + ".";
                } else if (damageByEntity.getDamager() instanceof org.bukkit.entity.Projectile) {
                    org.bukkit.entity.Projectile arrow = (org.bukkit.entity.Projectile) damageByEntity.getDamager();
                    if (arrow.getShooter() instanceof Player) {
                        Player shooter = (Player) arrow.getShooter();
                        reason = "was shot by " + shooter.getName() + ".";
                    }
                } else if (damageByEntity.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
                    org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) damageByEntity.getDamager();
                    if (tnt.getSource() instanceof Player) {
                        Player killer = (Player) tnt.getSource();
                        reason = "was blown up by " + killer.getName() + ".";
                    }
                } else if (damageByEntity.getDamager() instanceof org.bukkit.entity.Fireball) {
                    org.bukkit.entity.Fireball fireball = (org.bukkit.entity.Fireball) damageByEntity.getDamager();
                    if (fireball.getShooter() instanceof Player) {
                        Player killer = (Player) fireball.getShooter();
                        reason = "was fireballed by " + killer.getName() + ".";
                    }
                }
            }
            BedWars.getInstance().getGameManager().handleDeath(player, arena, reason);
        }

        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            org.bukkit.event.entity.EntityDamageByEntityEvent edbe_fix = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;
            Player damager = null;
            if (edbe_fix.getDamager() instanceof Player) {
                damager = (Player) edbe_fix.getDamager();
            } else if (edbe_fix.getDamager() instanceof org.bukkit.entity.Projectile) {
                org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) edbe_fix.getDamager();
                if (proj.getShooter() instanceof Player) {
                    damager = (Player) proj.getShooter();
                }
            } else if (edbe_fix.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
                org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) edbe_fix.getDamager();
                if (tnt.getSource() instanceof Player) {
                    damager = (Player) tnt.getSource();
                }
            }

            if (damager != null) {
                BedWars.getInstance().getGameManager().setLastDamager(player, damager);
            }
        }

        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            org.bukkit.event.entity.EntityDamageByEntityEvent edbe = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;
            if (edbe.getDamager().getScoreboardTags().contains("bw_mob")) {
                if (BedWars.getInstance().getConfig().getBoolean("game.prevent-own-mob-damage", true)) {
                    String mobTeamTag = edbe.getDamager().getScoreboardTags().stream()
                            .filter(t -> t.startsWith("team_")).findFirst().orElse(null);
                    me.horiciastko.bedwars.models.Team playerTeam = BedWars.getInstance().getGameManager()
                            .getPlayerTeam(arena, player);
                    if (mobTeamTag != null && playerTeam != null && mobTeamTag.equals("team_" + playerTeam.getName())) {
                        event.setCancelled(true);
                    }
                }
            }

            if (edbe.getEntity().getScoreboardTags().contains("bw_mob")
                    && edbe.getDamager() instanceof Player) {
                Player damager = (Player) edbe.getDamager();
                Team damagerTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, damager);
                String mobTeamTag = edbe.getEntity().getScoreboardTags().stream()
                        .filter(t -> t.startsWith("team_")).findFirst().orElse(null);
                if (mobTeamTag != null && damagerTeam != null && mobTeamTag.equals("team_" + damagerTeam.getName())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (!shouldPreventHungerLoss(player)) {
            return;
        }

        event.setCancelled(true);
        if (player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
        }
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    private boolean shouldPreventHungerLoss(Player player) {
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena != null) {
            return true;
        }

        Location mainLobby = BedWars.getInstance().getGameManager().getMainLobbyLocation();
        if (mainLobby == null || mainLobby.getWorld() == null || player.getWorld() == null) {
            return false;
        }

        return mainLobby.getWorld().getUID().equals(player.getWorld().getUID());
    }

    private boolean isPeacefulLikeDamage(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case HOT_FLOOR:
            case DROWNING:
            case SUFFOCATION:
            case CONTACT:
            case STARVATION:
            case POISON:
            case WITHER:
            case MAGIC:
            case DRAGON_BREATH:
                return true;
            default:
                return false;
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
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!event.getEntity().getScoreboardTags().contains("bw_mob"))
            return;

        String teamTag = event.getEntity().getScoreboardTags().stream()
                .filter(t -> t.startsWith("team_"))
                .findFirst().orElse(null);

        if (teamTag == null)
            return;

        if (event.getTarget() instanceof Player) {
            Player victim = (Player) event.getTarget();
            Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(victim);
            if (arena == null)
                return;
            Team victimTeam = BedWars.getInstance().getGameManager().getPlayerTeam(arena, victim);
            if (victimTeam != null && teamTag.equals("team_" + victimTeam.getName())) {
                event.setCancelled(true);
                return;
            }

            if (event.getEntity() instanceof org.bukkit.entity.IronGolem) {
                if (victimTeam != null && !teamTag.equals("team_" + victimTeam.getName())) {
                    if (victimTeam.getBasePos1() != null && victimTeam.getBasePos2() != null) {
                        Location loc = victim.getLocation();
                        Location p1 = victimTeam.getBasePos1();
                        Location p2 = victimTeam.getBasePos2();
                        double minX = Math.min(p1.getX(), p2.getX()) - 2;
                        double maxX = Math.max(p1.getX(), p2.getX()) + 2;
                        double minY = Math.min(p1.getY(), p2.getY()) - 2;
                        double maxY = Math.max(p1.getY(), p2.getY()) + 2;
                        double minZ = Math.min(p1.getZ(), p2.getZ()) - 2;
                        double maxZ = Math.max(p1.getZ(), p2.getZ()) + 2;

                        if (loc.getX() >= minX && loc.getX() <= maxX &&
                                loc.getY() >= minY && loc.getY() <= maxY &&
                                loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        } else if (event.getTarget() != null && event.getTarget().getScoreboardTags().contains("bw_mob")) {
            String targetTeamTag = event.getTarget().getScoreboardTags().stream()
                    .filter(t -> t.startsWith("team_"))
                    .findFirst().orElse(null);
            if (targetTeamTag != null && targetTeamTag.equalsIgnoreCase(teamTag)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Arena arena = null;
        for (Arena a : BedWars.getInstance().getArenaManager().getArenas()) {
            if (a.getWorldName() != null && a.getWorldName().equals(event.getLocation().getWorld().getName())) {
                arena = a;
                break;
            }
        }

        if (arena == null || arena.getState() != Arena.GameState.IN_GAME) {
            return;
        }

        if (event.getEntity() instanceof EnderDragon
                && event.getEntity().getScoreboardTags().contains("bw_sudden_death")) {
            java.util.Iterator<Block> it = event.blockList().iterator();
            while (it.hasNext()) {
                Block b = it.next();
                if (b.getType().name().endsWith("_BED") || b.getType() == org.bukkit.Material.BEDROCK) {
                    it.remove();
                }
            }
            return;
        }

        java.util.Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (!arena.getPlacedBlocks().contains(b.getLocation())) {
                it.remove();
                continue;
            }

            if (b.getType().name().contains("GLASS") || b.getType().name().endsWith("_BED")) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onDragonExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof EnderDragon || event.getEntityType() == EntityType.ENDER_DRAGON) {
            if (event.getEntity().getScoreboardTags().contains("bw_sudden_death")) {
                return;
            }
        }
    }

    @EventHandler
    public void onDragonBlockChange(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof EnderDragon || event.getEntityType() == EntityType.ENDER_DRAGON) {
            if (event.getEntity().getScoreboardTags().contains("bw_sudden_death")) {
                if (event.getBlock().getType().name().endsWith("_BED")
                        || event.getBlock().getType() == org.bukkit.Material.BEDROCK) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player))
            return;
        Player player = (Player) event.getEntity().getShooter();
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta())
            return;
        if (event.getEntity() instanceof org.bukkit.entity.Egg) {
            boolean isBridgeEgg = false;
            ItemMeta im = item.getItemMeta();
            if (im != null) {
                String special = me.horiciastko.bedwars.utils.ItemTagUtils.getTag(item, "special_item");
                if ("bridge_egg".equals(special)) {
                    isBridgeEgg = true;
                }
            }

            if (isBridgeEgg) {
                Team team = BedWars.getInstance().getGameManager().getPlayerTeam(arena, player);
                String colorName = (team != null && team.getColor() != null) ? team.getColor().name() : "WHITE";
                Material wool;
                wool = com.cryptomorin.xseries.XMaterial.matchXMaterial(colorName + "_WOOL")
                        .orElse(com.cryptomorin.xseries.XMaterial.WHITE_WOOL).parseMaterial();

                final Material finalWool = wool;
                new org.bukkit.scheduler.BukkitRunnable() {
                    private org.bukkit.Location lastLocation = null;

                    @Override
                    public void run() {
                        if (event.getEntity().isDead() || !event.getEntity().isValid()) {
                            this.cancel();
                            return;
                        }

                        org.bukkit.Location current = event.getEntity().getLocation();
                        if (lastLocation == null)
                            lastLocation = current.clone();

                        double dist = current.distance(lastLocation);
                        int steps = (int) Math.max(1, Math.ceil(dist * 2));
                        org.bukkit.util.Vector vec = current.clone().subtract(lastLocation).toVector()
                                .multiply(1.0 / steps);

                        for (int i = 0; i <= steps; i++) {
                            org.bukkit.Location interp = lastLocation.clone().add(vec.clone().multiply(i)).subtract(0,
                                    2,
                                    0);
                            placeBridgeBlocks(interp, finalWool, arena, event.getEntity().getVelocity());
                        }
                        lastLocation = current.clone();
                    }

                    private void placeBridgeBlocks(org.bukkit.Location loc, Material wool, Arena arena,
                            org.bukkit.util.Vector vel) {
                        org.bukkit.util.Vector dir = vel.clone().setY(0).normalize();
                        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX());

                        for (int offset = -1; offset <= 1; offset++) {
                            org.bukkit.Location bLoc = loc.clone().add(side.clone().multiply(offset));
                            Block block = bLoc.getBlock();
                            if (block.getType() == Material.AIR || block.getType().name().contains("PLANT")) {
                                boolean hasPlayer = bLoc.getWorld()
                                        .getNearbyEntities(bLoc.clone().add(0.5, 0.5, 0.5), 0.3, 0.5, 0.3).stream()
                                        .anyMatch(e -> e instanceof Player);

                                if (!hasPlayer) {
                                    block.setType(wool);
                                    arena.getPlacedBlocks().add(block.getLocation());
                                }
                            }
                        }
                    }
                }.runTaskTimer(BedWars.getInstance(), 0L, 1L);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpongePlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.SPONGE)
            return;

        org.bukkit.Location loc = event.getBlock().getLocation();
        boolean absorbedWater = false;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.WATER) {
                        b.setType(Material.AIR);
                        absorbedWater = true;
                    }
                }
            }
        }

        // Remove the sponge block after absorbing water (1-tick delay so the place event finishes)
        if (absorbedWater) {
            final Block sponge = event.getBlock();
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (sponge.getType() == Material.SPONGE || sponge.getType() == Material.WET_SPONGE) {
                        sponge.setType(Material.AIR);
                    }
                }
            }.runTaskLater(BedWars.getInstance(), 1L);
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
            if (event.getEntity().getScoreboardTags().contains("bw_mob")) {
                event.getDrops().clear();
                event.setDroppedExp(0);
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
                if (t.getShopLocation() != null && t.getShopLocation().getWorld() != null)
                    d = Math.min(d, loc.distanceSquared(t.getShopLocation()));
                if (t.getBasePos1() != null && t.getBasePos1().getWorld() != null)
                    d = Math.min(d, loc.distanceSquared(t.getBasePos1()));
                if (t.getBasePos2() != null && t.getBasePos2().getWorld() != null)
                    d = Math.min(d, loc.distanceSquared(t.getBasePos2()));
            } catch (IllegalArgumentException e) {
            }

            if (d < distSq && d < 10000) {
                distSq = d;
                closest = t;
            }
        }
        return closest;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            return;
        Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        if (arena == null || arena.getState() != Arena.GameState.IN_GAME)
            return;
        if (arena.getPos1() == null || arena.getPos2() == null)
            return;
        Location to = event.getTo();
        if (to == null)
            return;
        double minX = Math.min(arena.getPos1().getX(), arena.getPos2().getX());
        double maxX = Math.max(arena.getPos1().getX(), arena.getPos2().getX());
        double minY = Math.min(arena.getPos1().getY(), arena.getPos2().getY()) - 20;
        double maxY = Math.max(arena.getPos1().getY(), arena.getPos2().getY()) + 20;
        double minZ = Math.min(arena.getPos1().getZ(), arena.getPos2().getZ());
        double maxZ = Math.max(arena.getPos1().getZ(), arena.getPos2().getZ());
        if (to.getX() < minX || to.getX() > maxX || to.getY() < minY || to.getY() > maxY
                || to.getZ() < minZ || to.getZ() > maxZ) {
            // Push spectator back inside bounds
            Location from = event.getFrom();
            event.setTo(new Location(from.getWorld(), from.getX(), Math.max(minY, Math.min(maxY, from.getY())),
                    from.getZ(), from.getYaw(), from.getPitch()));
        }
    }
}

