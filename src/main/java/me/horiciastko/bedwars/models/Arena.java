package me.horiciastko.bedwars.models;

import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Data
@lombok.EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Arena {

    @lombok.EqualsAndHashCode.Include
    private final String name;
    private String worldName;

    public Arena(String name) {
        this.name = name;
    }

    private Location lobbyLocation;
    private Location lobbyPos1;
    private Location lobbyPos2;
    private Location pos1;
    private Location pos2;
    private final List<Player> players = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Location> diamondGenerators = new ArrayList<>();
    private final List<Location> emeraldGenerators = new ArrayList<>();
    private boolean autoSetup = false;
    private int minPlayers = 2;
    private int maxPlayers = 8;
    private ArenaMode mode = ArenaMode.SOLO;
    private PvpMode pvpMode = PvpMode.LEGACY_1_8;
    private String group = "Default";
    private boolean enabled = true;
    private boolean resetting = false;

    public enum PvpMode {
        LEGACY_1_8("1.8"), MODERN_1_16("1.16");

        private final String displayName;

        PvpMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public PvpMode getPvpMode() {
        return pvpMode;
    }

    public void setPvpMode(PvpMode pvpMode) {
        this.pvpMode = pvpMode;
    }

    public boolean isAutoSetup() {
        return autoSetup;
    }

    public void setAutoSetup(boolean autoSetup) {
        this.autoSetup = autoSetup;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public ArenaMode getMode() {
        return mode;
    }

    public void setMode(ArenaMode mode) {
        this.mode = mode;
    }

    private GameState state = GameState.WAITING;

    private int ticks = 0;
    private int gameTime = 0;
    private int eventIndex = 0;
    private int eventTimer = 0;
    private int diamondTier = 1;
    private int emeraldTier = 1;
    private int diamondCooldown = 0;
    private int emeraldCooldown = 0;
    private boolean soloTest = false;
    private final java.util.Set<Location> placedBlocks = new java.util.HashSet<>();
    private final java.util.List<org.bukkit.block.BlockState> initialBedStates = new java.util.ArrayList<>();

    private final List<Location> joinSigns = new ArrayList<>();

    public enum GameState {
        WAITING, STARTING, IN_GAME, ENDING
    }

    public java.util.List<org.bukkit.block.BlockState> getInitialBedStates() {
        return initialBedStates;
    }

    public void reset() {
        state = GameState.WAITING;
        ticks = 0;
        gameTime = 0;
        eventIndex = 0;
        eventTimer = 0;
        diamondTier = 1;
        emeraldTier = 1;
        diamondCooldown = 0;
        emeraldCooldown = 0;
        soloTest = false;
        resetting = false;
        placedBlocks.clear();
        initialBedStates.clear();
        players.clear();

        for (Team team : teams) {
            team.reset();
        }
    }

    public enum ArenaMode {
        SOLO("Solo", 1), DUO("Duo", 2), TRIO("Trio", 3), SQUAD("Squad", 4);

        private final String displayName;
        private final int playersPerTeam;

        ArenaMode(String displayName, int playersPerTeam) {
            this.displayName = displayName;
            this.playersPerTeam = playersPerTeam;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPlayersPerTeam() {
            return playersPerTeam;
        }
    }
}
