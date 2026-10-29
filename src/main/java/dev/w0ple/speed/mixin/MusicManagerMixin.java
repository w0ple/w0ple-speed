package dev.w0ple.speed.mixin;

import dev.w0ple.speed.SpeedMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No vanilla background music while the Speed music song is playing. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void w0ple$quiet(CallbackInfo ci) {
        if (SpeedMusic.silencesVanilla()) ci.cancel();
    }
}
