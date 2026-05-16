package me.horiciastko.bedwars.logic;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.utils.ItemTagUtils;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class LanguageManager {
    private final BedWars plugin;
    private final Map<String, FileConfiguration> messages = new HashMap<>();
    private final Map<String, Map<String, FileConfiguration>> configs = new HashMap<>();
    private String defaultLang = "en";
    private final Map<UUID, String> playerLangs = new HashMap<>();

    public LanguageManager(BedWars plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File langDir = new File(plugin.getDataFolder(), "Languages");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        String[] bundledLanguages = { "en", "pl" };
        for (String iso : bundledLanguages) {
            File dir = new File(langDir, iso);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            extractLanguageFiles(iso, dir);
        }

        backfillAllYamlFromBundledResources();

        File[] dirs = langDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String iso = dir.getName();
                loadLanguageFolder(iso, dir);
            }
        }

        defaultLang = plugin.getConfig().getString("default-language", "en");
        if (!messages.containsKey(defaultLang)) {
            if (!messages.containsKey("en")) {
                loadLanguageFolder("en", new File(langDir, "en"));
            }
            if (!messages.containsKey("en")) {
                plugin.getLogger().severe("CRITICAL: Default language 'en' could not be loaded!");
            }
            defaultLang = messages.containsKey(defaultLang) ? defaultLang : "en";
        }
    }

    private void extractLanguageFiles(String iso, File dir) {
        String[] filesToExtract = { "messages.yml", "shop.yml", "upgrades.yml", "generators.yml", "teams.yml",
                "sounds.yml", "npc.yml" };
        for (String fileName : filesToExtract) {
            File file = new File(dir, fileName);
            if (!file.exists()) {
                String resourcePath = "Languages/" + iso + "/" + fileName;
                try {
                    plugin.saveResource(resourcePath, false);
                } catch (Exception e) {
                }
            }
        }
    }

    private void loadLanguageFolder(String iso, File dir) {
        Map<String, FileConfiguration> langConfigs = new HashMap<>();

        String[] filesToLoad = { "messages.yml", "shop.yml", "upgrades.yml", "generators.yml", "teams.yml",
                "sounds.yml", "npc.yml" };
        for (String fileName : filesToLoad) {
            File file = new File(dir, fileName);

            if (file.exists()) {
                mergeMissingDefaults(iso, fileName, file);
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                langConfigs.put(fileName.replace(".yml", ""), config);
                if (fileName.equals("messages.yml")) {
                    messages.put(iso, config);
                }
            }
        }

        if (!langConfigs.isEmpty()) {
            configs.put(iso, langConfigs);
            plugin.getLogger().info("Loaded language data for: " + iso);
        }
    }

    private void mergeMissingDefaults(String iso, String fileName, File file) {
        String primaryResourcePath = "Languages/" + iso + "/" + fileName;
        YamlConfiguration defaults = loadBundledYaml(primaryResourcePath);

        // For custom languages, fallback to EN bundled defaults.
        if (defaults == null && !"en".equalsIgnoreCase(iso)) {
            defaults = loadBundledYaml("Languages/en/" + fileName);
        }

        if (defaults == null) {
            return;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            if (!current.contains(path)) {
                current.set(path, defaults.get(path));
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        try {
            current.save(file);
            plugin.getLogger().info("Filled missing language keys in " + iso + "/" + fileName + " without overriding existing values.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save merged language defaults for " + iso + "/" + fileName + ": "
                    + e.getMessage());
        }
    }

    private YamlConfiguration loadBundledYaml(String resourcePath) {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void backfillAllYamlFromBundledResources() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            return;
        }

        List<File> yamlFiles = new ArrayList<>();
        collectYamlFiles(dataFolder, yamlFiles);
        int updated = 0;

        for (File file : yamlFiles) {
            String relativePath = dataFolder.toPath().relativize(file.toPath()).toString()
                    .replace(File.separatorChar, '/');

            YamlConfiguration defaults = loadBundledYaml(relativePath);
            if (defaults == null) {
                continue;
            }

            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            boolean changed = false;

            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (!current.contains(path)) {
                    current.set(path, defaults.get(path));
                    changed = true;
                }
            }

            if (!changed) {
                continue;
            }

            try {
                current.save(file);
                updated++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save merged defaults for " + relativePath + ": "
                        + e.getMessage());
            }
        }

        if (updated > 0) {
            plugin.getLogger().info("Filled missing keys in " + updated
                    + " YAML file(s) from bundled defaults without overriding existing values.");
        }
    }

    private void collectYamlFiles(File directory, List<File> out) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectYamlFiles(file, out);
            } else if (file.getName().toLowerCase().endsWith(".yml")) {
                out.add(file);
            }
        }
    }

    public FileConfiguration getConfig(UUID uuid, String type) {
        String lang = playerLangs.getOrDefault(uuid, defaultLang);
        return getConfig(lang, type);
    }

    public FileConfiguration getConfig(String lang, String type) {
        Map<String, FileConfiguration> langConfigs = configs.get(lang);
        if (langConfigs == null || !langConfigs.containsKey(type)) {
            langConfigs = configs.get(defaultLang);
        }
        return (langConfigs != null) ? langConfigs.get(type) : null;
    }

    public String getMessage(UUID uuid, String path) {
        String lang = playerLangs.getOrDefault(uuid, defaultLang);
        FileConfiguration config = messages.get(lang);

        if (config != null && config.contains(path)) {
            String msg = config.getString(path);
            return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : "§cNull: " + path);
        }

        if (!lang.equals(defaultLang)) {
            FileConfiguration defConfig = messages.get(defaultLang);
            if (defConfig != null && defConfig.contains(path)) {
                String msg = defConfig.getString(path);
                return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : "§cNull: " + path);
            }
        }

        return "§cMissing path: " + path;
    }

    public List<String> getMessageList(UUID uuid, String path) {
        String lang = playerLangs.getOrDefault(uuid, defaultLang);
        FileConfiguration config = messages.get(lang);
        if (config == null || !config.contains(path)) {
            config = messages.get(defaultLang);
        }
        if (config == null)
            return java.util.Arrays.asList("§cMissing list: " + path);

        List<String> list = config.getStringList(path);
        return list.stream()
                .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                .collect(Collectors.toList());
    }

    public void setPlayerLanguage(UUID uuid, String lang) {
        if (configs.containsKey(lang)) {
            playerLangs.put(uuid, lang);
            plugin.getDatabaseManager().setSetting("lang_" + uuid.toString(), lang);
        }
    }

    public void loadPlayerLanguage(UUID uuid) {
        String lang = plugin.getDatabaseManager().getSetting("lang_" + uuid.toString());
        if (lang != null && configs.containsKey(lang)) {
            playerLangs.put(uuid, lang);
        }
    }

    public java.util.Set<String> getAvailableLanguages() {
        return configs.keySet();
    }

    public boolean isLanguageAvailable(String lang) {
        return configs.containsKey(lang);
    }

    public String getItemName(UUID uuid, org.bukkit.Material material) {
        if (material == null)
            return "§cUnknown";
        String materialName = material.name();
        String path = "items." + materialName;
        String lang = playerLangs.getOrDefault(uuid, defaultLang);
        FileConfiguration config = messages.get(lang);

        if (config != null && config.contains(path)) {
            String msg = config.getString(path);
            return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : cleanMaterialName(materialName));
        }

        if (!lang.equals(defaultLang)) {
            FileConfiguration defConfig = messages.get(defaultLang);
            if (defConfig != null && defConfig.contains(path)) {
                String msg = defConfig.getString(path);
                return ChatColor.translateAlternateColorCodes('&', msg != null ? msg : cleanMaterialName(materialName));
            }
        }

        return cleanMaterialName(materialName);
    }

    private String cleanMaterialName(String name) {
        if (name == null)
            return "Unknown";
        String[] split = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            if (s.isEmpty())
                continue;
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public void localizeItem(java.util.UUID uuid, org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR)
            return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        if (item.getType().getMaxStackSize() > 1) {
            ItemTagUtils.removeTag(item, "inventory_name");
            meta = item.getItemMeta();
            if (meta == null)
                return;
        }

        String taggedInventoryName = ItemTagUtils.getTag(item, "inventory_name");
        String resolvedName = null;

        if (taggedInventoryName != null && !taggedInventoryName.isEmpty()) {
            resolvedName = ChatColor.translateAlternateColorCodes('&', taggedInventoryName);
        } else if (!meta.hasDisplayName() || hasLegacyQuantitySuffix(meta.getDisplayName())) {
            resolvedName = getItemName(uuid, item.getType());
        }

        if (resolvedName != null && !resolvedName.equals(meta.getDisplayName())) {
            meta.setDisplayName(resolvedName);
            item.setItemMeta(meta);
        }
    }

    private boolean hasLegacyQuantitySuffix(String displayName) {
        if (displayName == null || displayName.isEmpty())
            return false;
        String stripped = ChatColor.stripColor(displayName);
        return stripped != null && stripped.matches(".*\\(x\\d+\\)$");
    }

    public void reloadDefaultLanguage() {
        String newDefaultLang = plugin.getConfig().getString("default-language", "en");
        if (configs.containsKey(newDefaultLang)) {
            defaultLang = newDefaultLang;
            if (messages.containsKey(newDefaultLang)) {
                plugin.getLogger().info("Default language changed to: " + newDefaultLang);
            }
        }
    }
}
