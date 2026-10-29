package dev.w0ple.speed.sound;

import dev.w0ple.speed.speed.EnergyWindows;
import dev.w0ple.speed.speed.PeakFinder;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp3AudioStreamTest {

    private static InputStream sample() {
        return Mp3AudioStreamTest.class.getResourceAsStream("/test.mp3");
    }

    @Test
    void formatAndLengthMatchTheFile() throws IOException {
        try (Mp3AudioStream s = new Mp3AudioStream(sample())) {
            AudioFormat f = s.getFormat();
            assertEquals(44100f, f.getSampleRate());
            assertEquals(2, f.getChannels());
            assertEquals(16, f.getSampleSizeInBits());
            long bytes = 0;
            while (true) {
                ByteBuffer b = s.read(4096);
                if (!b.hasRemaining()) break;
                bytes += b.remaining();
            }
            double seconds = bytes / (double) (44100 * 4);
            assertEquals(2.0, seconds, 0.15);
        }
    }

    @Test
    void decodesANonSilentSine() throws IOException {
        try (Mp3AudioStream s = new Mp3AudioStream(sample())) {
            ByteBuffer b = s.read(1 << 16);
            int max = 0;
            while (b.remaining() >= 2) max = Math.max(max, Math.abs(b.getShort()));
            assertTrue(max > 1000, "peak amplitude " + max);
        }
    }

    @Test
    void peakFinderRunsOnTheDecodedStream() throws IOException {
        try (Mp3AudioStream s = new Mp3AudioStream(sample())) {
            AudioFormat f = s.getFormat();
            EnergyWindows w = new EnergyWindows((int) f.getSampleRate(), f.getChannels(), 0.5);
            byte[] buf = new byte[1 << 16];
            while (true) {
                ByteBuffer b = s.read(buf.length);
                if (!b.hasRemaining()) break;
                int n = b.remaining();
                b.get(buf, 0, n);
                w.add(buf, 0, n);
            }
            double peak = assertDoesNotThrow(() -> PeakFinder.find(w.finish(), 0.5));
            assertTrue(peak >= 0 && peak <= 2.5, "peak " + peak);
        }
    }

    @Test
    void seekingSkipsTheRequestedTime() throws IOException {
        try (SeekingStream s = new SeekingStream(new Mp3AudioStream(sample()), 1.0)) {
            long bytes = 0;
            while (true) {
                ByteBuffer b = s.read(4096);
                if (!b.hasRemaining()) break;
                bytes += b.remaining();
            }
            assertEquals(1.0, bytes / (double) (44100 * 4), 0.15);
        }
    }

    @Test
    void garbageIsRejected() {
        assertThrows(IOException.class, () -> new Mp3AudioStream(new ByteArrayInputStream(new byte[4096])));
    }
}
