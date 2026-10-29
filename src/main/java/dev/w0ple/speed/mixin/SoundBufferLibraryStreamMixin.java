package dev.w0ple.speed.mixin;

import dev.w0ple.speed.sound.SeekingStream;
import dev.w0ple.speed.sound.SongDecoder;
import dev.w0ple.speed.sound.SpeedTrack;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Starts the Speed music song from its peak: its stream skips the PCM before the offset. */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryStreamMixin {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void w0ple$seek(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!SpeedTrack.ID.getNamespace().equals(id.getNamespace()) || !id.getPath().contains("speed_music_track")) return;
        Path song = SpeedTrack.file();
        if (song == null) return;
        double offset = SpeedTrack.takeOffset();
        // the decoder (ogg or mp3) is chosen by the file extension, not by the virtual name
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                return new SeekingStream(SongDecoder.open(song), offset);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool()));
    }
}
