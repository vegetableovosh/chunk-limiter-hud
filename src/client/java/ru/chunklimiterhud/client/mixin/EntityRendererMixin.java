package ru.chunklimiterhud.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.chunklimiterhud.client.ChunkLimiterHudClient;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void chunkLimiterHud$applyRedOutline(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (ChunkLimiterHudClient.shouldHighlightEntity(entity)) {
            state.outlineColor = 0xFFFF3030;
        }
    }
}
