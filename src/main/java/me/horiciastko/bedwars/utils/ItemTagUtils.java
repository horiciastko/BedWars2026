package me.horiciastko.bedwars.utils;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemTagUtils {

    private static final boolean HAS_PDC;

    static {
        boolean hasPdc;
        try {
            Class.forName("org.bukkit.persistence.PersistentDataContainer");
            hasPdc = true;
        } catch (ClassNotFoundException e) {
            hasPdc = false;
        }
        HAS_PDC = hasPdc;
    }

    public static void setTag(ItemStack item, String key, String value) {
        if (item == null)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        if (HAS_PDC) {
            PDCHandler.setTag(meta, key, value);
        } else {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (lore == null)
                lore = new ArrayList<>();
            lore.removeIf(line -> line.startsWith("§d§e§a§d§b§e§e§f§" + key + ":"));
            lore.add(0, "§d§e§a§d§b§e§e§f§" + key + ":" + value);
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    public static String getTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;

        if (HAS_PDC) {
            return PDCHandler.getTag(meta, key);
        } else {
            if (!meta.hasLore())
                return null;
            List<String> lore = meta.getLore();
            if (lore == null)
                return null;
            for (String line : lore) {
                if (line.startsWith("§d§e§a§d§b§e§e§f§" + key + ":")) {
                    return line.substring(("§d§e§a§d§b§e§e§f§" + key + ":").length());
                }
            }
        }
        return null;
    }

    public static void removeTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta())
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        if (HAS_PDC) {
            PDCHandler.removeTag(meta, key);
        } else {
            if (!meta.hasLore())
                return;
            List<String> lore = meta.getLore();
            if (lore == null)
                return;
            lore.removeIf(line -> line.startsWith("§d§e§a§d§b§e§e§f§" + key + ":"));
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
    }

    private static class PDCHandler {
        public static void setTag(org.bukkit.inventory.meta.ItemMeta meta, String key, String value) {
            org.bukkit.NamespacedKey nsk = new org.bukkit.NamespacedKey(BedWars.getInstance(), key);
            meta.getPersistentDataContainer().set(nsk, org.bukkit.persistence.PersistentDataType.STRING, value);
        }

        public static String getTag(org.bukkit.inventory.meta.ItemMeta meta, String key) {
            org.bukkit.NamespacedKey nsk = new org.bukkit.NamespacedKey(BedWars.getInstance(), key);
            return meta.getPersistentDataContainer().get(nsk, org.bukkit.persistence.PersistentDataType.STRING);
        }

        public static void removeTag(org.bukkit.inventory.meta.ItemMeta meta, String key) {
            org.bukkit.NamespacedKey nsk = new org.bukkit.NamespacedKey(BedWars.getInstance(), key);
            meta.getPersistentDataContainer().remove(nsk);
        }
    }
}
