package me.horiciastko.bedwars.utils;

import org.bukkit.Bukkit;

public enum ServerVersion {
    V1_8(8),
    V1_9(9),
    V1_10(10),
    V1_11(11),
    V1_12(12),
    V1_13(13),
    V1_14(14),
    V1_15(15),
    V1_16(16),
    V1_17(17),
    V1_18(18),
    V1_19(19),
    V1_20(20),
    V1_21(21),
    V1_22(22),
    V1_23(23),
    V1_24(24),
    V1_25(25),
    V26_1(26),
    UNKNOWN(0);

    private final int minor;
    private static ServerVersion current;

    ServerVersion(int minor) {
        this.minor = minor;
    }

    public int getMinor() {
        return minor;
    }

    public static ServerVersion getCurrent() {
        if (current == null) {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            if (parts.length >= 1) {
                try {
                    // Supports both legacy "1.x.y" and new "x.y" version formats.
                    int minor;
                    if (parts.length >= 2 && "1".equals(parts[0])) {
                        minor = Integer.parseInt(parts[1]);
                    } else {
                        minor = Integer.parseInt(parts[0]);
                    }
                    for (ServerVersion sv : values()) {
                        if (sv.minor == minor) {
                            current = sv;
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (current == null)
                current = UNKNOWN;
        }
        return current;
    }

    public static boolean isAtLeast(ServerVersion version) {
        return getCurrent().minor >= version.minor && getCurrent() != UNKNOWN;
    }
}
