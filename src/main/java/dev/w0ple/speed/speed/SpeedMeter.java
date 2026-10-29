package dev.w0ple.speed.speed;

/**
 * Speed from position changes between ticks (no Minecraft types). {@link #update} returns the raw
 * blocks-per-second of the last step; {@link #smoothed()} is the same with an EMA. The first sample
 * and jumps over {@link #MAX_STEP} blocks per tick (teleports) are ignored.
 */
public final class SpeedMeter {

    public static final double MAX_STEP = 10;
    public static final double SMOOTHING = 0.2;

    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean has;
    private boolean primed;
    private double smoothed;

    /** Raw speed over dt seconds, or -1 when there is no usable step (first sample, teleport). */
    public double update(double x, double y, double z, double dt) {
        boolean had = has;
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        lastX = x;
        lastY = y;
        lastZ = z;
        has = true;
        if (!had || dt <= 0) return -1;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > MAX_STEP) return -1;
        double speed = d / dt;
        if (!primed) {
            smoothed = speed;
            primed = true;
        } else {
            smoothed += (speed - smoothed) * (1 - Math.exp(-dt / SMOOTHING));
        }
        return speed;
    }

    public double smoothed() {
        return smoothed;
    }

    public void reset() {
        has = false;
        primed = false;
        smoothed = 0;
    }
}
