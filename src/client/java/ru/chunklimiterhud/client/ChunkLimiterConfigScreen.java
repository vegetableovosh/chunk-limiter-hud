package ru.chunklimiterhud.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ChunkLimiterConfigScreen extends Screen {
    private static final int HEADER_Y = 28;
    private static final int TOGGLE_GAP = 6;
    private static final int SMALL_TOGGLE_WIDTH = 112;
    private static final int BLOCK_TOGGLE_WIDTH = 170;
    private static final int MIN_ENTITY_TOGGLE_WIDTH = 100;
    private static final int NAVIGATION_Y = 55;
    private static final int FILTER_X_OFFSET = 222;
    private static final int PAGING_AREA_WIDTH = 82;
    private static final int MIN_FILTER_WIDTH = 70;

    private final Screen parent;
    private boolean entityTab;
    private int page;
    private EditBox idField;
    private EditBox limitField;
    private String status = "";
    private int left;
    private int panelWidth;
    private int firstRowY;
    private int rowCount;
    private List<Row> visibleRows = List.of();

    ChunkLimiterConfigScreen(Screen parent) {
        super(Component.literal("Chunk Limiter HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HudConfig config = ChunkLimiterHudClient.config();
        panelWidth = Math.min(620, width - 20);
        left = (width - panelWidth) / 2;
        firstRowY = 82;
        rowCount = Math.max(3, (height - 182) / 25);

        int modToggleX = left;
        int hudToggleX = modToggleX + SMALL_TOGGLE_WIDTH + TOGGLE_GAP;
        int blockToggleX = hudToggleX + SMALL_TOGGLE_WIDTH + TOGGLE_GAP;
        int entityToggleX = blockToggleX + BLOCK_TOGGLE_WIDTH + TOGGLE_GAP;
        int entityToggleWidth = Math.max(
                MIN_ENTITY_TOGGLE_WIDTH,
                panelWidth - (entityToggleX - left)
        );

        addToggle(modToggleX, HEADER_Y, SMALL_TOGGLE_WIDTH, "Мод", config.enabled, _ -> {
            config.enabled = !config.enabled;
            changed();
        });
        addToggle(hudToggleX, HEADER_Y, SMALL_TOGGLE_WIDTH, "HUD", config.hudEnabled, _ -> {
            config.hudEnabled = !config.hudEnabled;
            changed();
        });
        addToggle(blockToggleX, HEADER_Y, BLOCK_TOGGLE_WIDTH, "Красить блоки", config.highlightOverloadedBlocks, _ -> {
            config.highlightOverloadedBlocks = !config.highlightOverloadedBlocks;
            changed();
        });
        addToggle(entityToggleX, HEADER_Y, entityToggleWidth, "Красить сущности", config.highlightOverloadedEntities, _ -> {
            config.highlightOverloadedEntities = !config.highlightOverloadedEntities;
            changed();
        });
        int filterWidth = Math.max(
                MIN_FILTER_WIDTH,
                panelWidth - FILTER_X_OFFSET - PAGING_AREA_WIDTH
        );
        addToggle(left + FILTER_X_OFFSET, NAVIGATION_Y, filterWidth, "Только ненулевые", config.showOnlyNonZero, _ -> {
            config.showOnlyNonZero = !config.showOnlyNonZero;
            changed();
        });

        addRenderableWidget(Button.builder(Component.literal("Блоки"), _ -> {
            entityTab = false;
            page = 0;
            rebuildWidgets();
        }).bounds(left, 55, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сущности"), _ -> {
            entityTab = true;
            page = 0;
            rebuildWidgets();
        }).bounds(left + 106, 55, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), _ -> {
            if (page > 0) page--;
            rebuildWidgets();
        }).bounds(left + panelWidth - 76, 55, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), _ -> {
            if ((page + 1) * rowCount < allRows().size()) page++;
            rebuildWidgets();
        }).bounds(left + panelWidth - 36, 55, 34, 20).build());

        List<Row> all = allRows();
        int maxPage = Math.max(0, (all.size() - 1) / rowCount);
        page = Math.min(page, maxPage);
        int from = Math.min(page * rowCount, all.size());
        int to = Math.min(from + rowCount, all.size());
        visibleRows = new ArrayList<>(all.subList(from, to));
        for (int index = 0; index < visibleRows.size(); index++) addRowButtons(visibleRows.get(index), firstRowY + index * 25);

        int inputY = height - 67;
        idField = addRenderableWidget(new EditBox(font, left, inputY, Math.max(160, panelWidth - 310), 20, Component.literal("ID или имя группы")));
        idField.setHint(Component.literal(entityTab ? "minecraft:villager или имя группы" : "minecraft:hopper или имя группы"));
        limitField = addRenderableWidget(new EditBox(font, left + panelWidth - 304, inputY, 54, 20, Component.literal("Лимит")));
        limitField.setHint(Component.literal("лимит"));
        limitField.setValue("20");
        addRenderableWidget(Button.builder(Component.literal("Добавить ID"), _ -> addExact())
                .bounds(left + panelWidth - 244, inputY, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Новая группа"), _ -> addGroup())
                .bounds(left + panelWidth - 126, inputY, 126, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), _ -> onClose())
                .bounds(width / 2 - 75, height - 32, 150, 20).build());
    }

    private void addToggle(int x, int y, int width, String label, boolean value, Button.OnPress press) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (value ? "ВКЛ" : "ВЫКЛ")), press)
                .bounds(x, y, width, 20).build());
    }

    private void addRowButtons(Row row, int y) {
        int right = left + panelWidth;
        addRenderableWidget(Button.builder(Component.literal("−"), _ -> changeLimit(row, -1))
                .bounds(right - 148, y + 2, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), _ -> changeLimit(row, 1))
                .bounds(right - 116, y + 2, 28, 20).build());
        if (row.group()) {
            addRenderableWidget(Button.builder(Component.literal("Состав"), _ -> minecraft.gui.setScreen(new GroupEditScreen(this, row.key())))
                    .bounds(right - 84, y + 2, 58, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("×"), _ -> delete(row))
                .bounds(right - 22, y + 2, 20, 20).build());
    }

    private List<Row> allRows() {
        HudConfig config = ChunkLimiterHudClient.config();
        List<Row> rows = new ArrayList<>();
        Map<String, Integer> exact = entityTab ? config.entities : config.blocks;
        exact.keySet().forEach(id -> rows.add(new Row(id, false)));
        config.groups.forEach((name, group) -> {
            if (group.isEntity() == entityTab) rows.add(new Row(name, true));
        });
        return rows;
    }

    private void changeLimit(Row row, int delta) {
        HudConfig config = ChunkLimiterHudClient.config();
        if (row.group()) {
            HudConfig.Group group = config.groups.get(row.key());
            if (group != null) group.limit = Math.max(1, group.limit + delta);
        } else {
            Map<String, Integer> limits = entityTab ? config.entities : config.blocks;
            limits.computeIfPresent(row.key(), (_, limit) -> Math.max(1, limit + delta));
        }
        changed();
    }

    private void delete(Row row) {
        HudConfig config = ChunkLimiterHudClient.config();
        if (row.group()) config.groups.remove(row.key());
        else (entityTab ? config.entities : config.blocks).remove(row.key());
        changed();
    }

    private void addExact() {
        String raw = idField.getValue().trim();
        Identifier id = Identifier.tryParse(raw);
        int limit = parsedLimit();
        boolean known = id != null && (entityTab
                ? BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent()
                : BuiltInRegistries.BLOCK.getOptional(id).isPresent());
        if (!known) {
            status = "Неизвестный ID: " + raw;
            return;
        }
        (entityTab ? ChunkLimiterHudClient.config().entities : ChunkLimiterHudClient.config().blocks).put(id.toString(), limit);
        status = "Добавлено: " + id;
        idField.setValue("");
        changed();
    }

    private void addGroup() {
        String name = idField.getValue().trim();
        if (name.isBlank()) {
            status = "Введите имя группы";
            return;
        }
        HudConfig config = ChunkLimiterHudClient.config();
        if (config.groups.containsKey(name)) {
            status = "Группа уже существует";
            return;
        }
        config.groups.put(name, new HudConfig.Group(entityTab ? "entity" : "block", parsedLimit(), List.of()));
        status = "Создана группа: " + name;
        idField.setValue("");
        changed();
    }

    private int parsedLimit() {
        try {
            return Math.max(1, Integer.parseInt(limitField.getValue().trim()));
        } catch (NumberFormatException ignored) {
            return 20;
        }
    }

    private void changed() {
        ChunkLimiterHudClient.applyConfigChanges();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF111215);
        graphics.fill(left - 6, 20, left + panelWidth + 6, height - 38, 0xEE1B1D21);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        for (int index = 0; index < visibleRows.size(); index++) {
            Row row = visibleRows.get(index);
            int y = firstRowY + index * 25;
            graphics.fill(left, y, left + panelWidth, y + 23, 0xFF27292E);
            ItemStack icon = row.icon();
            graphics.item(icon, left + 4, y + 4);
            graphics.text(font, row.label(), left + 25, y + 4, 0xFFFFFFFF, true);
            graphics.text(font, "Лимит: " + row.limit(), left + panelWidth - 220, y + 7, 0xFFB8BBC2, false);
        }
        if (!status.isEmpty()) graphics.text(font, status, left, height - 43, 0xFFFFAA55, true);
        graphics.text(font, "Страница " + (page + 1), left + panelWidth - 165, 61, 0xFF999CA3, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ChunkLimiterHudClient.applyConfigChanges();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(String key, boolean group) {
        private String label() {
            return group ? key + "  [группа]" : ChunkLimiterHudClient.displayName(key);
        }

        private int limit() {
            HudConfig config = ChunkLimiterHudClient.config();
            if (group) return config.groups.get(key).limit;
            Integer value = config.entities.get(key);
            if (value == null) value = config.blocks.getOrDefault(key, 1);
            return value;
        }

        private ItemStack icon() {
            HudConfig config = ChunkLimiterHudClient.config();
            if (group) return ChunkLimiterHudClient.iconForGroup(config.groups.get(key));
            return ChunkLimiterHudClient.iconFor(key, config.entities.containsKey(key));
        }
    }
}
