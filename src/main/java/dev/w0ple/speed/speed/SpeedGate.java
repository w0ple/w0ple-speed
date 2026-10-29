package dev.w0ple.speed.speed;

/**
 * Speed state machine with no Minecraft types. Smooths the raw speed, decides whether the song is
 * on and how loud it is. The song turns on once the speed reaches {@code minSpeed}; short dips
 * (between riptide dashes) only lower the volume, and the song stops only after the speed stayed
 * below {@code minSpeed} for {@link #OFF_DELAY} seconds. Volume = (v - min) / (max - min), clamped,
 * rising fast and falling slower so it does not twitch.
 */
public final class SpeedGate {

    public static final double OFF_DELAY = 3.0;
    /** Time constant of the speed smoothing, seconds. */
    public static final double SPEED_SMOOTHING = 0.2;
    public static final double LEVEL_UP = 0.15;
    public static final double LEVEL_DOWN = 0.6;

    private double smoothed;
    private double level;
    private double belowFor;
    private boolean active;
    private boolean primed;

    /** Feeds one sample (blocks per second over dt seconds); returns whether the song is on now. */
    public boolean update(double speed, double dt, double minSpeed, double maxSpeed) {
        if (dt <= 0 || Double.isNaN(speed)) return active;
        if (!primed) {
            smoothed = speed;
            primed = true;
        } else {
            smoothed += (speed - smoothed) * (1 - Math.exp(-dt / SPEED_SMOOTHING));
        }
        if (!active) {
            if (smoothed >= minSpeed) {
                active = true;
                belowFor = 0;
            }
        } else if (smoothed < minSpeed) {
            belowFor += dt;
            if (belowFor >= OFF_DELAY) {
                active = false;
                belowFor = 0;
            }
        } else {
            belowFor = 0;
        }
        double target = active ? target(smoothed, minSpeed, maxSpeed) : 0;
        double tau = target > level ? LEVEL_UP : LEVEL_DOWN;
        level += (target - level) * (1 - Math.exp(-dt / tau));
        return active;
    }

    private static double target(double v, double min, double max) {
        double span = Math.max(1e-6, max - min);
        return Math.max(0, Math.min(1, (v - min) / span));
    }

    public double smoothed() {
        return smoothed;
    }

    /** Smoothed volume factor, 0..1. */
    public double level() {
        return level;
    }

    public boolean isActive() {
        return active;
    }

    public void reset() {
        smoothed = 0;
        level = 0;
        belowFor = 0;
        active = false;
        primed = false;
    }
}
