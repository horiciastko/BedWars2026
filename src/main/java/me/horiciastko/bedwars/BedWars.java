package me.horiciastko.bedwars;

import lombok.Getter;
import me.horiciastko.bedwars.commands.BedWarsCommand;
import me.horiciastko.bedwars.listeners.InventoryListener;
import me.horiciastko.bedwars.logic.ArenaManager;
import me.horiciastko.bedwars.logic.ConfigManager;
import me.horiciastko.bedwars.logic.DatabaseManager;
import me.horiciastko.bedwars.logic.ShopManager;
import me.horiciastko.bedwars.logic.SignManager;
import me.horiciastko.bedwars.logic.ScoreboardManager;
import me.horiciastko.bedwars.logic.GeneratorTask;
import me.horiciastko.bedwars.logic.StatsManager;
import me.horiciastko.bedwars.logic.SupportManager;
import me.horiciastko.bedwars.logic.PartyManager;
import me.horiciastko.bedwars.logic.SoundManager;
import me.horiciastko.bedwars.logic.LanguageManager;
import me.horiciastko.bedwars.logic.GameManager;
import me.horiciastko.bedwars.logic.VisualizationManager;
import me.horiciastko.bedwars.logic.LevelsManager;
import me.horiciastko.bedwars.npc.NPCManager;
import me.horiciastko.bedwars.listeners.SignListener;
import me.horiciastko.bedwars.listeners.CleanupListener;
import me.horiciastko.bedwars.listeners.LobbyListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * BedWars Plugin - Paper API Version
 * Migrated from Purpur API to Paper API
 */
@Getter
public class BedWars extends JavaPlugin {

    @Getter
    private static BedWars instance;
    private ArenaManager arenaManager;
    private ShopManager shopManager;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private VisualizationManager visualizationManager;
    private SignManager signManager;
    private ScoreboardManager scoreboardManager;
    private StatsManager statsManager;
    private GameManager gameManager;
    private SoundManager soundManager;
    private PartyManager partyManager;
    private LanguageManager languageManager;
    private LevelsManager levelsManager;
    @Getter
    private SupportManager supportManager;
    @Getter
    private NPCManager npcManager;

    /**
     * Send title to player using Paper API (Adventure)
     */
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        try {
            Title titleComponent = Title.title(
                    title != null ? Component.text(title) : Component.empty(),
                    subtitle != null ? Component.text(subtitle) : Component.empty(),
                    Title.Times.of(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L)
                    )
            );
            player.showTitle(titleComponent);
        } catch (Exception e) {
            // Fallback to message if Paper API fails
            if (title != null && !title.isEmpty())
                player.sendMessage(Component.text(title));
            if (subtitle != null && !subtitle.isEmpty())
                player.sendMessage(Component.text(subtitle));
        }
    }

    /**
     * Send action bar to player using Paper API (Adventure)
     */
    public void sendActionBar(Player player, String message) {
        if (player == null || message == null) return;

        try {
            player.sendActionBar(Component.text(message));
        } catch (Exception e) {
            player.sendMessage(Component.text(message));
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        Logger logger = getLogger();

        saveDefaultConfig();

        me.horiciastko.bedwars.utils.ServerVersion version = me.horiciastko.bedwars.utils.ServerVersion.getCurrent();
        logger.info("Detected Server Version: " + org.bukkit.Bukkit.getBukkitVersion() + " (Mapped: "
                + version.name() + ")");
        logger.info("Running on Paper API - Enhanced performance and features enabled");

        this.configManager = new ConfigManager(this);
        this.soundManager = new SoundManager(this);
        this.soundManager.load();
        this.languageManager = new LanguageManager(this);
        this.languageManager.load();
        this.levelsManager = new LevelsManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.supportManager = new SupportManager(this);
        this.npcManager = new NPCManager(this);
        this.arenaManager = new ArenaManager(this);
        this.shopManager = new ShopManager(this);
        this.visualizationManager = new VisualizationManager(this);
        this.visualizationManager.clearAll();
        this.signManager = new SignManager(this);
        this.statsManager = new StatsManager(this);
        this.partyManager = new PartyManager(this);
        this.gameManager = new GameManager(this);
        this.scoreboardManager = new ScoreboardManager(this);

        this.npcManager.loadStandaloneNPCsFromDatabase();

        new GeneratorTask(this).runTaskTimer(this, 0L, 1L);
        new me.horiciastko.bedwars.logic.CompassTracker(this).runTaskTimer(this, 0L, 10L);

        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            this.gameManager.prepareWorldRules(world);
        }

        BedWarsCommand bwCommand = new BedWarsCommand(this);
        getCommand("bw").setExecutor(bwCommand);
        getCommand("bw").setTabCompleter(bwCommand);

        // Standalone shortcut commands (/party, /join, /leave) — toggled in config
        registerStandaloneShortcuts(bwCommand);

        getServer().getPluginManager().registerEvents(new InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new CleanupListener(), this);
        getServer().getPluginManager().registerEvents(new SignListener(), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.LobbyItemListener(), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.GameListener(), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.ProjectileListener(), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.SelectionWandListener(this),
                this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.NPCListener(this), this);

        if (supportManager.isCitizensEnabled()) {
            try {
                getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.CitizensNPCListener(this), this);
                logger.info("Citizens NPC listener registered successfully!");
            } catch (NoClassDefFoundError e) {
                logger.warning("Citizens detected but listener registration failed: " + e.getMessage());
            }
        }

        logger.info("Plugin enabled successfully! Version: " + getDescription().getVersion());
        logger.info("Paper API Migration: All systems operational");

        me.horiciastko.bedwars.utils.UpdateChecker updateChecker = new me.horiciastko.bedwars.utils.UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.check();
    }

    private void registerStandaloneShortcuts(me.horiciastko.bedwars.commands.BedWarsCommand bwCommand) {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        String[] shortcuts = {"join", "leave", "lobby", "party", "rejoin", "stats"};
        for (String name : shortcuts) {
            boolean enabled = cfg.getBoolean("commands.shortcuts." + name, true);
            org.bukkit.command.PluginCommand cmd = getCommand(name);
            if (cmd == null) continue;
            if (enabled) {
                me.horiciastko.bedwars.commands.SubCommand sub = bwCommand.getSubCommand(name);
                if (sub != null) {
                    me.horiciastko.bedwars.commands.StandaloneCommandExecutor exec =
                            new me.horiciastko.bedwars.commands.StandaloneCommandExecutor(sub);
                    cmd.setExecutor(exec);
                    cmd.setTabCompleter(exec);
                    getLogger().info("Standalone command enabled: /" + name);
                }
            } else {
                unregisterShortcutCommand(cmd, name);
            }
        }

        refreshServerCommands();
    }

    private void unregisterShortcutCommand(org.bukkit.command.PluginCommand cmd, String name) {
        try {
            Object commandMapObject = getCommandMap();
            if (!(commandMapObject instanceof org.bukkit.command.CommandMap)) {
                getLogger().warning("Could not access command map to unregister /" + name);
                return;
            }

            org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) commandMapObject;
            cmd.unregister(commandMap);

            if (commandMap instanceof org.bukkit.command.SimpleCommandMap) {
                java.lang.reflect.Field knownCommandsField = org.bukkit.command.SimpleCommandMap.class
                        .getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                Object knownCommandsObject = knownCommandsField.get(commandMap);
                if (knownCommandsObject instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, org.bukkit.command.Command> knownCommands =
                            (java.util.Map<String, org.bukkit.command.Command>) knownCommandsObject;
                    knownCommands.remove(name.toLowerCase());
                    knownCommands.remove((getDescription().getName().toLowerCase() + ":" + name.toLowerCase()));
                }
            }

            unregisterHelpTopic(name);

            getLogger().info("Standalone command disabled and unregistered: /" + name);
        } catch (Exception ex) {
            getLogger().warning("Failed to unregister /" + name + ": " + ex.getMessage());
        }
    }

    private void unregisterHelpTopic(String name) {
        try {
            org.bukkit.help.HelpMap helpMap = getServer().getHelpMap();
            java.lang.reflect.Field helpTopicsField = helpMap.getClass().getDeclaredField("helpTopics");
            helpTopicsField.setAccessible(true);
            Object helpTopicsObject = helpTopicsField.get(helpMap);
            if (helpTopicsObject instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, org.bukkit.help.HelpTopic> helpTopics =
                        (java.util.Map<String, org.bukkit.help.HelpTopic>) helpTopicsObject;
                helpTopics.remove("/" + name.toLowerCase());
                helpTopics.remove("/" + getDescription().getName().toLowerCase() + ":" + name.toLowerCase());
            }
        } catch (Exception ignored) {
            // HelpMap internals vary by server implementation.
        }
    }

    private Object getCommandMap() throws ReflectiveOperationException {
        java.lang.reflect.Method getCommandMapMethod = getServer().getClass().getMethod("getCommandMap");
        return getCommandMapMethod.invoke(getServer());
    }

    private void refreshServerCommands() {
        try {
            java.lang.reflect.Method syncCommandsMethod = getServer().getClass().getMethod("syncCommands");
            syncCommandsMethod.invoke(getServer());
        } catch (ReflectiveOperationException ignored) {
            // Older server implementations may not expose syncCommands.
        }
    }

    @Override
    public void onDisable() {
        if (visualizationManager != null) {
            visualizationManager.clearAll();
        }
        if (arenaManager != null) {
            arenaManager.saveArenas();
            arenaManager.unloadArenaWorlds();
        }
        if (statsManager != null) {
            statsManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Plugin disabled!");
    }
}