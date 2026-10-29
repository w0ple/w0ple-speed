package dev.w0ple.speed.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.w0ple.speed.Afterimages;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Afterimages copies are a bare body: no cape, armour, items or cosmetics. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @ModifyReturnValue(method = "shouldRenderLayers", at = @At("RETURN"))
    private boolean w0ple$bare(boolean original) {
        return !Afterimages.isDrawing() && original;
    }
}
