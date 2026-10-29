package dev.w0ple.speed.sound;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Picks the decoder by the file extension: the one place that knows which formats the mod plays. */
public final class SongDecoder {

    private SongDecoder() {
    }

    public static boolean isSong(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".mp3");
    }

    /** ogg sorts before mp3 when both exist; ties are broken by name. */
    public static int compare(Path a, Path b) {
        int byType = Boolean.compare(isMp3(a), isMp3(b));
        return byType != 0 ? byType : a.getFileName().toString().compareTo(b.getFileName().toString());
    }

    private static boolean isMp3(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    public static AudioStream open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        try {
            return isMp3(file) ? new Mp3AudioStream(in) : new JOrbisAudioStream(in);
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }
}
