package dev.w0ple.speed.speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedMeterTest {

    @Test
    void firstSampleGivesNothing() {
        assertEquals(-1, new SpeedMeter().update(0, 0, 0, 0.05));
    }

    @Test
    void constantMotionConvergesToItsSpeed() {
        var m = new SpeedMeter();
        double x = 0;
        for (int i = 0; i < 40; i++) {
            m.update(x, 0, 0, 0.05);
            x += 1.0; // 20 blocks/s
        }
        assertEquals(20, m.smoothed(), 0.01);
    }

    @Test
    void teleportIsIgnored() {
        var m = new SpeedMeter();
        for (int i = 0; i < 20; i++) m.update(i * 0.5, 0, 0, 0.05);
        double before = m.smoothed();
        assertEquals(-1, m.update(1000, 0, 0, 0.05));
        assertEquals(before, m.smoothed(), 1e-9);
        assertTrue(m.update(1000.5, 0, 0, 0.05) > 0);
    }
}
