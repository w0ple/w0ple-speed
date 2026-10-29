package dev.w0ple.speed.speed;

/**
 * Finds where a song's "drop" starts, from loudness per window. For every point t:
 * next = mean energy of [t, t+8 s), prev = mean of [t-4 s, t). Among the points whose next is at
 * least 0.8 of the best next, the one with the biggest next - prev is the entry into the loud part.
 */
public final class PeakFinder {

    public static final double NEXT_SECONDS = 8;
    public static final double PREV_SECONDS = 4;
    public static final double NEAR_BEST = 0.8;

    private PeakFinder() {
    }

    /** Start of the drop, in seconds; 0 for an empty or silent track. */
    public static double find(double[] energy, double windowSeconds) {
        int n = energy.length;
        if (n == 0 || windowSeconds <= 0) return 0;
        int nextLen = Math.max(1, (int) Math.round(NEXT_SECONDS / windowSeconds));
        int prevLen = Math.max(1, (int) Math.round(PREV_SECONDS / windowSeconds));
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + energy[i];
        // only points with a whole "next" window after them (the tail would look quieter by cut-off)
        int candidates = n >= nextLen ? n - nextLen + 1 : n;
        double[] next = new double[candidates];
        double best = 0;
        for (int i = 0; i < candidates; i++) {
            int end = Math.min(n, i + nextLen);
            next[i] = (prefix[end] - prefix[i]) / (end - i);
            best = Math.max(best, next[i]);
        }
        if (best <= 0) return 0;
        int pick = 0;
        double bestRise = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates; i++) {
            if (next[i] < NEAR_BEST * best) continue;
            int from = Math.max(0, i - prevLen);
            double prev = i == from ? 0 : (prefix[i] - prefix[from]) / (i - from);
            double rise = next[i] - prev;
            if (rise > bestRise) {
                bestRise = rise;
                pick = i;
            }
        }
        return pick * windowSeconds;
    }
}
