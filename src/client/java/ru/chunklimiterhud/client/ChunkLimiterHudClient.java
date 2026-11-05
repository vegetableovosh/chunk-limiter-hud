package ru.chunklimiterhud.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ChunkLimiterHudClient implements ClientModInitializer {
    static final String MOD_ID = "chunk_limiter_hud";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;

    private HudConfig config;
    private ChunkSnapshot snapshot = ChunkSnapshot.empty();
    private final Set<Integer> highlightedEntityIds = new HashSet<>();
    private int ticksUntilScan;
    private static ChunkLimiterHudClient instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = HudConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(id("controls"));
        KeyMapping openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chunk_limiter_hud.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                category
        ));
        KeyMapping toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chunk_limiter_hud.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.gui.screen() == null) openConfig(null);
            }
            while (toggleHudKey.consumeClick()) {
                config.hudEnabled = !config.hudEnabled;
                config.save();
            }
            if (!config.enabled) {
                snapshot = ChunkSnapshot.empty();
                highlightedEntityIds.clear();
                return;
            }
            if (client.level == null || client.player == null) {
                snapshot = ChunkSnapshot.empty();
                highlightedEntityIds.clear();
                return;
            }
            if (--ticksUntilScan <= 0 || !snapshot.chunk().equals(client.player.chunkPosition())) {
                snapshot = ChunkSnapshot.scan(client, config);
                rebuildHighlightedEntities(client);
                ticksUntilScan = config.updateIntervalTicks;
            }
        });

        HudElementRegistry.addLast(id("limits"), this::renderHud);
        LevelExtractionEvents.END_EXTRACTION.register(_ -> renderOverloadedBlocks());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> registerCommands(dispatcher));
        LOGGER.info("Chunk Limiter HUD initialized");
    }

    private void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!config.enabled || !config.hudEnabled || client.level == null || client.player == null) return;

        HudContent content = hudContent();
        HudSize size = scaledSize(content, config.hudScale);
        int x = Math.min(config.x, Math.max(0, graphics.guiWidth() - size.width()));
        int y = Math.min(config.y, Math.max(0, graphics.guiHeight() - size.height()));
        drawHud(graphics, content, x, y, config.hudScale);
    }

    private HudContent hudContent() {
        Font font = Minecraft.getInstance().font;
        List<HudEntry> entries = buildHudEntries();
        String title = "Лимиты чанка  " + snapshot.chunk().x() + ", " + snapshot.chunk().z();
        int width = Math.max(174, font.width(title) + 16);
        for (HudEntry entry : entries) {
            width = Math.max(width, font.width(entry.title()) + 34);
            width = Math.max(width, font.width(entry.details()) + 34);
        }
        return new HudContent(title, entries, width, 24 + entries.size() * 24);
    }

    private static HudSize scaledSize(HudContent content, float scale) {
        return new HudSize(
                (int) Math.ceil(content.width() * scale),
                (int) Math.ceil(content.height() * scale)
        );
    }

    private static void drawHud(GuiGraphicsExtractor graphics, HudContent content, int x, int y, float scale) {
        Font font = Minecraft.getInstance().font;
        int width = content.width();
        int height = content.height();

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fill(0, 0, width, height, 0xE018191C);
        graphics.outline(0, 0, width, height, 0xFF45484F);
        graphics.fill(1, 1, width - 1, 18, 0xFF27292E);
        graphics.text(font, content.title(), 7, 5, WHITE, true);

        int rowY = 20;
        for (HudEntry entry : content.entries()) {
            graphics.fill(3, rowY, width - 3, rowY + 21, 0xE823252A);
            graphics.fill(3, rowY, 5, rowY + 21, entry.color());
            graphics.item(entry.icon(), 8, rowY + 3);
            graphics.text(font, entry.title(), 28, rowY + 2, WHITE, true);
            graphics.text(font, entry.details(), 28, rowY + 11, entry.color(), true);
            rowY += 24;
        }
        graphics.pose().popMatrix();
    }

    static HudSize currentHudSize(float scale) {
        if (instance == null) return new HudSize(0, 0);
        return scaledSize(instance.hudContent(), scale);
    }

    static void renderHudPreview(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        if (instance == null) return;
        drawHud(graphics, instance.hudContent(), x, y, scale);
    }

    private List<HudEntry> buildHudEntries() {
        List<HudEntry> entries = new ArrayList<>();
        appendEntries(entries, snapshot.entities(), config.entities, true);
        appendEntries(entries, snapshot.blocks(), config.blocks, false);
        for (Map.Entry<String, HudConfig.Group> entry : config.groups.entrySet()) {
            int count = snapshot.groups().getOrDefault(entry.getKey(), 0);
            if (config.showOnlyNonZero && count == 0) continue;
            HudConfig.Group group = entry.getValue();
            entries.add(entry(entry.getKey(), count, group.limit, iconForGroup(group)));
        }
        return entries;
    }

    private void appendEntries(List<HudEntry> entries, Map<String, Integer> counts, Map<String, Integer> limits, boolean entity) {
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            if (config.showOnlyNonZero && count == 0) continue;
            entries.add(entry(displayName(entry.getKey()), count, entry.getValue(), iconFor(entry.getKey(), entity)));
        }
    }

    private static HudEntry entry(String title, int count, int limit, ItemStack icon) {
        int remaining = Math.max(0, limit - count);
        String details = count + " / " + limit + "   •   осталось " + remaining;
        return new HudEntry(title, details, statusColor(count, limit), icon);
    }

    private void renderOverloadedBlocks() {
        if (!config.enabled || !config.highlightOverloadedBlocks) return;
        List<net.minecraft.core.BlockPos> overloaded = new ArrayList<>();
        config.blocks.forEach((id, limit) -> {
            if (snapshot.blocks().getOrDefault(id, 0) > limit) overloaded.addAll(snapshot.blockPositions().getOrDefault(id, List.of()));
        });
        config.groups.forEach((name, group) -> {
            if (group.isBlock() && snapshot.groups().getOrDefault(name, 0) > group.limit) {
                overloaded.addAll(snapshot.blockPositions().getOrDefault("custom:" + name, List.of()));
            }
        });
        int maximum = Math.min(config.maxHighlightedBlocks, overloaded.size());
        GizmoStyle style = GizmoStyle.strokeAndFill(0xFFFF3030, 2.5F, 0x55FF0000);
        for (int index = 0; index < maximum; index++) {
            Gizmos.cuboid(overloaded.get(index), 0.006F, style);
        }
    }

    private static int statusColor(int count, int limit) {
        if (count >= limit) return RED;
        if (count * 4 >= limit * 3) return YELLOW;
        return GREEN;
    }

    static String displayName(String id) {
        String special = switch (id) {
            case "group:entities" -> "все сущности";
            case "group:mobs" -> "все мобы";
            case "group:minecarts" -> "все вагонетки";
            case "group:items" -> "выброшенные предметы";
            case "group:pistons" -> "все поршни";
            default -> null;
        };
        if (special != null) return special;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null) {
            var entity = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier);
            if (entity.isPresent()) return entity.get().getDescription().getString();
            var block = BuiltInRegistries.BLOCK.getOptional(identifier);
            if (block.isPresent()) return block.get().getName().getString();
        }
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    static ItemStack iconFor(String id, boolean entity) {
        return switch (id) {
            case "group:minecarts" -> new ItemStack(Items.MINECART);
            case "group:pistons" -> new ItemStack(Items.PISTON);
            case "group:items" -> new ItemStack(Items.BUNDLE);
            case "group:mobs", "group:entities" -> new ItemStack(Items.EGG);
            default -> iconForIdentifier(id, entity);
        };
    }

    static ItemStack iconForGroup(HudConfig.Group group) {
        if (group.members.isEmpty()) return new ItemStack(Items.BARRIER);
        return iconForIdentifier(group.members.getFirst(), group.isEntity());
    }

    static ItemStack iconForIdentifier(String id, boolean entity) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return new ItemStack(Items.BARRIER);
        if (entity) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier)
                    .flatMap(SpawnEggItem::byId)
                    .map(ItemStack::new)
                    .orElseGet(() -> new ItemStack(Items.EGG));
        }
        return BuiltInRegistries.BLOCK.getOptional(identifier)
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    private void rebuildHighlightedEntities(Minecraft client) {
        highlightedEntityIds.clear();
        if (!config.enabled || !config.highlightOverloadedEntities || client.level == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (highlightedEntityIds.size() >= config.maxHighlightedEntities) break;
            if (entity.chunkPosition().equals(snapshot.chunk()) && entityBelongsToOverloadedLimit(entity)) {
                highlightedEntityIds.add(entity.getId());
            }
        }
    }

    private boolean entityBelongsToOverloadedLimit(Entity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        Integer exactLimit = config.entities.get(id);
        if (exactLimit != null && snapshot.entities().getOrDefault(id, 0) > exactLimit) return true;
        if (overloadedBuiltInEntityGroup("group:entities", true)) return true;
        if (overloadedBuiltInEntityGroup("group:mobs", entity instanceof Mob)) return true;
        if (overloadedBuiltInEntityGroup("group:minecarts", entity instanceof AbstractMinecart)) return true;
        if (overloadedBuiltInEntityGroup("group:items", entity instanceof ItemEntity)) return true;
        for (Map.Entry<String, HudConfig.Group> entry : config.groups.entrySet()) {
            HudConfig.Group group = entry.getValue();
            if (group.isEntity() && group.members.contains(id) && snapshot.groups().getOrDefault(entry.getKey(), 0) > group.limit) return true;
        }
        return false;
    }

    private boolean overloadedBuiltInEntityGroup(String id, boolean matches) {
        Integer limit = config.entities.get(id);
        return matches && limit != null && snapshot.entities().getOrDefault(id, 0) > limit;
    }

    public static boolean shouldHighlightEntity(Entity entity) {
        return instance != null && instance.config.enabled && instance.config.highlightOverloadedEntities
                && instance.highlightedEntityIds.contains(entity.getId());
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("chunkhud")
                .then(literal("toggle").executes(ctx -> {
                    config.hudEnabled = !config.hudEnabled;
                    config.save();
                    ctx.getSource().sendFeedback(Component.literal("Chunk HUD: " + (config.hudEnabled ? "включён" : "выключен")));
                    return 1;
                }))
                .then(literal("menu").executes(_ -> {
                    openConfig(null);
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    config = HudConfig.load();
                    ticksUntilScan = 0;
                    ctx.getSource().sendFeedback(Component.literal("Настройки Chunk HUD перезагружены"));
                    return 1;
                }))
                .then(literal("entity")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("limit", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setLimit(ctx, true)))))
                .then(literal("block")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("limit", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setLimit(ctx, false)))))
                .then(literal("remove")
                        .then(literal("entity").then(argument("id", StringArgumentType.word()).executes(ctx -> removeLimit(ctx, true))))
                        .then(literal("block").then(argument("id", StringArgumentType.word()).executes(ctx -> removeLimit(ctx, false)))))
                .then(literal("group")
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("type", StringArgumentType.word())
                                                .then(argument("limit", IntegerArgumentType.integer(1))
                                                        .executes(this::createGroup)))))
                        .then(literal("add")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("id", StringArgumentType.word()).executes(ctx -> changeGroupMember(ctx, true)))))
                        .then(literal("remove")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("id", StringArgumentType.word()).executes(ctx -> changeGroupMember(ctx, false)))))
                        .then(literal("limit")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("limit", IntegerArgumentType.integer(1)).executes(this::changeGroupLimit))))
                        .then(literal("delete")
                                .then(argument("name", StringArgumentType.word()).executes(this::deleteGroup)))
                        .then(literal("list").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal("Свои группы: " + config.groups.keySet()));
                            return 1;
                        })))
                .then(literal("list").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("Сущности: " + config.entities));
                    ctx.getSource().sendFeedback(Component.literal("Блоки: " + config.blocks));
                    ctx.getSource().sendFeedback(Component.literal("Свои группы: " + config.groups.keySet()));
                    return 1;
                })));
    }

    private int createGroup(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
        if (!type.equals("block") && !type.equals("entity")) {
            ctx.getSource().sendError(Component.literal("Тип должен быть block или entity"));
            return 0;
        }
        if (config.groups.containsKey(name)) {
            ctx.getSource().sendError(Component.literal("Группа уже существует: " + name));
            return 0;
        }
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        config.groups.put(name, new HudConfig.Group(type, limit, List.of()));
        saveAndRescan();
        ctx.getSource().sendFeedback(Component.literal("Создана группа " + name + " [" + type + "], лимит " + limit));
        return 1;
    }

    private int changeGroupMember(CommandContext<FabricClientCommandSource> ctx, boolean add) {
        String name = StringArgumentType.getString(ctx, "name");
        HudConfig.Group group = config.groups.get(name);
        if (group == null) {
            ctx.getSource().sendError(Component.literal("Группа не найдена: " + name));
            return 0;
        }
        String rawId = StringArgumentType.getString(ctx, "id");
        Identifier identifier = Identifier.tryParse(rawId);
        boolean unknown = identifier == null || (group.isBlock()
                ? BuiltInRegistries.BLOCK.getOptional(identifier).isEmpty()
                : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).isEmpty());
        if (unknown) {
            ctx.getSource().sendError(Component.literal("Неизвестный " + group.type + " ID: " + rawId));
            return 0;
        }
        String normalized = identifier.toString();
        if (add) {
            if (!group.members.contains(normalized)) group.members.add(normalized);
        } else if (!group.members.remove(normalized)) {
            ctx.getSource().sendError(Component.literal("В группе нет: " + normalized));
            return 0;
        }
        saveAndRescan();
        ctx.getSource().sendFeedback(Component.literal((add ? "Добавлено в " : "Удалено из ") + name + ": " + normalized));
        return 1;
    }

    private int changeGroupLimit(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        HudConfig.Group group = config.groups.get(name);
        if (group == null) {
            ctx.getSource().sendError(Component.literal("Группа не найдена: " + name));
            return 0;
        }
        group.limit = IntegerArgumentType.getInteger(ctx, "limit");
        saveAndRescan();
        ctx.getSource().sendFeedback(Component.literal("Новый лимит группы " + name + ": " + group.limit));
        return 1;
    }

    private int deleteGroup(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (config.groups.remove(name) == null) {
            ctx.getSource().sendError(Component.literal("Группа не найдена: " + name));
            return 0;
        }
        saveAndRescan();
        ctx.getSource().sendFeedback(Component.literal("Группа удалена: " + name));
        return 1;
    }

    private void saveAndRescan() {
        config.save();
        ticksUntilScan = 0;
    }

    private int setLimit(CommandContext<FabricClientCommandSource> ctx, boolean entity) {
        String rawId = StringArgumentType.getString(ctx, "id");
        Identifier identifier = Identifier.tryParse(rawId);
        boolean group = isKnownGroup(rawId, entity);
        boolean missing = !group && (identifier == null || (entity
                ? BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).isEmpty()
                : BuiltInRegistries.BLOCK.getOptional(identifier).isEmpty()));
        if (missing) {
            ctx.getSource().sendError(Component.literal("Неизвестный ID: " + rawId));
            return 0;
        }
        String normalized = group ? rawId : identifier.toString();
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        (entity ? config.entities : config.blocks).put(normalized, limit);
        config.save();
        ticksUntilScan = 0;
        ctx.getSource().sendFeedback(Component.literal("Лимит " + normalized + " = " + limit));
        return 1;
    }

    private int removeLimit(CommandContext<FabricClientCommandSource> ctx, boolean entity) {
        String rawId = StringArgumentType.getString(ctx, "id");
        Identifier identifier = Identifier.tryParse(rawId);
        String normalized = identifier == null ? rawId : identifier.toString();
        Integer removed = (entity ? config.entities : config.blocks).remove(normalized);
        if (removed == null) {
            ctx.getSource().sendError(Component.literal("Такого лимита нет: " + normalized));
            return 0;
        }
        config.save();
        ticksUntilScan = 0;
        ctx.getSource().sendFeedback(Component.literal("Лимит удалён: " + normalized));
        return 1;
    }

    private static boolean isKnownGroup(String id, boolean entity) {
        return entity
                ? id.equals("group:entities") || id.equals("group:mobs") || id.equals("group:minecarts") || id.equals("group:items")
                : id.equals("group:pistons");
    }

    static HudConfig config() {
        return instance.config;
    }

    static void applyConfigChanges() {
        if (instance == null) return;
        instance.config.save();
        instance.ticksUntilScan = 0;
    }

    public static void openConfig(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new ChunkLimiterConfigScreen(parent));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    record HudSize(int width, int height) {}

    private record HudContent(String title, List<HudEntry> entries, int width, int height) {}

    private record HudEntry(String title, String details, int color, ItemStack icon) {}
}
