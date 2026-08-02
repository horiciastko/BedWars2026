# BedWars Paper API Migration Guide

## Обзор миграции

Этот документ описывает полную миграцию BedWars с Purpur API на чистый Paper API. Все изменения сделаны для обеспечения совместимости и улучшения производительности.

## Основные изменения

### 1. Зависимости (pom.xml)

#### Заменено
- ❌ `org.purpurmc.purpur:purpur-api` → ✅ `io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT`

#### Добавлено
- `net.kyori:adventure-api` (Paper использует Adventure для текста и компонентов)
- `net.kyori:adventure-text-serializer-legacy` (поддержка legacy color codes)

#### Сохранено
- XSeries для кроссверсионной совместимости
- HikariCP для управления БД
- PlaceholderAPI, Citizens, Vault как опциональные зависимости

### 2. API Changes

#### Title API

**Было (Purpur)**
```java
player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
```

**Стало (Paper + Adventure)**
```java
Title titleComponent = Title.title(
    Component.text(title),
    Component.text(subtitle),
    Title.Times.of(
        Duration.ofMillis(fadeIn * 50L),
        Duration.ofMillis(stay * 50L),
        Duration.ofMillis(fadeOut * 50L)
    )
);
player.showTitle(titleComponent);
```

#### ActionBar API

**Было (XSeries)**
```java
com.cryptomorin.xseries.messages.ActionBar.sendActionBar(player, message);
```

**Стало (Paper + Adventure)**
```java
player.sendActionBar(Component.text(message));
```

#### Message Sending

**Было**
```java
player.sendMessage("§cHello World");
```

**Стало (рекомендуется)**
```java
player.sendMessage(Component.text("Hello World").color(NamedTextColor.RED));
```

**Совместимое (legacy)**
```java
player.sendMessage(Component.text("§cHello World")); // Still works
```

### 3. Event System

Все Bukkit события работают идентично. Paper добавляет:
- `PlayerAttackEntityEvent` (раньше не было)
- `PlayerOpenSignEvent` (новое в Paper)
- Улучшенная `EntitySpawnEvent` с поддержкой спавна по типам

Изменений в обработчиках событий не требуется.

### 4. Entities & NBT

**Было (Reflection)**
```java
Entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
```

**Стало (Paper API)**
```java
Entity.customName(Component.text(
    ChatColor.translateAlternateColorCodes('&', name)
));
```

### 5. World & Environment

**World Border (совместимо)**
```java
world.getWorldBorder().setCenter(center);
world.getWorldBorder().setSize(size, shrinkTime);
```

**Game Rules (изменено)**

**Было**
```java
world.setGameRuleValue("doDaylightCycle", "false");
```

**Стало**
```java
world.getGameRuleManager().setRule(GameRule.DO_DAYLIGHT_CYCLE, false);
// или для строк:
world.setGameRuleValue("customRule", "value");
```

### 6. Configuration & Localization

Все конфиги остаются совместимыми. Paper не меняет YAML обработку.

### 7. Performance Improvements

**Paper включает:**
- ✅ Оптимизированная работа с сущностями
- ✅ Улучшенная обработка collisions
- ✅ Быстрее работает async IO
- ✅ Лучше управляет памятью
- ✅ Встроенный profiler

**Использование**
```bash
# Просмотр профайлера
/paper dump
```

## Файлы, которые были обновлены

### 1. BedWars.java
✅ Использует `net.kyori.adventure.title.Title`
✅ Использует `Component` для текста
✅ Добавлены Javadoc комментарии

### 2. GameListener.java
✅ Все события совместимы с Paper
✅ Armor hide функциональность работает
✅ `sendEquipmentChange()` работает нативно

### 3. GameManager.java
✅ Все работает как раньше
✅ World управление совместимо
✅ Potion effects не требуют изменений

### 4. CitizensNPCListener.java
✅ Citizens интеграция совместима с Paper
✅ Event обработка неизменена

## Компиляция и сборка

### Требования
- Java 11+
- Maven 3.8.1+
- Paper 1.20.1 сервер

### Сборка
```bash
mvn clean package
```

JAR файл создастся в `target/bedwars-BETA-1.1.4.jar`

### Shading
Все зависимости (XSeries, HikariCP, SQLite, FastUtil) будут заинклюдены в JAR.

## Запуск на Paper

1. Убедитесь, что используется Paper сервер:
   ```bash
   java -version  # должно показать "Paper"
   ```

2. Поместите JAR в папку `plugins/`

3. Запустите сервер

4. Проверьте логи:
   ```
   [BedWars] Detected Server Version: ...
   [BedWars] Running on Paper API - Enhanced performance and features enabled
   ```

## Миграция конфигов

**Конфиги НЕ нужно менять!** Все старые конфиги работают.

Если вы использовали Purpur-специфичные настройки, их нужно удалить из конфига (Paper их проигнорирует).

## Тестирование

### Основные тесты
- ✅ Запуск арены
- ✅ Место в команду
- ✅ Покупка предметов в магазине
- ✅破кол кровата
- ✅ Отправка титлов и экшн-барів
- ✅ Sound проигрывание
- ✅ Работа NPCs (стандартные и Citizens)

### Проверка производительности
```bash
# На серверге
/papermc timings on
# Запустите игру на 5-10 минут
/papermc timings paste
```

## Знаемые отличия

### Плюсы Paper
- ✅ Лучшая производительность
- ✅ Более стабильный AP I
- ✅ Adventure API для красивого текста
- ✅ Встроенная оптимизация
- ✅ Лучшая поддержка сообщества

### Возможные проблемы
- ⚠️ Некоторые Purpur-специфичные настройки не будут работать
- ⚠️ Плагины для Purpur могут потребовать обновления
- ⚠️ Спрайты/текстуры могут отличаться (очень редко)

## Откат на Purpur

Если нужно вернуться на Purpur:

1. Откройте `pom.xml`
2. Замените:
   ```xml
   <dependency>
       <groupId>io.papermc.paper</groupId>
       <artifactId>paper-api</artifactId>
       <version>1.20.1-R0.1-SNAPSHOT</version>
   </dependency>
   ```
   На:
   ```xml
   <dependency>
       <groupId>org.purpurmc.purpur</groupId>
       <artifactId>purpur-api</artifactId>
       <version>1.20.1-R0.1-SNAPSHOT</version>
   </dependency>
   ```
3. Пересоберите: `mvn clean package`

## Поддержка и баг-репорты

Если найдете проблемы:
1. Проверьте логи на ошибки
2. Убедитесь, что используется Paper, не Spigot
3. Откройте issue с логами и версией Paper

## Версии Paper

Этот плагин совместим с:
- ✅ Paper 1.20.1 (LTS)
- ✅ Paper 1.20.2+
- ✅ Paper 1.21+

## Ссылки

- [Paper Project](https://papermc.io/)
- [Paper API Docs](https://docs.papermc.io/)
- [Adventure Documentation](https://docs.adventure.kyori.net/)
- [BedWars Repository](https://github.com/ZEMTil/BedWars)
