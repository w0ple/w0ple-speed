package dev.w0ple.speed.speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeakFinderTest {

    private static final double W = 0.5;

    private static double[] track(double quietSeconds, double loudSeconds, double quiet, double loud) {
        int q = (int) (quietSeconds / W);
        int l = (int) (loudSeconds / W);
        double[] e = new double[q + l];
        for (int i = 0; i < e.length; i++) e[i] = i < q ? quiet : loud;
        return e;
    }

    @Test
    void findsQuietToLoudTransition() {
        double[] e = track(60, 40, 0.05, 0.5);
        assertEquals(60, PeakFinder.find(e, W), 1.0);
    }

    @Test
    void picksTheBigDropNotTheSmallRise() {
        // quiet 0-30, medium 30-60, quiet 60-90, very loud 90-130
        double[] e = new double[260];
        for (int i = 0; i < e.length; i++) {
            double t = i * W;
            e[i] = t < 30 ? 0.05 : t < 60 ? 0.25 : t < 90 ? 0.05 : 0.6;
        }
        assertEquals(90, PeakFinder.find(e, W), 1.0);
    }

    @Test
    void silentOrEmptyTrackGivesZero() {
        assertEquals(0, PeakFinder.find(new double[0], W));
        assertEquals(0, PeakFinder.find(new double[100], W));
    }

    @Test
    void windowEnergiesFromPcm() {
        var w = new EnergyWindows(100, 1, 0.5);
        byte[] pcm = new byte[200 * 2];
        for (int i = 0; i < 100; i++) { // first second: loud (16384), second: silent
            pcm[i * 2] = 0;
            pcm[i * 2 + 1] = 0x40;
        }
        w.add(pcm, 0, pcm.length);
        double[] e = w.finish();
        assertEquals(4, e.length);
        assertEquals(0.5, e[0], 1e-6);
        assertEquals(0, e[3], 1e-9);
    }
}
