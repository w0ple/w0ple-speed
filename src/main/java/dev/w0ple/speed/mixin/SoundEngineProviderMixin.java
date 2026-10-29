package dev.w0ple.speed.mixin;

import dev.w0ple.speed.sound.SpeedTrack;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Lets the sound library load the song from the config folder (see SpeedTrack). */
@Mixin(SoundEngine.class)
public abstract class SoundEngineProviderMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;)V"))
    private ResourceProvider w0ple$wrap(ResourceProvider original) {
        return SpeedTrack.wrap(original);
    }
}
