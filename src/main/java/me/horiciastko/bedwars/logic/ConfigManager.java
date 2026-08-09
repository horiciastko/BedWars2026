package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * ConfigManager - Paper API версия
 * Управление конфигурациями плагина
 */
public class ConfigManager {

    private final BedWars plugin;

    private FileConfiguration shopConfig;
    private File shopFile;

    private FileConfiguration upgradesConfig;
    private File upgradesFile;

    private FileConfiguration messagesConfig;
    private File messagesFile;

    private FileConfiguration generatorConfig;
    private File generatorFile;

    private FileConfiguration teamConfig;
    private File teamFile;

    private FileConfiguration npcConfig;
    private File npcFile;

    private FileConfiguration permissionsConfig;
    private File permissionsFile;

    /**
     * Инициализация менеджера конфигураций
     */
    public ConfigManager(BedWars plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    /**
     * Загрузка всех конфигурационных файлов
     */
    public void loadConfigs() {
        String lang = plugin.getConfig().getString("default-language", "en");
        File langDir = new File(plugin.getDataFolder(), "Languages/" + lang);
        if (!langDir.exists()) {
            if (!langDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create language directory: " + langDir.getPath());
            }
        }

        // Загрузка shop.yml
        shopFile = new File(langDir, "shop.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/shop.yml", false);
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);

        // Загрузка upgrades.yml
        upgradesFile = new File(langDir, "upgrades.yml");
        if (!upgradesFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/upgrades.yml", false);
        }
        upgradesConfig = YamlConfiguration.loadConfiguration(upgradesFile);

        // Загрузка messages.yml
        messagesFile = new File(langDir, "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Загрузка generators.yml
        generatorFile = new File(langDir, "generators.yml");
        if (!generatorFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/generators.yml", false);
        }
        generatorConfig = YamlConfiguration.loadConfiguration(generatorFile);

        // Загрузка teams.yml
        teamFile = new File(langDir, "teams.yml");
        if (!teamFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/teams.yml", false);
        }
        teamConfig = YamlConfiguration.loadConfiguration(teamFile);

        // Загрузка npc.yml
        npcFile = new File(langDir, "npc.yml");
        if (!npcFile.exists()) {
            plugin.saveResource("Languages/" + lang + "/npc.yml", false);
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);

        // Загрузка permissions.yml
        permissionsFile = new File(plugin.getDataFolder(), "permissions.yml");
        if (!permissionsFile.exists()) {
            plugin.saveResource("permissions.yml", false);
        }
        permissionsConfig = YamlConfiguration.loadConfiguration(permissionsFile);
    }

    /**
     * Получение конфига магазина
     */
    public FileConfiguration getShopConfig() {
        if (shopConfig == null)
            reloadShopConfig();
        return shopConfig;
    }

    /**
     * Получение конфига улучшений
     */
    public FileConfiguration getUpgradesConfig() {
        if (upgradesConfig == null)
            reloadUpgradesConfig();
        return upgradesConfig;
    }

    /**
     * Перезагрузка конфига магазина
     */
    public void reloadShopConfig() {
        if (shopFile != null) {
            try {
                shopConfig = YamlConfiguration.loadConfiguration(shopFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload shop.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Перезагрузка конфига улучшений
     */
    public void reloadUpgradesConfig() {
        if (upgradesFile != null) {
            try {
                upgradesConfig = YamlConfiguration.loadConfiguration(upgradesFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload upgrades.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Сохранение конфига магазина
     */
    public void saveShopConfig() {
        try {
            if (shopConfig != null && shopFile != null)
                shopConfig.save(shopFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save shop.yml: " + e.getMessage());
        }
    }

    /**
     * Сохранение конфига улучшений
     */
    public void saveUpgradesConfig() {
        try {
            if (upgradesConfig != null && upgradesFile != null)
                upgradesConfig.save(upgradesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save upgrades.yml: " + e.getMessage());
        }
    }

    /**
     * Получение конфига сообщений
     */
    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null)
            reloadMessagesConfig();
        return messagesConfig;
    }

    /**
     * Перезагрузка конфига сообщений
     */
    public void reloadMessagesConfig() {
        if (messagesFile != null) {
            try {
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload messages.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Перезагрузка всех конфигов
     */
    public void reloadAll() {
        plugin.reloadConfig();
        loadConfigs();
        
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().load();
        }
        if (plugin.getLanguageManager() != null) {
            plugin.getLanguageManager().load();
        }
        
        plugin.getLogger().info("All configurations reloaded successfully!");
    }

    /**
     * Получение конфига команд
     */
    public FileConfiguration getTeamConfig() {
        if (teamConfig == null)
            reloadTeamConfig();
        return teamConfig;
    }

    /**
     * Перезагрузка конфига команд
     */
    public void reloadTeamConfig() {
        if (teamFile != null) {
            try {
                teamConfig = YamlConfiguration.loadConfiguration(teamFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload teams.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Получение конфига генераторов
     */
    public FileConfiguration getGeneratorConfig() {
        if (generatorConfig == null)
            reloadGeneratorConfig();
        return generatorConfig;
    }

    /**
     * Перезагрузка конфига генераторов
     */
    public void reloadGeneratorConfig() {
        if (generatorFile != null) {
            try {
                generatorConfig = YamlConfiguration.loadConfiguration(generatorFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload generators.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Получение конфига магазина для группы
     */
    public FileConfiguration getShopConfig(String group) {
        if (group == null || group.equalsIgnoreCase("Default")) {
            return getShopConfig();
        }

        String fileName = plugin.getConfig().getString("groups." + group + ".shop-file");
        if (fileName == null)
            fileName = "shop.yml";

        String lang = plugin.getConfig().getString("default-language", "en");
        File file = new File(plugin.getDataFolder(), "Languages/" + lang + "/" + fileName);

        if (!file.exists()) {
            plugin.getLogger().warning("Shop config file not found for group '" + group + "': " + file.getPath());
            return getShopConfig();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Получение конфига улучшений для группы
     */
    public FileConfiguration getUpgradesConfig(String group) {
        if (group == null || group.equalsIgnoreCase("Default")) {
            return getUpgradesConfig();
        }

        String fileName = plugin.getConfig().getString("groups." + group + ".upgrades-file");
        if (fileName == null)
            fileName = "upgrades.yml";

        String lang = plugin.getConfig().getString("default-language", "en");
        File file = new File(plugin.getDataFolder(), "Languages/" + lang + "/" + fileName);

        if (!file.exists()) {
            plugin.getLogger().warning("Upgrades config file not found for group '" + group + "': " + file.getPath());
            return getUpgradesConfig();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Получение NPC конфига
     */
    public FileConfiguration getNpcConfig() {
        if (npcConfig == null)
            reloadNpcConfig();
        return npcConfig;
    }

    /**
     * Перезагрузка NPC конфига
     */
    public void reloadNpcConfig() {
        if (npcFile != null) {
            try {
                npcConfig = YamlConfiguration.loadConfiguration(npcFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload npc.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Получение конфига прав доступа
     */
    public FileConfiguration getPermissionsConfig() {
        if (permissionsConfig == null)
            reloadPermissionsConfig();
        return permissionsConfig;
    }

    /**
     * Перезагрузка конфига прав доступа
     */
    public void reloadPermissionsConfig() {
        if (permissionsFile != null) {
            try {
                permissionsConfig = YamlConfiguration.loadConfiguration(permissionsFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload permissions.yml: " + e.getMessage());
            }
        }
    }
}
