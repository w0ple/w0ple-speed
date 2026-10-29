package dev.w0ple.speed.speed;

import java.util.ArrayList;
import java.util.List;

/** Collects 16-bit little-endian PCM and turns it into one RMS value per fixed window. */
public final class EnergyWindows {

    private final int channels;
    private final int framesPerWindow;
    private final List<Double> out = new ArrayList<>();
    private double sumSquares;
    private int frames;
    private double frameSum;
    private int channel;

    public EnergyWindows(int sampleRate, int channels, double windowSeconds) {
        this.channels = Math.max(1, channels);
        this.framesPerWindow = Math.max(1, (int) Math.round(sampleRate * windowSeconds));
    }

    /** Adds interleaved samples; a frame split between two calls is handled. */
    public void add(byte[] pcm, int offset, int length) {
        for (int i = offset; i + 1 < offset + length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            frameSum += s / 32768.0;
            if (++channel == channels) {
                double mono = frameSum / channels;
                sumSquares += mono * mono;
                channel = 0;
                frameSum = 0;
                if (++frames == framesPerWindow) flush();
            }
        }
    }

    private void flush() {
        out.add(Math.sqrt(sumSquares / frames));
        sumSquares = 0;
        frames = 0;
    }

    /** All full windows plus the partial last one (if it holds at least half a window). */
    public double[] finish() {
        if (frames * 2 >= framesPerWindow) flush();
        double[] result = new double[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }
}
