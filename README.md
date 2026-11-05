# Chunk Limiter HUD

Клиентский Fabric-мод для Minecraft 26.2. Считает настроенные типы сущностей и блоков в выбранной области от `1×1` до `32×32` чанков, показывает `текущее / лимит / осталось` и предупреждает о превышении лимитов.

Требования: Fabric Loader `0.19.3+`, Fabric API `0.159.0+26.2` и Minecraft `26.2`.

## Возможности

- любые ванильные или модовые ID сущностей и блоков;
- суммарные группы `group:mobs`, `group:entities`, `group:minecarts`, `group:items` и `group:pistons`;
- собственные группы блоков и сущностей с общим лимитом;
- красная полупрозрачная заливка и контур конкретных блоков перегруженной группы;
- красный ванильный outline сущностей при превышении их лимита;
- тёмно-серый карточный HUD с иконками блоков и яйцами призыва мобов;
- отдельные лимиты для каждого ID;
- зелёный цвет до 75%, жёлтый от 75%, красный при достижении лимита;
- автоматическое сохранение настроек;
- работает на обычном сервере без серверной установки;
- тёмно-серое меню для добавления блоков, сущностей и своих групп;
- визуальный каталог блоков и сущностей с иконками и поиском по названию или ID;
- настраиваемая область подсчёта от `1×1` до `32×32` чанков;
- перетаскивание HUD мышью и масштаб от 50% до 200%;
- подсветка перегруженных блоков сквозь стены;
- `L` открывает меню настроек, `O` включает и выключает HUD;
- при установленном [Mod Menu](https://modrinth.com/mod/modmenu) настройки также открываются через `Моды → Chunk Limiter HUD → Настроить`.

> Мод учитывает только загруженные клиентом чанки и сущности в пределах server tracking distance. HUD показывает число фактически загруженных чанков рядом с общим размером выбранной области.

## Команды

```text
/chunkhud entity minecraft:villager 30
/chunkhud entity group:minecarts 8
/chunkhud entity group:mobs 70
/chunkhud block minecraft:hopper 20
/chunkhud block group:pistons 64
/chunkhud remove entity minecraft:villager
/chunkhud remove block minecraft:piston
/chunkhud list
/chunkhud toggle
/chunkhud menu
/chunkhud reload
```

`/chunkhud toggle` переключает только HUD. Даже если сам мод выключен в меню, доступ к настройкам через `L`, Mod Menu и `/chunkhud menu` сохраняется. Клавиши можно переназначить в обычном меню управления Minecraft.

### Собственные группы

```text
# Создать группу блоков с общим лимитом 40
/chunkhud group create механизмы block 40

# Добавить учитываемые блоки
/chunkhud group add механизмы minecraft:hopper
/chunkhud group add механизмы minecraft:piston
/chunkhud group add механизмы minecraft:sticky_piston
/chunkhud group add механизмы minecraft:observer

# Изменить лимит или состав
/chunkhud group limit механизмы 64
/chunkhud group remove механизмы minecraft:observer

# Посмотреть или удалить группы
/chunkhud group list
/chunkhud group delete механизмы
```

Для группы мобов вместо `block` используется `entity`:

```text
/chunkhud group create животные entity 30
/chunkhud group add животные minecraft:cow
/chunkhud group add животные minecraft:sheep
/chunkhud group add животные minecraft:pig
```

При превышении лимита группы принадлежащие ей блоки получают красную полупрозрачную оболочку и яркий контур. Перегруженные сущности получают красный ванильный outline вокруг модели. Это только клиентское предупреждение: мир и сами объекты не изменяются.

Настройки находятся в `.minecraft/config/chunk_limiter_hud.json`. Там также можно изменить положение HUD, частоту обновления, скрытие нулевых строк, отдельное включение подсветки блоков и сущностей, а также максимальное число одновременно подсвечиваемых объектов.

## Сборка

Нужна Java 25:

```powershell
.\gradlew.bat build
```

Готовый JAR создаётся в `build/libs/`.
