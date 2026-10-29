package dev.w0ple.speed;

import dev.w0ple.speed.config.SpeedConfig;
import dev.w0ple.speed.sound.SongDecoder;
import dev.w0ple.speed.sound.SpeedTrack;
import dev.w0ple.speed.speed.EnergyWindows;
import dev.w0ple.speed.speed.PeakFinder;
import dev.w0ple.speed.speed.SpeedGate;
import dev.w0ple.speed.speed.SpeedMeter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.entity.Entity;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The song gets louder the faster the player moves. The first .ogg or .mp3 in config/w0plespeed starts from
 * its peak (the drop) once the speed passes the minimum, its volume follows the speed, and it stops
 * (and goes back to the peak) after a few seconds below the minimum.
 */
public final class SpeedMusic {

    private static final double DT = 0.05;
    private static final double WINDOW = 0.5;
    private static final int RESCAN_TICKS = 40;
    private static final int RESTART_TICKS = 20;

    private final SpeedConfig cfg = SpeedConfig.get();
    private final SpeedGate gate = new SpeedGate();
    private final SpeedMeter meter = new SpeedMeter();
    private SpeedTrack track;
    private Path song;
    private int rescan;
    private int restartIn;
    private boolean vanillaStopped;

    private volatile String foundName;
    private volatile long foundSize;
    private volatile double foundPeak = -1;
    private String analysing = "";

    /** From the MusicManager mixin: true while our song is on, so vanilla music stays quiet. */
    public static boolean silencesVanilla() {
        SpeedMusic m = W0pleSpeed.music();
        return W0pleSpeed.active() && m != null && SpeedConfig.get().musicEnabled && m.track != null && !m.track.isStopped();
    }

    public void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        if (!cfg.musicEnabled) {
            if (track != null) stopTrack(mc);
            gate.reset();
            meter.reset();
            return;
        }
        applyFound();
        if (rescan-- <= 0) {
            rescan = RESCAN_TICKS;
            song = firstSong();
            maybeAnalyse();
        }
        // a track the engine has dropped (world left, device change) is forgotten
        if (track != null && track.fade() > 0.01f && !mc.getSoundManager().isActive(track)) track = null;

        double speed = speedOf(p);
        if (speed < 0) return;
        double max = Math.max(cfg.maxSpeed, cfg.minSpeed + 1);
        boolean on = gate.update(speed, DT, cfg.minSpeed, max) && song != null;
        if (on) {
            if (restartIn > 0) restartIn--;
            if (track == null && restartIn <= 0) startTrack(mc);
            if (track != null) {
                track.setTarget(1f);
                track.setGain(gain());
            }
            return;
        }
        // off: fade out, then forget the track so the next start is from the peak again
        if (track != null) {
            track.setTarget(0f);
            track.setGain(gain());
            if (track.fade() <= 0f) stopTrack(mc);
        }
        vanillaStopped = false;
    }

    private float gain() {
        float v = (float) cfg.volume / 100f;
        return cfg.volumeBySpeed ? v * (float) gate.level() : v;
    }

    /** Blocks per second moved since the last tick (the vehicle's, when riding); -1 on a teleport or first sample. */
    private double speedOf(LocalPlayer p) {
        Entity e = p.getVehicle() != null ? p.getVehicle() : p;
        return meter.update(e.getX(), e.getY(), e.getZ(), DT);
    }

    private void startTrack(Minecraft mc) {
        double at = cfg.peakSeconds >= 0 ? cfg.peakSeconds : Math.max(0, usablePeak());
        SpeedTrack.prepare(song, at);
        track = new SpeedTrack(gain());
        mc.getSoundManager().play(track);
        if (!vanillaStopped) {
            mc.getMusicManager().stopPlaying();
            vanillaStopped = true;
        }
    }

    private void stopTrack(Minecraft mc) {
        if (track != null) {
            track.stopNow();
            mc.getSoundManager().stop(track);
            track = null;
        }
        restartIn = RESTART_TICKS;
        vanillaStopped = false;
    }

    // ---- song and peak -----------------------------------------------------

    private static Path firstSong() {
        if (!Files.isDirectory(SpeedConfig.DIR)) return null;
        try (Stream<Path> files = Files.list(SpeedConfig.DIR)) {
            return files.filter(SongDecoder::isSong)
                    .sorted(SongDecoder::compare).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean cacheValid() {
        try {
            return song != null && cfg.peakValue >= 0 && cfg.peakFile.equals(song.getFileName().toString()) && cfg.peakSize == Files.size(song);
        } catch (IOException e) {
            return false;
        }
    }

    private double usablePeak() {
        return cacheValid() ? cfg.peakValue : 0;
    }

    private void maybeAnalyse() {
        if (song == null || cacheValid()) return;
        String name = song.getFileName().toString();
        long size;
        try {
            size = Files.size(song);
        } catch (IOException e) {
            return;
        }
        String key = name + ":" + size;
        if (key.equals(analysing)) return;
        analysing = key;
        Path file = song;
        Thread t = new Thread(() -> {
            double peak = analyse(file);
            foundName = name;
            foundSize = size;
            foundPeak = peak;
        }, "w0ple-speed-peak");
        t.setDaemon(true);
        t.start();
    }

    private void applyFound() {
        double peak = foundPeak;
        if (peak < 0) return;
        foundPeak = -1;
        cfg.peakFile = foundName;
        cfg.peakSize = foundSize;
        cfg.peakValue = peak;
        SpeedConfig.save();
    }

    /** Decodes the file and finds the start of its drop; 0 when it cannot be read. */
    private static double analyse(Path file) {
        try (AudioStream ogg = SongDecoder.open(file)) {
            AudioFormat f = ogg.getFormat();
            EnergyWindows windows = new EnergyWindows((int) f.getSampleRate(), f.getChannels(), WINDOW);
            byte[] buf = new byte[1 << 16];
            while (true) {
                ByteBuffer b = ogg.read(buf.length);
                if (b == null || !b.hasRemaining()) break;
                int n = b.remaining();
                if (n > buf.length) buf = new byte[n];
                b.get(buf, 0, n);
                windows.add(buf, 0, n);
            }
            return PeakFinder.find(windows.finish(), WINDOW);
        } catch (IOException | RuntimeException e) {
            W0pleSpeed.LOGGER.warn("Cannot analyse {}", file, e);
            return 0;
        }
    }

    /** One line for the settings screen: what the mod found in the song folder. */
    public String statusText() {
        if (song == null) return "Положи песню .mp3 или .ogg в config/w0plespeed";
        if (!cacheValid()) return "Ищу пик песни...";
        int s = (int) Math.round(cfg.peakSeconds >= 0 ? cfg.peakSeconds : cfg.peakValue);
        return String.format(Locale.ROOT, "Пик: %d:%02d%s", s / 60, s % 60, cfg.peakSeconds >= 0 ? " (вручную)" : "");
    }

    /** Test hook: whether the song is playing now. */
    public boolean playing() {
        return track != null && Minecraft.getInstance().getSoundManager().isActive(track);
    }

    public double speedNow() {
        return gate.smoothed();
    }
}
