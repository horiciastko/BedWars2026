package me.horiciastko.bedwars.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(com.cryptomorin.xseries.XMaterial material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material != null ? material : Material.STONE, amount);
    }

    public ItemBuilder(com.cryptomorin.xseries.XMaterial material, int amount) {
        ItemStack parsed = material != null ? material.parseItem() : null;
        if (parsed == null) {
            this.item = new ItemStack(Material.STONE, amount);
        } else {
            this.item = parsed;
            this.item.setAmount(amount);
        }
    }

    public ItemBuilder setName(String name) {
        if (item == null || name == null)
            return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(line != null ? ChatColor.translateAlternateColorCodes('&', line) : null);
            }
            meta.setLore(colored);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (lore == null)
                lore = new ArrayList<>();
            lore.add(line != null ? ChatColor.translateAlternateColorCodes('&', line) : null);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder hideAttributes() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
