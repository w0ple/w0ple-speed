package dev.w0ple.speed.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The song of the Speed music module: a streamed, tickable MUSIC sound whose gain fades in and out.
 * The file is served under a fixed virtual name (see {@link #wrap}); where playback
 * starts is passed to the stream through {@link #takeOffset()} (see SoundBufferLibraryStreamMixin).
 */
public final class SpeedTrack extends AbstractSoundInstance implements TickableSoundInstance {

    public static final String NAMESPACE = "w0plespeed_file";
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NAMESPACE, "speed_music_track");
    public static final String VIRTUAL_FILE = "speed_music_track.ogg";
    public static final float FADE_IN_SECONDS = 0.5f;
    public static final float FADE_OUT_SECONDS = 1f;
    private static final float TICK = 0.05f;

    private static volatile Path file;
    private static volatile double pendingOffset;

    private float fade;
    private float target = 1f;
    private float gain;
    private boolean stopped;

    public SpeedTrack(float gain) {
        super(ID, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.gain = gain;
        this.volume = 0f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    /** Wraps the sound library's resource provider so our virtual name reads the song file. */
    public static ResourceProvider wrap(ResourceProvider original) {
        return new ResourceProvider() {
            @Override
            public Optional<Resource> getResource(Identifier location) {
                return original.getResource(location);
            }

            @Override
            public InputStream open(Identifier location) throws IOException {
                if (NAMESPACE.equals(location.getNamespace()) && location.getPath().equals("sounds/" + VIRTUAL_FILE) && file != null) {
                    return Files.newInputStream(file);
                }
                return original.open(location);
            }
        };
    }

    /** The file the virtual name points at and the second the next stream starts from. */
    public static void prepare(Path song, double offsetSeconds) {
        file = song;
        pendingOffset = offsetSeconds;
    }

    public static Path file() {
        return file;
    }

    /** Called by the stream mixin once per started stream. */
    public static double takeOffset() {
        double o = pendingOffset;
        pendingOffset = 0;
        return o;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        this.sound = new Sound(ID, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(ID, null);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** The loudness the song is fading towards: 1 = fully on, 0 = fading out. */
    public void setTarget(float t) {
        target = t;
    }

    public void setGain(float g) {
        gain = g;
    }

    public float fade() {
        return fade;
    }

    public void stopNow() {
        stopped = true;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public void tick() {
        float step = target > fade ? TICK / FADE_IN_SECONDS : TICK / FADE_OUT_SECONDS;
        fade = target > fade ? Math.min(target, fade + step) : Math.max(target, fade - step);
        volume = fade * gain;
    }
}
