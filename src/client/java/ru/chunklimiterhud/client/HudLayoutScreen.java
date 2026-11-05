package ru.chunklimiterhud.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.jspecify.annotations.NullMarked;

final class HudLayoutScreen extends Screen {
    private static final float SCALE_STEP = 0.1F;
    private static final int MIN_SAFE_WIDTH = 240;
    private static final int MIN_SAFE_HEIGHT = 130;

    private final Screen parent;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    HudLayoutScreen(Screen parent) {
        super(Component.literal("Положение HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HudConfig config = ChunkLimiterHudClient.config();
        clampPosition();

        int scaleY = height - 56;
        int scaleControlsWidth = Math.max(120, Math.min(210, width - 20));
        int scaleLeft = (width - scaleControlsWidth) / 2;
        int scaleButtonWidth = Math.min(40, scaleControlsWidth / 4);
        int scaleLabelWidth = Math.max(40, scaleControlsWidth - scaleButtonWidth * 2 - 10);
        addRenderableWidget(Button.builder(Component.literal("−"), _ -> changeScale(-SCALE_STEP))
                .bounds(scaleLeft, scaleY, scaleButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Масштаб: " + Math.round(config.hudScale * 100) + "%"), _ -> resetScale())
                .bounds(scaleLeft + scaleButtonWidth + 5, scaleY, scaleLabelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), _ -> changeScale(SCALE_STEP))
                .bounds(scaleLeft + scaleControlsWidth - scaleButtonWidth, scaleY, scaleButtonWidth, 20).build());

        int footerWidth = Math.max(120, Math.min(310, width - 20));
        int footerLeft = (width - footerWidth) / 2;
        int footerButtonWidth = (footerWidth - 10) / 2;
        addRenderableWidget(Button.builder(Component.literal("Сбросить"), _ -> resetLayout())
                .bounds(footerLeft, height - 30, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), _ -> onClose())
                .bounds(footerLeft + footerButtonWidth + 10, height - 30, footerButtonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HudConfig config = ChunkLimiterHudClient.config();
        graphics.fill(0, 0, width, height, 0xB0111215);
        if (width < MIN_SAFE_WIDTH || height < MIN_SAFE_HEIGHT) return;
        ChunkLimiterHudClient.renderHudPreview(graphics, config.x, config.y, config.hudScale);

        graphics.fill(0, 0, width, 28, 0xE827292E);
        graphics.centeredText(font, "Перетащите HUD мышью • колесо меняет масштаб", width / 2, 5, 0xFFFFFFFF);
        graphics.centeredText(font, "Нажатие на значение масштаба возвращает 100%", width / 2, 16, 0xFFB8BBC2);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @NullMarked
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverHud(event.x(), event.y())) {
            HudConfig config = ChunkLimiterHudClient.config();
            dragging = true;
            dragOffsetX = event.x() - config.x;
            dragOffsetY = event.y() - config.y;
            return true;
        }
        return false;
    }

    @NullMarked
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        HudConfig config = ChunkLimiterHudClient.config();
        config.x = (int) Math.round(event.x() - dragOffsetX);
        config.y = (int) Math.round(event.y() - dragOffsetY);
        clampPosition();
        return true;
    }

    @NullMarked
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            ChunkLimiterHudClient.applyConfigChanges();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0 && isOverHud(mouseX, mouseY)) {
            changeScale(verticalAmount > 0.0 ? SCALE_STEP : -SCALE_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isOverHud(double mouseX, double mouseY) {
        HudConfig config = ChunkLimiterHudClient.config();
        ChunkLimiterHudClient.HudSize size = ChunkLimiterHudClient.currentHudSize(config.hudScale);
        return mouseX >= config.x && mouseX < config.x + size.width()
                && mouseY >= config.y && mouseY < config.y + size.height();
    }

    private void changeScale(float delta) {
        HudConfig config = ChunkLimiterHudClient.config();
        ChunkLimiterHudClient.HudSize oldSize = ChunkLimiterHudClient.currentHudSize(config.hudScale);
        float nextScale = Math.round((config.hudScale + delta) * 10.0F) / 10.0F;
        config.hudScale = Math.max(HudConfig.MIN_HUD_SCALE, Math.min(HudConfig.MAX_HUD_SCALE, nextScale));
        ChunkLimiterHudClient.HudSize newSize = ChunkLimiterHudClient.currentHudSize(config.hudScale);
        config.x += (oldSize.width() - newSize.width()) / 2;
        config.y += (oldSize.height() - newSize.height()) / 2;
        clampPosition();
        ChunkLimiterHudClient.applyConfigChanges();
        rebuildWidgets();
    }

    private void resetScale() {
        HudConfig config = ChunkLimiterHudClient.config();
        config.hudScale = HudConfig.DEFAULT_HUD_SCALE;
        clampPosition();
        ChunkLimiterHudClient.applyConfigChanges();
        rebuildWidgets();
    }

    private void resetLayout() {
        HudConfig config = ChunkLimiterHudClient.config();
        config.x = 8;
        config.y = 8;
        config.hudScale = HudConfig.DEFAULT_HUD_SCALE;
        clampPosition();
        ChunkLimiterHudClient.applyConfigChanges();
        rebuildWidgets();
    }

    private void clampPosition() {
        HudConfig config = ChunkLimiterHudClient.config();
        ChunkLimiterHudClient.HudSize size = ChunkLimiterHudClient.currentHudSize(config.hudScale);
        config.x = Math.max(0, Math.min(config.x, Math.max(0, width - size.width())));
        config.y = Math.max(0, Math.min(config.y, Math.max(0, height - size.height())));
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
