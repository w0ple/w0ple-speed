package dev.w0ple.speed.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.w0ple.speed.Afterimages;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While an Afterimages copy is drawn: a flat white texture with the full-bright translucent
 * (emissive) render type, tinted with the copy's colour and fading alpha.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @ModifyReturnValue(method = "getModelTint", at = @At("RETURN"))
    private int w0ple$tint(int original) {
        return Afterimages.isDrawing() ? Afterimages.tint() : original;
    }

    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    private RenderType w0ple$flat(RenderType original) {
        return Afterimages.isDrawing() ? RenderTypes.entityTranslucentEmissive(Afterimages.WHITE) : original;
    }
}
