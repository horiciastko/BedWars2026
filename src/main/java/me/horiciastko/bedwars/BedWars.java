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
import me.horiciastko.bedwars.logic.SoundManager;
import me.horiciastko.bedwars.logic.LanguageManager;
import me.horiciastko.bedwars.logic.GameManager;
import me.horiciastko.bedwars.logic.VisualizationManager;
import me.horiciastko.bedwars.logic.LevelsManager;
import me.horiciastko.bedwars.npc.NPCManager;
import me.horiciastko.bedwars.listeners.SignListener;
import me.horiciastko.bedwars.listeners.CleanupListener;
import me.horiciastko.bedwars.listeners.LobbyListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

@Getter
@SuppressWarnings("deprecation")
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
    private LanguageManager languageManager;
    private LevelsManager levelsManager;
    @Getter
    private SupportManager supportManager;
    @Getter
    private NPCManager npcManager;

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null)
            return;
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (NoSuchMethodError e) {
            try {
                player.getClass().getMethod("sendTitle", String.class, String.class)
                        .invoke(player, title, subtitle);
            } catch (Exception ex) {
                if (title != null && !title.isEmpty())
                    player.sendMessage(title);
                if (subtitle != null && !subtitle.isEmpty())
                    player.sendMessage(subtitle);
            }
        }
    }


    public void sendActionBar(Player player, String message) {
        if (player == null || message == null)
            return;
        try {
            com.cryptomorin.xseries.messages.ActionBar.sendActionBar(player, message);
        } catch (Exception e) {
            player.sendMessage(message);
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

        getServer().getPluginManager().registerEvents(new InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new CleanupListener(), this);
        getServer().getPluginManager().registerEvents(new SignListener(), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.LobbyItemListener(), this);
        getServer().getPluginManager().registerEvents(new me.horiciastko.bedwars.listeners.GameListener(), this);
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

        me.horiciastko.bedwars.utils.UpdateChecker updateChecker = new me.horiciastko.bedwars.utils.UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.check();
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
        getLogger().info("[BedWars] Plugin disabled!");
    }
}
