# Paper API Changes - Быстрая справка

## Component API (Текст)

### Создание компонентов
```java
// Простой текст
Component text = Component.text("Hello");

// С цветом
Component colored = Component.text("Red Text")
    .color(NamedTextColor.RED);

// С жирностью
Component bold = Component.text("Bold")
    .decorate(TextDecoration.BOLD);

// Комбинировано
Component fancy = Component.text("Fancy")
    .color(NamedTextColor.BLUE)
    .decorate(TextDecoration.ITALIC)
    .decorate(TextDecoration.UNDERLINED);

// Мультистрока
Component multi = Component.text()
    .append(Component.text("Line 1\n"))
    .append(Component.text("Line 2"))
    .build();
```

### Отправка
```java
player.sendMessage(component);
Broadcast.broadcast(component);
```

## Title API

```java
// Полный title
Title title = Title.title(
    Component.text("Welcome"),
    Component.text("To BedWars"),
    Title.Times.of(
        Duration.ofMillis(500),  // fade in
        Duration.ofSeconds(2),   // stay
        Duration.ofMillis(500)   // fade out
    )
);
player.showTitle(title);

// Только subtitle
Subtitle sub = Subtitle.subtitle(Component.text("Subtitle"));
player.showSubtitle(sub);

// Очистить
player.clearTitle();
```

## ActionBar API

```java
player.sendActionBar(Component.text("Action Bar Text"));
```

## Sound API

```java
// Встроенный звук
player.playSound(Sound.sound(
    Key.key("minecraft", "entity.player.levelup"),
    Sound.Source.PLAYER,
    1.0f, // volume
    1.0f  // pitch
));

// Позиция
player.playSound(
    Sound.sound(
        Key.key("minecraft", "entity.player.levelup"),
        Sound.Source.PLAYER,
        1.0f,
        1.0f
    ),
    player.getX(),
    player.getY(),
    player.getZ()
);
```

## Entity API

```java
// Custom name
entity.customName(Component.text("Boss HP: 100/100").color(NamedTextColor.RED));
entity.customNameVisible(true);

// Metadata
entity.getPersistentDataContainer().set(
    new NamespacedKey(plugin, "key"),
    PersistentDataType.STRING,
    "value"
);
```

## World Management

```java
// Game rules (новый способ)
world.getGameRuleManager()
    .setRule(GameRule.DO_DAYLIGHT_CYCLE, false);

// Старый способ еще работает
world.setGameRuleValue("doDaylightCycle", "false");

// World Border
WorldBorder border = world.getWorldBorder();
border.setCenter(center);
border.setSize(size, duration);
border.setDamageBuffer(buffer);
border.setDamageAmount(damage);
```

## Scheduler

```java
// Обычная задача
BukkitScheduler scheduler = Bukkit.getScheduler();
scheduler.runTask(plugin, () -> {
    // Синхронно
});

// Асинхронно
scheduler.runTaskAsynchronously(plugin, () -> {
    // В отдельном потоке
});

// С задержкой
scheduler.runTaskLater(plugin, () -> {
    // Через 20 тиков (1 сек)
}, 20L);

// Повторяющаяся
scheduler.runTaskTimer(plugin, () -> {
    // Каждые 20 тиков
}, 0L, 20L);
```

## Damage API

```java
// Новый способ
player.damage(amount, damageSource);

// Старый способ еще работает
player.damage(amount);
```

## Potion Effects

```java
// Добавить эффект
player.addPotionEffect(new PotionEffect(
    PotionEffectType.SPEED,
    duration,  // в тиках
    amplifier, // 0 = уровень 1
    ambient,   // particle clouds
    showIcon   // показывать иконку в инвентаре
));

// Удалить
player.removePotionEffect(PotionEffectType.SPEED);

// Проверить
if (player.hasPotionEffect(PotionEffectType.SPEED)) {
    // ...
}
```

## Event API

```java
// Обработчик события
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onPlayerInteract(PlayerInteractEvent event) {
    // Никаких изменений
}

// Регистрация
getServer().getPluginManager().registerEvents(listener, plugin);
```

## Inventory API

```java
// Создать инвентарь
Inventory inv = Bukkit.createInventory(
    holder,
    InventoryType.CHEST,  // или размер (9, 18, 27, 36, 45, 54)
    Component.text("Title")  // Component, не String!
);

// Открыть
player.openInventory(inv);

// Клик (в event handler)
ClickType click = event.getClick();
if (click == ClickType.LEFT) {
    // Left click
}
```

## NBT Data

```java
// Получить контейнер
PersistentDataContainer pdc = entity.getPersistentDataContainer();

// Сохранить
pdc.set(
    new NamespacedKey(plugin, "key"),
    PersistentDataType.STRING,
    "value"
);

// Получить
String value = pdc.get(
    new NamespacedKey(plugin, "key"),
    PersistentDataType.STRING
);
```

## Attributes

```java
// Получить атрибут
AttributeInstance attackSpeed = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
if (attackSpeed != null) {
    attackSpeed.setBaseValue(4.0);
}

// Возможные атрибуты:
// - GENERIC_ATTACK_SPEED
// - GENERIC_MAX_HEALTH
// - GENERIC_MOVEMENT_SPEED
// - GENERIC_KNOCKBACK_RESISTANCE
// - GENERIC_LUCK
// - HORSE_JUMP_STRENGTH
// - ZOMBIE_SPAWN_REINFORCEMENTS
```

## BiomeData

```java
// Получить биом
Biome biome = world.getBiome(x, y, z);

// Проверить
if (biome == Biome.FOREST) {
    // ...
}
```

## Misc Changes

```java
// Scoreboard Tags (работает как раньше)
entity.addScoreboardTag("tag");
entity.removeScoreboardTag("tag");
if (entity.getScoreboardTags().contains("tag")) {
    // ...
}

// Enchantments (trabajo как раньше)
item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 5);

// Unbreakable flag
ItemMeta meta = item.getItemMeta();
if (meta != null) {
    meta.setUnbreakable(true);
    item.setItemMeta(meta);
}
```
