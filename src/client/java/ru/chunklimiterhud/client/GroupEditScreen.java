package ru.chunklimiterhud.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

final class GroupEditScreen extends Screen {
    private final Screen parent;
    private final String groupName;
    private EditBox memberField;
    private String status = "";
    private int left;
    private int panelWidth;
    private int page;
    private int rowCount;
    private List<String> visibleMembers = List.of();

    GroupEditScreen(Screen parent, String groupName) {
        super(Component.literal("Группа: " + groupName));
        this.parent = parent;
        this.groupName = groupName;
    }

    @Override
    protected void init() {
        HudConfig.Group group = group();
        if (group == null) {
            onClose();
            return;
        }
        panelWidth = Math.min(520, width - 20);
        left = (width - panelWidth) / 2;
        rowCount = Math.max(3, (height - 150) / 25);

        addRenderableWidget(Button.builder(Component.literal("Лимит −"), button -> {
            group.limit = Math.max(1, group.limit - 1);
            changed();
        }).bounds(left, 30, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Лимит +"), button -> {
            group.limit++;
            changed();
        }).bounds(left + 96, 30, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) page--;
            rebuildWidgets();
        }).bounds(left + panelWidth - 76, 30, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * rowCount < group.members.size()) page++;
            rebuildWidgets();
        }).bounds(left + panelWidth - 36, 30, 34, 20).build());

        int maxPage = Math.max(0, (group.members.size() - 1) / rowCount);
        page = Math.min(page, maxPage);
        int from = Math.min(page * rowCount, group.members.size());
        int to = Math.min(from + rowCount, group.members.size());
        visibleMembers = new ArrayList<>(group.members.subList(from, to));
        for (int index = 0; index < visibleMembers.size(); index++) {
            String member = visibleMembers.get(index);
            int y = 58 + index * 25;
            addRenderableWidget(Button.builder(Component.literal("Удалить"), button -> {
                group.members.remove(member);
                changed();
            }).bounds(left + panelWidth - 70, y + 2, 68, 20).build());
        }

        int inputY = height - 61;
        memberField = addRenderableWidget(new EditBox(font, left, inputY, panelWidth - 126, 20, Component.literal("ID объекта")));
        memberField.setHint(Component.literal(group.isEntity() ? "minecraft:villager" : "minecraft:hopper"));
        addRenderableWidget(Button.builder(Component.literal("Добавить"), button -> addMember())
                .bounds(left + panelWidth - 120, inputY, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
                .bounds(width / 2 - 75, height - 31, 150, 20).build());
    }

    private void addMember() {
        HudConfig.Group group = group();
        String raw = memberField.getValue().trim();
        Identifier id = Identifier.tryParse(raw);
        boolean known = id != null && (group.isEntity()
                ? BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent()
                : BuiltInRegistries.BLOCK.getOptional(id).isPresent());
        if (!known) {
            status = "Неизвестный ID: " + raw;
            return;
        }
        if (!group.members.contains(id.toString())) group.members.add(id.toString());
        status = "Добавлено: " + id;
        memberField.setValue("");
        changed();
    }

    private HudConfig.Group group() {
        return ChunkLimiterHudClient.config().groups.get(groupName);
    }

    private void changed() {
        ChunkLimiterHudClient.applyConfigChanges();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF111215);
        graphics.fill(left - 6, 20, left + panelWidth + 6, height - 37, 0xEE1B1D21);
        HudConfig.Group group = group();
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        if (group != null) graphics.text(font, "Тип: " + group.type + "   Лимит: " + group.limit, left + 196, 36, 0xFFB8BBC2, false);
        for (int index = 0; index < visibleMembers.size(); index++) {
            String member = visibleMembers.get(index);
            int y = 58 + index * 25;
            graphics.fill(left, y, left + panelWidth, y + 23, 0xFF27292E);
            graphics.item(ChunkLimiterHudClient.iconForIdentifier(member, group != null && group.isEntity()), left + 4, y + 4);
            graphics.text(font, ChunkLimiterHudClient.displayName(member), left + 25, y + 4, 0xFFFFFFFF, true);
            graphics.text(font, member, left + 25, y + 13, 0xFF8F9299, false);
        }
        if (!status.isEmpty()) graphics.text(font, status, left, height - 38, 0xFFFFAA55, true);
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
}
