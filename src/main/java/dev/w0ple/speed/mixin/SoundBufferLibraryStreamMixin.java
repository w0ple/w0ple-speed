package dev.w0ple.speed.mixin;

import dev.w0ple.speed.sound.SeekingStream;
import dev.w0ple.speed.sound.SpeedTrack;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Starts the Speed music song from its peak: its stream skips the PCM before the offset. */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryStreamMixin {

    @Inject(method = "getStream", at = @At("RETURN"), cancellable = true)
    private void w0ple$seek(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!SpeedTrack.ID.getNamespace().equals(id.getNamespace()) || !id.getPath().contains("speed_music_track")) return;
        double offset = SpeedTrack.takeOffset();
        cir.setReturnValue(cir.getReturnValue().thenApply(s -> new SeekingStream(s, offset)));
    }
}
