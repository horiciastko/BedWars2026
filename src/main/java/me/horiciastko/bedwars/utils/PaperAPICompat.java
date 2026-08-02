package me.horiciastko.bedwars.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Утилита для совместимости с Paper API
 * Переводит legacy color codes в Adventure Components
 */
public class PaperAPICompat {

    /**
     * Преобразование legacy string в Component
     * Поддерживает §-коды и &-коды
     */
    public static Component legacyToComponent(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Component.empty();
        }

        // Переводим & в §
        String translated = ChatColor.translateAlternateColorCodes('&', legacy);
        
        // Используем встроенный парсер Bukkit/Paper
        return Component.text(translated);
    }

    /**
     * Отправка сообщения с поддержкой legacy кодов
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null) return;
        player.sendMessage(legacyToComponent(message));
    }

    /**
     * Broadcast сообщения
     */
    public static void broadcast(String message) {
        org.bukkit.Bukkit.broadcast(legacyToComponent(message));
    }

    /**
     * Создание красивого компонента с цветом
     */
    public static Component colored(String text, ChatColor color) {
        NamedTextColor ntc = chatColorToNamedTextColor(color);
        return Component.text(text).color(ntc);
    }

    /**
     * Преобразование ChatColor в NamedTextColor
     */
    private static NamedTextColor chatColorToNamedTextColor(ChatColor color) {
        return switch (color) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * Создание bold компонента
     */
    public static Component bold(String text) {
        return Component.text(text).decorate(TextDecoration.BOLD);
    }

    /**
     * Создание italic компонента
     */
    public static Component italic(String text) {
        return Component.text(text).decorate(TextDecoration.ITALIC);
    }

    /**
     * Создание underlined компонента
     */
    public static Component underlined(String text) {
        return Component.text(text).decorate(TextDecoration.UNDERLINED);
    }

    /**
     * Создание strikethrough компонента
     */
    public static Component strikethrough(String text) {
        return Component.text(text).decorate(TextDecoration.STRIKETHROUGH);
    }
}
