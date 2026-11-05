package ru.chunklimiterhud.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@NullMarked
final class RegistryPickerScreen extends Screen {
    private static final int TILE_HEIGHT = 42;
    private static final int TILE_GAP = 4;
    private static final int FIRST_ROW_Y = 62;
    private static final int MIN_SAFE_WIDTH = 240;
    private static final int MIN_SAFE_HEIGHT = 160;

    private final ChunkLimiterConfigScreen parent;
    private final boolean entities;
    private List<PickerEntry> allEntries = List.of();
    private List<PickerEntry> filteredEntries = List.of();
    private String query = "";
    private int page;
    private int left;
    private int panelWidth;
    private int columns;
    private int rows;
    private int tileWidth;

    RegistryPickerScreen(ChunkLimiterConfigScreen parent, boolean entities) {
        super(Component.literal(entities ? "Выбор сущности" : "Выбор блока"));
        this.parent = parent;
        this.entities = entities;
    }

    @Override
    protected void init() {
        panelWidth = Math.max(20, Math.min(780, width - 20));
        left = (width - panelWidth) / 2;
        columns = Math.max(1, panelWidth / 150);
        rows = Math.max(1, (height - FIRST_ROW_Y - 42) / (TILE_HEIGHT + TILE_GAP));
        tileWidth = (panelWidth - (columns - 1) * TILE_GAP) / columns;

        if (allEntries.isEmpty()) {
            allEntries = loadEntries();
            updateFilter();
        }

        EditBox search = addRenderableWidget(new EditBox(
                font,
                left,
                32,
                Math.max(100, panelWidth - 190),
                20,
                Component.literal("Поиск")
        ));
        search.setHint(Component.literal(entities ? "Например: житель или villager" : "Например: воронка или hopper"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            updateFilter();
        });

        addRenderableWidget(Button.builder(Component.literal("<"), _ -> changePage(-1))
                .bounds(left + panelWidth - 84, 32, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), _ -> changePage(1))
                .bounds(left + panelWidth - 42, 32, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), _ -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    private List<PickerEntry> loadEntries() {
        List<PickerEntry> result = new ArrayList<>();
        if (entities) {
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type ->
                        result.add(new PickerEntry(id.toString(), type.getDescription().getString(), icon(id.toString())))
                );
            }
        } else {
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block ->
                        result.add(new PickerEntry(id.toString(), block.getName().getString(), icon(id.toString())))
                );
            }
        }
        result.sort(Comparator.comparing(PickerEntry::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private ItemStack icon(String id) {
        ItemStack icon = ChunkLimiterHudClient.iconForIdentifier(id, entities);
        return icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon;
    }

    private void updateFilter() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            filteredEntries = allEntries;
        } else {
            filteredEntries = allEntries.stream()
                    .filter(entry -> entry.searchText().contains(normalized))
                    .toList();
        }
        page = Math.min(page, maxPage());
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(maxPage(), page + delta));
    }

    private int pageSize() {
        return columns * rows;
    }

    private int maxPage() {
        return Math.max(0, (filteredEntries.size() - 1) / Math.max(1, pageSize()));
    }

    private List<PickerEntry> visibleEntries() {
        int from = Math.min(page * pageSize(), filteredEntries.size());
        int to = Math.min(from + pageSize(), filteredEntries.size());
        return filteredEntries.subList(from, to);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF111215);
        if (width < MIN_SAFE_WIDTH || height < MIN_SAFE_HEIGHT) return;
        graphics.fill(left - 6, 24, left + panelWidth + 6, height - 34, 0xEE1B1D21);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

        List<PickerEntry> visible = visibleEntries();
        for (int index = 0; index < visible.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = left + column * (tileWidth + TILE_GAP);
            int y = FIRST_ROW_Y + row * (TILE_HEIGHT + TILE_GAP);
            boolean hovered = mouseX >= x && mouseX < x + tileWidth && mouseY >= y && mouseY < y + TILE_HEIGHT;
            graphics.fill(x, y, x + tileWidth, y + TILE_HEIGHT, hovered ? 0xFF353941 : 0xFF27292E);
            graphics.outline(x, y, tileWidth, TILE_HEIGHT, hovered ? 0xFF7C818C : 0xFF45484F);

            PickerEntry entry = visible.get(index);
            graphics.item(entry.icon(), x + 5, y + 13);
            graphics.text(font, fit(entry.name(), tileWidth - 29), x + 25, y + 8, 0xFFFFFFFF, true);
            graphics.text(font, fit(entry.id(), tileWidth - 29), x + 25, y + 23, 0xFF9EA2AA, false);
        }

        String pageText = filteredEntries.isEmpty()
                ? "Ничего не найдено"
                : "Найдено: " + filteredEntries.size() + "  •  страница " + (page + 1) + "/" + (maxPage() + 1);
        graphics.text(font, pageText, left, 54, 0xFFB8BBC2, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        int relativeX = (int) event.x() - left;
        int relativeY = (int) event.y() - FIRST_ROW_Y;
        if (relativeX < 0 || relativeY < 0) return false;
        int column = relativeX / (tileWidth + TILE_GAP);
        int row = relativeY / (TILE_HEIGHT + TILE_GAP);
        if (column >= columns || row >= rows) return false;
        if (relativeX % (tileWidth + TILE_GAP) >= tileWidth || relativeY % (TILE_HEIGHT + TILE_GAP) >= TILE_HEIGHT) return false;

        int index = row * columns + column;
        List<PickerEntry> visible = visibleEntries();
        if (index >= visible.size()) return false;
        PickerEntry selected = visible.get(index);
        parent.selectRegistryId(selected.id(), selected.name());
        minecraft.gui.setScreen(parent);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0 && mouseY >= FIRST_ROW_Y && mouseY < height - 34) {
            changePage(verticalAmount > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String fit(String text, int maximumWidth) {
        if (font.width(text) <= maximumWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > maximumWidth) end--;
        return text.substring(0, end) + suffix;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record PickerEntry(String id, String name, ItemStack icon) {
        private String searchText() {
            return (name + " " + id).toLowerCase(Locale.ROOT);
        }
    }
}
