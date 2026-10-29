package dev.w0ple.speed.speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedGateTest {

    private static final double DT = 0.05;
    private static final double MIN = 8;
    private static final double MAX = 40;

    private static boolean run(SpeedGate g, double speed, double seconds) {
        boolean on = g.isActive();
        for (int i = 0; i < Math.round(seconds / DT); i++) on = g.update(speed, DT, MIN, MAX);
        return on;
    }

    @Test
    void silentBelowMinimum() {
        var g = new SpeedGate();
        assertFalse(run(g, 5, 10));
        assertEquals(0, g.level(), 1e-9);
    }

    @Test
    void startsAboveMinimumAndLevelFollowsSpeed() {
        var g = new SpeedGate();
        assertTrue(run(g, 24, 3));
        assertEquals(0.5, g.level(), 0.02);
        run(g, 40, 3);
        assertEquals(1, g.level(), 0.02);
        run(g, 100, 3);
        assertTrue(g.level() <= 1.0);
    }

    @Test
    void shortDipsDoNotStopTheSong() {
        var g = new SpeedGate();
        run(g, 30, 2);
        for (int dash = 0; dash < 5; dash++) {
            assertTrue(run(g, 2, 2.0), "dip of 2 s must keep the song");
            run(g, 30, 1);
        }
        assertTrue(g.isActive());
    }

    @Test
    void stopsOnlyAfterThreeSecondsBelowMinimum() {
        var g = new SpeedGate();
        run(g, 30, 2);
        assertTrue(run(g, 0, 2.5));
        assertFalse(run(g, 0, 1.5));
        assertEquals(0, g.level(), 0.05);
    }

    @Test
    void volumeRisesFasterThanItFalls() {
        var up = new SpeedGate();
        run(up, 9, 2);
        double before = up.level();
        run(up, 40, 0.3);
        double rise = up.level() - before;

        var down = new SpeedGate();
        run(down, 40, 3);
        double high = down.level();
        run(down, 9, 0.3);
        double fall = high - down.level();
        assertTrue(rise > fall, "rise " + rise + " must beat fall " + fall);
    }

    @Test
    void restartsAfterStop() {
        var g = new SpeedGate();
        run(g, 30, 2);
        run(g, 0, 4);
        assertFalse(g.isActive());
        assertTrue(run(g, 30, 1));
    }
}
