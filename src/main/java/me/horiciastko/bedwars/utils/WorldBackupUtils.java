package me.horiciastko.bedwars.utils;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utility for creating and restoring per-arena world backups.
 * Backup location is driven by config.yml:
 *   world-backup.enabled  — when false all operations become no-ops
 *   world-backup.folder   — subdirectory (relative to server root) that holds all backups
 *
 * All file-I/O methods are thread-safe and contain no Bukkit API calls,
 * so they can be called from async scheduler tasks.
 */
public final class WorldBackupUtils {

    private static final String BACKUP_SUFFIX = "_bw_backup";

    private WorldBackupUtils() {}

    /** Returns true when backups are enabled in config. */
    public static boolean isEnabled() {
        return me.horiciastko.bedwars.BedWars.getInstance()
                .getConfig().getBoolean("world-backup.enabled", true);
    }

    /** Returns the backup root directory (server-root/bw_backups by default). */
    private static File getBackupRoot() {
        String folder = me.horiciastko.bedwars.BedWars.getInstance()
                .getConfig().getString("world-backup.folder", "bw_backups");
        File root = new File(folder);
        // If not absolute, resolve relative to server root (same dir as world containers)
        if (!root.isAbsolute()) {
            root = new File(Bukkit.getWorldContainer().getParentFile(), folder);
        }
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public static String getBackupFolderName(String worldName) {
        return worldName + BACKUP_SUFFIX;
    }

    public static File getBackupFolder(String worldName) {
        return new File(getBackupRoot(), getBackupFolderName(worldName));
    }

    /** Returns true if a valid backup exists (folder + level.dat present). */
    public static boolean hasBackup(String worldName) {
        if (!isEnabled()) return false;
        File backup = getBackupFolder(worldName);
        return backup.exists() && backup.isDirectory() && new File(backup, "level.dat").exists();
    }

    /**
     * Creates (or overwrites) the backup from the live world folder.
     * Must be called after world.save() and with no Bukkit world loaded,
     * OR from an async thread while auto-save is disabled.
     *
     * @return true on success, false if disabled or error
     */
    public static boolean createBackup(String worldName) {
        if (!isEnabled()) return false;
        File source = new File(Bukkit.getWorldContainer(), worldName);
        if (!source.exists() || !source.isDirectory()) {
            return false;
        }
        File backup = getBackupFolder(worldName);
        if (backup.exists()) {
            deleteFolder(backup);
        }
        try {
            copyFolder(source, backup);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Restores the world folder from backup. The world must be unloaded first.
     *
     * @return true on success, false if disabled or no backup
     */
    public static boolean restoreBackup(String worldName) {
        if (!isEnabled()) return false;
        if (!hasBackup(worldName)) {
            return false;
        }
        File backup = getBackupFolder(worldName);
        File target = new File(Bukkit.getWorldContainer(), worldName);
        if (target.exists()) {
            deleteFolder(target);
        }
        try {
            copyFolder(backup, target);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyFolder(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.mkdirs() && !dest.isDirectory()) {
                throw new IOException("Cannot create directory: " + dest);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    // Skip session.lock — Minecraft holds a lock on this file while the world is loaded
                    if (child.getName().equals("session.lock")) {
                        continue;
                    }
                    copyFolder(child, new File(dest, child.getName()));
                }
            }
        } else {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        if (folder.isDirectory()) {
            File[] children = folder.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolder(child);
                }
            }
        }
        folder.delete();
    }
}
