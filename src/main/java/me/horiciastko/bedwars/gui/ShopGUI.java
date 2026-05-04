package me.horiciastko.bedwars.gui;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.logic.ShopManager;
import me.horiciastko.bedwars.models.Team;
import me.horiciastko.bedwars.utils.ItemBuilder;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

@SuppressWarnings("deprecation")
public class ShopGUI extends BaseGUI {

    private final String category;

    public ShopGUI(String category) {
        super(BedWars.getInstance().getLanguageManager().getMessage(null, "shop-title"), 6);
        this.category = (category == null || category.isEmpty()) ? "quick_buy" : category;
    }

    public ShopGUI() {
        this("quick_buy");
    }

    @Override
    public void setContents(Player player) {
        me.horiciastko.bedwars.models.Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        String group = (arena != null) ? arena.getGroup() : "Default";

        ConfigurationSection shopConfig = BedWars.getInstance().getLanguageManager().getConfig(player.getUniqueId(),
                "shop");

        shopConfig = BedWars.getInstance().getConfigManager().getShopConfig(group);

        ShopManager shopManager = BedWars.getInstance().getShopManager();
        ItemStack background = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, background);
        }

        ConfigurationSection categories = shopConfig.getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                String name = ChatColor.translateAlternateColorCodes('&', categories.getString(key + ".name", key));
                int slot = categories.getInt(key + ".slot");
                if (slot < 0 || slot >= 9)
                    continue;

                XMaterial icon = XMaterial.matchXMaterial(categories.getString(key + ".icon", "BARRIER"))
                        .orElse(XMaterial.BARRIER);

                ItemBuilder builder = new ItemBuilder(icon).setName(name);
                if (key.equalsIgnoreCase(this.category)) {
                    builder.addEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
                    builder.hideAttributes();
                    builder.addLoreLine(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "shop-selected"));
                } else {
                    builder.addLoreLine(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                            "shop-click-to-view"));
                }

                inventory.setItem(slot, builder.build());
            }
        }

        if (this.category.equalsIgnoreCase("quick_buy")) {
            setQuickBuyContents(player, shopConfig, shopManager);
        } else {
            setSectionContents(player, shopConfig, shopManager);
        }
    }

    private void setQuickBuyContents(Player player, ConfigurationSection shopConfig, ShopManager shopManager) {
        Map<Integer, String[]> customQuickBuy = shopManager.getPlayerQuickBuy(player);
        Integer editingSlot = shopManager.getEditingSlot(player);

        int[] itemSlots = { 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };

        for (int slot : itemSlots) {
            if (editingSlot != null && editingSlot == slot) {
                inventory.setItem(slot, new ItemBuilder(XMaterial.YELLOW_STAINED_GLASS_PANE)
                        .setName(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                                "shop-editing-name"))
                        .setLore(BedWars.getInstance().getLanguageManager().getMessageList(player.getUniqueId(),
                                "shop-editing-lore"))
                        .build());
                continue;
            }

            if (customQuickBuy.containsKey(slot)) {
                String[] itemInfo = customQuickBuy.get(slot);
                String cat = itemInfo[0];
                String key = itemInfo[1];

                if (cat.equalsIgnoreCase("EMPTY")) {
                    inventory.setItem(slot, createEmptySlot(slot));
                    continue;
                }

                ConfigurationSection itemData = shopConfig.getConfigurationSection("sections." + cat + ".items." + key);

                if (itemData != null) {
                    inventory.setItem(slot, createShopItem(player, itemData, true));
                } else {
                    shopManager.removeQuickBuy(player, slot);
                    inventory.setItem(slot, createEmptySlot(slot));
                }
            } else {
                ConfigurationSection defaultQuickBuy = shopConfig.getConfigurationSection("sections.quick_buy.items");
                boolean foundDefault = false;
                if (defaultQuickBuy != null) {
                    for (String key : defaultQuickBuy.getKeys(false)) {
                        if (defaultQuickBuy.getInt(key + ".slot") == slot) {
                            inventory.setItem(slot,
                                    createShopItem(player, defaultQuickBuy.getConfigurationSection(key), true));
                            foundDefault = true;
                            break;
                        }
                    }
                }

                if (!foundDefault) {
                    inventory.setItem(slot, createEmptySlot(slot));
                }
            }
        }
    }

    private void setSectionContents(Player player, ConfigurationSection shopConfig, ShopManager shopManager) {
        ConfigurationSection section = shopConfig.getConfigurationSection("sections." + this.category);
        if (section == null)
            return;

        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemData = items.getConfigurationSection(key);
                if (itemData == null)
                    continue;
                int slot = itemData.getInt("slot");
                ItemStack shopItem = createShopItem(player, itemData, false);
                if (shopItem != null && shopItem.getType() != Material.AIR) {
                    inventory.setItem(slot, shopItem);
                }
            }
        }
    }

    private ItemStack createEmptySlot(int slot) {
        return new ItemBuilder(XMaterial.RED_STAINED_GLASS_PANE)
                .setName("§c+")
                .setLore(java.util.Arrays.asList(
                        "§7This is a Quick Buy slot!",
                        "",
                        "§eClick an item in another",
                        "§ecategory while holding",
                        "§eshift to add it here!"))
                .build();
    }

    private ItemStack createShopItem(Player player, ConfigurationSection itemData, boolean inQuickBuy) {
        XMaterial xMat = XMaterial.matchXMaterial(itemData.getString("material", "BARRIER")).orElse(XMaterial.BARRIER);
        String name = itemData.getString("name");
        if (name != null) {
            name = ChatColor.translateAlternateColorCodes('&', name);
        } else {
            name = BedWars.getInstance().getLanguageManager().getItemName(player.getUniqueId(), xMat.parseMaterial());
        }
        int amount = itemData.getInt("amount", 1);
        int cost = itemData.getInt("cost", 0);
        XMaterial xCurrency = XMaterial.matchXMaterial(itemData.getString("currency", "IRON_INGOT"))
                .orElse(XMaterial.IRON_INGOT);
        java.util.List<String> description = itemData.getStringList("lore");
        if (description == null)
            description = new java.util.ArrayList<>();

        if (itemData.contains("tiers")) {
            boolean isPick = xMat.name().endsWith("_PICKAXE");
            boolean isAxe = xMat.name().endsWith("_AXE");


            int currentTier = 0;
            if (isPick) {
                currentTier = BedWars.getInstance().getGameManager().getPlayerPickaxeTier(player.getUniqueId());
            } else if (isAxe) {
                currentTier = BedWars.getInstance().getGameManager().getPlayerAxeTier(player.getUniqueId());
            }

            int nextTier = currentTier + 1;
            ConfigurationSection tierData = itemData.getConfigurationSection("tiers." + nextTier);

            if (tierData == null) {
                xMat = XMaterial.BARRIER;
                name = "§cMAX TIER";
                cost = 0;
                description.clear();
                description.add("§7You have reached the maximum tier.");
            } else {
                xMat = XMaterial.matchXMaterial(tierData.getString("material", "BARRIER")).orElse(XMaterial.BARRIER);
                name = ChatColor.translateAlternateColorCodes('&', tierData.getString("name", name));
                name = name.replace("%tier%", String.valueOf(nextTier));

                cost = tierData.getInt("cost", cost);
                xCurrency = XMaterial.matchXMaterial(tierData.getString("currency", "IRON_INGOT"))
                        .orElse(XMaterial.IRON_INGOT);

                java.util.List<String> tierLore = tierData.getStringList("lore");
                if (!tierLore.isEmpty()) {
                    description = tierLore;
                }
            }
        } else if (xMat.name().endsWith("_PICKAXE") || xMat.name().endsWith("_AXE")) {
            boolean isPick = xMat.name().endsWith("_PICKAXE");
            int tier = isPick ? BedWars.getInstance().getGameManager().getPlayerPickaxeTier(player.getUniqueId())
                    : BedWars.getInstance().getGameManager().getPlayerAxeTier(player.getUniqueId());

            if (tier >= 4) {
                xMat = XMaterial.BARRIER;
                name = "§cMAX TIER";
                cost = 0;
            } else {
                int next = tier + 1;
                switch (next) {
                    case 1:
                        xMat = isPick ? XMaterial.WOODEN_PICKAXE : XMaterial.WOODEN_AXE;
                        break;
                    case 2:
                        xMat = isPick ? XMaterial.STONE_PICKAXE : XMaterial.STONE_AXE;
                        break;
                    case 3:
                        xMat = isPick ? XMaterial.IRON_PICKAXE : XMaterial.IRON_AXE;
                        break;
                    default:
                        xMat = isPick ? XMaterial.DIAMOND_PICKAXE : XMaterial.DIAMOND_AXE;
                        break;
                }
                name = (next == 1 ? "Wooden " : next == 2 ? "Stone " : next == 3 ? "Iron " : "Diamond ")
                        + (isPick ? "Pickaxe" : "Axe");

                if (next <= 2) {
                    cost = 10;
                    xCurrency = XMaterial.IRON_INGOT;
                } else {
                    cost = (next == 3) ? 10 : 20;
                    xCurrency = XMaterial.GOLD_INGOT;
                }
            }
        }

        ItemBuilder builder = new ItemBuilder(xMat, amount).setName(name);

        if (cost > 0 || !xMat.name().contains("GLASS_PANE")) {
            String currencyName;
            switch (xCurrency) {
                case IRON_INGOT:
                    currencyName = "Iron";
                    break;
                case GOLD_INGOT:
                    currencyName = "Gold";
                    break;
                case EMERALD:
                    currencyName = "Emerald";
                    break;
                default:
                    currencyName = xCurrency.name();
                    break;
            }

            String costColor;
            switch (xCurrency) {
                case IRON_INGOT:
                    costColor = "§f";
                    break;
                case GOLD_INGOT:
                    costColor = "§6";
                    break;
                case EMERALD:
                    costColor = "§2";
                    break;
                default:
                    costColor = "§7";
                    break;
            }

            if (cost > 0) {
                builder.addLoreLine(
                        BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-cost")
                                .replace("%color%", costColor).replace("%amount%", String.valueOf(cost))
                                .replace("%currency%", currencyName));
                builder.addLoreLine("");
            }
        }

        if (!description.isEmpty()) {
            for (String line : description) {
                builder.addLoreLine("§7" + ChatColor.translateAlternateColorCodes('&', line));
            }
            builder.addLoreLine("");
        }

        if (xMat != XMaterial.BARRIER && !xMat.name().contains("GLASS_PANE")) {
            Material currencyMat = xCurrency.parseMaterial();
            if (currencyMat != null
                    && !me.horiciastko.bedwars.utils.InventoryUtils.hasEnough(player, currencyMat, cost)) {
                String currencyName;
                switch (xCurrency) {
                    case IRON_INGOT:
                        currencyName = "Iron";
                        break;
                    case GOLD_INGOT:
                        currencyName = "Gold";
                        break;
                    case EMERALD:
                        currencyName = "Emerald";
                        break;
                    default:
                        currencyName = xCurrency.name();
                        break;
                }
                builder.addLoreLine(
                        BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-not-enough")
                                .replace("%currency%", currencyName));
            }
        }

        if (inQuickBuy && !xMat.name().contains("GLASS_PANE") && xMat != XMaterial.BARRIER) {
            builder.addLoreLine(
                    BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-sneak-remove"));
        } else if (!inQuickBuy && xMat != XMaterial.BARRIER) {
            builder.addLoreLine(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "shop-right-click-add"));
        }

        return builder.build();
    }

    @Override
    public void handleAction(Player player, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (item == null || item.getType().isAir())
            return;
        if (item.getType().name().contains("GLASS_PANE") && item.hasItemMeta()) {
            String displayName = item.getItemMeta().getDisplayName();
            if (" ".equals(displayName) || displayName == null) {
                return;
            }
        }

        me.horiciastko.bedwars.models.Arena arena = BedWars.getInstance().getArenaManager().getPlayerArena(player);
        String group = (arena != null) ? arena.getGroup() : "Default";

        ConfigurationSection shopConfig = BedWars.getInstance().getConfigManager().getShopConfig(group);
        ShopManager shopManager = BedWars.getInstance().getShopManager();

        ConfigurationSection categories = shopConfig.getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                if (categories.getInt(key + ".slot") == slot) {
                    new ShopGUI(key).open(player);
                    return;
                }
            }
        }

        if (this.category.equalsIgnoreCase("quick_buy")) {
            handleQuickBuyInteraction(player, slot, item, clickType, shopConfig, shopManager);
        } else {
            handleSectionInteraction(player, slot, item, clickType, shopConfig, shopManager);
        }
    }

    private void handleQuickBuyInteraction(Player player, int slot, ItemStack item,
            org.bukkit.event.inventory.ClickType clickType, ConfigurationSection shopConfig, ShopManager shopManager) {

        if (clickType.isShiftClick() && clickType.isRightClick()) {
            shopManager.updateQuickBuy(player, slot, "EMPTY", "EMPTY");
            String msg = BedWars.getInstance().getLanguageManager()
                    .getMessage(player.getUniqueId(), "quick-buy-removed")
                    .replace("%slot%", String.valueOf(slot));
            player.sendMessage(msg);
            new ShopGUI("quick_buy").open(player);
            return;
        }

        if (item.getType().name().contains("RED_STAINED_GLASS_PANE")) {
            return;
        }

        Map<Integer, String[]> customQuickBuy = shopManager.getPlayerQuickBuy(player);
        if (customQuickBuy.containsKey(slot)) {
            String[] info = customQuickBuy.get(slot);
            if (info[0].equalsIgnoreCase("EMPTY")) {
                new QuickBuySelectorGUI(slot, 0).open(player);
                return;
            }
            ConfigurationSection itemData = shopConfig
                    .getConfigurationSection("sections." + info[0] + ".items." + info[1]);
            if (itemData != null)
                processPurchase(player, itemData);
        } else {
            ConfigurationSection defaults = shopConfig.getConfigurationSection("sections.quick_buy.items");
            if (defaults != null) {
                for (String key : defaults.getKeys(false)) {
                    if (defaults.getInt(key + ".slot") == slot) {
                        processPurchase(player, defaults.getConfigurationSection(key));
                        break;
                    }
                }
            }
        }
    }

    private void handleSectionInteraction(Player player, int slot, ItemStack item,
            org.bukkit.event.inventory.ClickType clickType, ConfigurationSection shopConfig, ShopManager shopManager) {
        ConfigurationSection section = shopConfig.getConfigurationSection("sections." + this.category);
        if (section == null)
            return;
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null)
            return;

        String itemKey = null;
        ConfigurationSection itemData = null;
        for (String key : items.getKeys(false)) {
            if (items.getInt(key + ".slot") == slot) {
                itemKey = key;
                itemData = items.getConfigurationSection(key);
                break;
            }
        }

        if (itemData == null)
            return;

        if (clickType.isShiftClick()) {
            Integer targetSlot = findFirstAvailableQuickBuySlot(player, shopManager, shopConfig);
            if (targetSlot != null) {
                shopManager.updateQuickBuy(player, targetSlot, this.category, itemKey);

                String itemName = ChatColor.translateAlternateColorCodes('&',
                        itemData.getString("name", item.getType().name()));
                player.sendMessage(
                        BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "quick-buy-added")
                                .replace("%item%", itemName).replace("%slot%", targetSlot.toString()));

                new ShopGUI("quick_buy").open(player);
            } else {
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-quick-buy-full"));
            }
            return;
        }

        processPurchase(player, itemData);
    }

    private Integer findFirstAvailableQuickBuySlot(Player player, ShopManager shopManager,
            ConfigurationSection shopConfig) {
        int[] itemSlots = { 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };
        Map<Integer, String[]> custom = shopManager.getPlayerQuickBuy(player);

        for (int slot : itemSlots) {
            if (!custom.containsKey(slot) || custom.get(slot)[0].equalsIgnoreCase("EMPTY")) {
                boolean hasDefault = false;
                ConfigurationSection defaults = shopConfig.getConfigurationSection("sections.quick_buy.items");
                if (defaults != null) {
                    for (String key : defaults.getKeys(false)) {
                        if (defaults.getInt(key + ".slot") == slot) {
                            hasDefault = true;
                            break;
                        }
                    }
                }
                if (!hasDefault)
                    return slot;
            }
        }
        return itemSlots[0];
    }

    private void processPurchase(Player player, ConfigurationSection itemData) {
        XMaterial xCurrency = XMaterial.matchXMaterial(itemData.getString("currency", "IRON_INGOT"))
                .orElse(XMaterial.IRON_INGOT);
        int cost = itemData.getInt("cost");
        int amount = itemData.getInt("amount", 1);
        XMaterial xMat = XMaterial.matchXMaterial(itemData.getString("material", "BARRIER")).orElse(XMaterial.BARRIER);
        String name = itemData.getString("name");
        if (name != null) {
            name = ChatColor.translateAlternateColorCodes('&', name);
        } else {
            name = BedWars.getInstance().getLanguageManager().getItemName(player.getUniqueId(), xMat.parseMaterial());
        }

        if (itemData.contains("tiers")) {
            boolean isPick = xMat.name().endsWith("_PICKAXE");
            boolean isAxe = xMat.name().endsWith("_AXE");

            int currentTier = 0;
            if (isPick) {
                currentTier = BedWars.getInstance().getGameManager().getPlayerPickaxeTier(player.getUniqueId());
            } else if (isAxe) {
                currentTier = BedWars.getInstance().getGameManager().getPlayerAxeTier(player.getUniqueId());
            }

            int nextTier = currentTier + 1;
            ConfigurationSection tierData = itemData.getConfigurationSection("tiers." + nextTier);

            if (tierData != null) {
                cost = tierData.getInt("cost", cost);
                xCurrency = XMaterial.matchXMaterial(tierData.getString("currency", "IRON_INGOT"))
                        .orElse(XMaterial.IRON_INGOT);
                xMat = XMaterial.matchXMaterial(tierData.getString("material", xMat.name())).orElse(xMat);
                name = ChatColor.translateAlternateColorCodes('&', tierData.getString("name", name));
            } else {
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-best-tool-already"));
                return;
            }
        } else if (xMat.name().endsWith("_PICKAXE") || xMat.name().endsWith("_AXE")) {
            boolean isPick = xMat.name().endsWith("_PICKAXE");
            int tier = isPick ? BedWars.getInstance().getGameManager().getPlayerPickaxeTier(player.getUniqueId())
                    : BedWars.getInstance().getGameManager().getPlayerAxeTier(player.getUniqueId());

            if (tier >= 4) {
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "shop-best-tool-already"));
                return;
            }

            int next = tier + 1;
            if (next <= 2) {
                cost = 10;
                xCurrency = XMaterial.IRON_INGOT;
            } else {
                cost = (next == 3) ? 10 : 20;
                xCurrency = XMaterial.GOLD_INGOT;
            }
        }

        Material currencyMat = xCurrency.parseMaterial();
        if (currencyMat != null && me.horiciastko.bedwars.utils.InventoryUtils.hasEnough(player, currencyMat, cost)) {
            ItemStack rawGiving = xMat.parseItem();
            if (rawGiving == null)
                rawGiving = new ItemStack(Material.BARRIER);
            final ItemStack giving = rawGiving;
            giving.setAmount(amount);

            if (xMat == XMaterial.STICK && name.toLowerCase().contains("knockback")) {
                giving.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1);
            }

            Team team = BedWars.getInstance().getGameManager().getPlayerTeam(
                    BedWars.getInstance().getArenaManager().getPlayerArena(player), player);

            if (team != null) {
                String teamColorName = team.getColor().name();
                if (xMat == XMaterial.WHITE_WOOL) {
                    XMaterial.matchXMaterial(teamColorName + "_WOOL").ifPresent(xm -> xm.setType(giving));
                } else if (xMat == XMaterial.TERRACOTTA) {
                    XMaterial.matchXMaterial(teamColorName + "_TERRACOTTA").ifPresent(xm -> xm.setType(giving));
                } else if (xMat == XMaterial.GLASS) {
                    XMaterial.matchXMaterial(teamColorName + "_STAINED_GLASS").ifPresent(xm -> xm.setType(giving));
                    org.bukkit.inventory.meta.ItemMeta meta = giving.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§fBlast-Proof Glass");
                        giving.setItemMeta(meta);
                    }
                }
            }

            if (xMat == XMaterial.POTION) {
                org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) giving.getItemMeta();
                if (pm != null) {
                    if (name.toLowerCase().contains("speed")) {
                        pm.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,
                                45 * 20, 1), true);
                        pm.setColor(org.bukkit.Color.AQUA);
                    } else if (name.toLowerCase().contains("jump")) {
                        pm.addCustomEffect(
                                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 45 * 20, 4),
                                true);
                        pm.setColor(org.bukkit.Color.GREEN);
                    } else if (name.toLowerCase().contains("invisibility")) {
                        pm.addCustomEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.INVISIBILITY, 30 * 20, 0), true);
                        pm.setColor(org.bukkit.Color.GRAY);
                    }
                    giving.setItemMeta(pm);
                }
            }

            boolean isSpecial = xMat.name().endsWith("_SWORD") || xMat.name().endsWith("_PICKAXE")
                    || xMat.name().endsWith("_AXE") || (xMat.name().endsWith("_BOOTS") && (xMat.name().contains("CHAIN")
                            || xMat.name().contains("IRON") || xMat.name().contains("DIAMOND")));

            if (!isSpecial && !me.horiciastko.bedwars.utils.InventoryUtils.canFit(player, giving)) {
                player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                        "shop-inventory-full"));
                BedWars.getInstance().getSoundManager().playSound(player, "shop-insufficient-money");
                return;
            }

            me.horiciastko.bedwars.utils.InventoryUtils.removeItems(player, currencyMat, cost);

            if (giving.getType() != Material.AIR) {
                org.bukkit.inventory.meta.ItemMeta meta = giving.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(name);
                    giving.setItemMeta(meta);
                    if (itemData.getName().equals("tower")) {
                        me.horiciastko.bedwars.utils.ItemTagUtils.setTag(giving, "special_item", "tower");
                    } else if (itemData.getName().equals("bridge_egg")) {
                        me.horiciastko.bedwars.utils.ItemTagUtils.setTag(giving, "special_item", "bridge_egg");
                    } else if (itemData.getName().equals("tracker")) {
                        me.horiciastko.bedwars.utils.ItemTagUtils.setTag(giving, "special_item", "tracker");
                    }
                }
            }

            if (xMat.name().endsWith("_SWORD")) {
                removeSimilarItems(player, "_SWORD");
                player.getInventory().addItem(giving);
                BedWars.getInstance().getGameManager().giveStartingKit(player, team);
            } else if (xMat.name().endsWith("_PICKAXE")) {
                int currentTier = BedWars.getInstance().getGameManager().getPlayerPickaxeTier(player.getUniqueId());
                BedWars.getInstance().getGameManager().setPlayerPickaxeTier(player.getUniqueId(), currentTier + 1);
                BedWars.getInstance().getGameManager().giveStartingKit(player, team);
            } else if (xMat.name().endsWith("_AXE")) {
                int currentTier = BedWars.getInstance().getGameManager().getPlayerAxeTier(player.getUniqueId());
                BedWars.getInstance().getGameManager().setPlayerAxeTier(player.getUniqueId(), currentTier + 1);
                BedWars.getInstance().getGameManager().giveStartingKit(player, team);
            } else if (xMat.name().endsWith("_BOOTS") && (xMat.name().contains("CHAIN") || xMat.name().contains("IRON")
                    || xMat.name().contains("DIAMOND"))) {
                String rawTier = xMat.name().split("_")[0];
                String tier = rawTier.equals("CHAINMAIL") ? "CHAIN" : rawTier;
                BedWars.getInstance().getGameManager().setPlayerArmorTier(player, tier);
            } else {
                player.getInventory().addItem(giving);
            }

            String purchaseMsg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "shop-purchased");
            player.sendMessage(purchaseMsg.replace("%item%", name));
            BedWars.getInstance().getSoundManager().playSound(player, "shop-bought");

            new ShopGUI(this.category).open(player);
        } else {
            String errorMsg = BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(),
                    "shop-not-enough");
            String currencyName;
            switch (xCurrency) {
                case IRON_INGOT:
                    currencyName = "Iron";
                    break;
                case GOLD_INGOT:
                    currencyName = "Gold";
                    break;
                case EMERALD:
                    currencyName = "Emerald";
                    break;
                default:
                    currencyName = xCurrency.name();
                    break;
            }
            player.sendMessage(errorMsg.replace("%currency%", currencyName));
            BedWars.getInstance().getSoundManager().playSound(player, "shop-insufficient-money");
        }
    }

    private void removeSimilarItems(Player player, String suffix) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);
            if (invItem != null && invItem.getType().name().endsWith(suffix)) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
