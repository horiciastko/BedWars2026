package me.horiciastko.bedwars.utils;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker implements Listener {

    private static final int SPIGOT_RESOURCE_ID = 132654;
    private static final String SPIGOT_DOWNLOAD_URL = "https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID + "/";

    private final BedWars plugin;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(BedWars plugin) {
        this.plugin = plugin;
    }

    public void check() {
        if (SPIGOT_RESOURCE_ID <= 0) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "BedWars-UpdateChecker");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    final int code = responseCode;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getLogger().warning("Update check failed: Spigot API returned HTTP " + code));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line = reader.readLine();
                    if (line == null || line.trim().isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.getLogger().warning("Update check failed: empty response from Spigot API."));
                        return;
                    }
                    latestVersion = line.trim();
                }

                String currentVersion = plugin.getDescription().getVersion().trim();

                if (isNewer(latestVersion, currentVersion)) {
                    updateAvailable = true;
                    final String current = currentVersion;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().warning("=================================================");
                        plugin.getLogger().warning(" An update for BedWars is available!");
                        plugin.getLogger().warning(" Current:  " + current);
                        plugin.getLogger().warning(" Latest:   " + latestVersion);
                        plugin.getLogger().warning(" Download: " + SPIGOT_DOWNLOAD_URL);
                        plugin.getLogger().warning("=================================================");

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("bedwars.admin")) {
                                notifyPlayer(p);
                            }
                        }
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getLogger().info("BedWars is up to date. (" + currentVersion + ")"));
                }
            } catch (java.net.SocketTimeoutException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLogger().warning("Update check failed: connection to Spigot API timed out."));
            } catch (java.net.UnknownHostException e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLogger().warning("Update check failed: no internet connection or DNS failure."));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLogger().warning("Update check failed: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private boolean isNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        if (remote.equalsIgnoreCase(current)) return false;

        int[] r = parseVersion(remote);
        int[] c = parseVersion(current);

        int len = Math.max(r.length, c.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (rv > cv) return true;
            if (rv < cv) return false;
        }
        return false;
    }

    private int[] parseVersion(String version) {
        String cleaned = version.replaceAll("[^0-9.]", " ").trim();
        for (String token : cleaned.split("\\s+")) {
            if (token.matches("[0-9]+(\\.[0-9]+)*")) {
                String[] parts = token.split("\\.");
                int[] result = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        result[i] = Integer.parseInt(parts[i]);
                    } catch (NumberFormatException ignored) {
                        result[i] = 0;
                    }
                }
                return result;
            }
        }
        return new int[]{0};
    }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;
        Player p = event.getPlayer();
        if (!p.hasPermission("bedwars.admin")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> notifyPlayer(p), 40L);
    }

    private void notifyPlayer(Player player) {
        player.sendMessage("§e§l[BedWars]§r§e A new update is available!");
        player.sendMessage("§7  Current:  §f" + plugin.getDescription().getVersion());
        player.sendMessage("§7  Latest:   §a" + latestVersion);
        player.sendMessage("§7  Download: §b" + SPIGOT_DOWNLOAD_URL);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
