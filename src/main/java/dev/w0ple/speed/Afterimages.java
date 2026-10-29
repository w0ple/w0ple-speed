package dev.w0ple.speed;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.w0ple.speed.config.SpeedConfig;
import dev.w0ple.speed.speed.SpeedMeter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Translucent green copies of a fast player left behind, like Naoya Zenin's projection technique.
 * Every few ticks a fast player's render state is extracted into a pooled slot and frozen; the slot
 * is drawn with the normal player renderer (tint and translucency come from the render mixins) and
 * fades out. Players are picked once per tick; a frame costs nothing while no copy is alive.
 */
public final class Afterimages {

    public static final int MAX_PER_PLAYER = 6;
    public static final int MAX_TOTAL = 24;
    private static final double RANGE = 64;
    private static final double TICK = 0.05;
    private static final float MAX_ALPHA = 0.7f;
    /** A copy this close to the body, or not behind it, is not drawn. */
    private static final double MIN_BEHIND = 0.6;
    private static final double MIN_DISTANCE_SQ = 1.0;
    /** Hysteresis: a copy that is behind stays so until it is clearly not (no flicker from frame to frame). */
    private static final double KEEP_BEHIND = 0.3;
    private static final double KEEP_DISTANCE_SQ = 0.6;
    /** Time constant of the direction smoothing, seconds. */
    private static final double DIRECTION_SMOOTHING = 0.2;
    /** Closer than this to the camera a copy is not drawn; it fades in up to FADE_FAR. */
    private static final double FADE_NEAR = 1.5;
    private static final double FADE_FAR = 4.0;
    public static final net.minecraft.resources.Identifier WHITE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("w0plespeed", "textures/afterimage_white.png");

    /** Read by the renderer mixins while a copy is being submitted. */
    private static boolean drawing;
    private static int tint;

    private final SpeedConfig cfg = SpeedConfig.get();

    private static final class Tracker {
        final SpeedMeter meter = new SpeedMeter();
        int seen;
        int sinceSnap = 1000;
        boolean has;
        double cx;
        double cy;
        double cz;
        double dirX;
        double dirY;
        double dirZ = 1;
    }

    private static final class Slot {
        AvatarRenderState state;
        final double[] pos = new double[3];
        AABB box;
        Tracker tracker;
        boolean behind;
        int owner;
        long born;
        boolean live;
    }

    private final Map<Integer, Tracker> trackers = new HashMap<>();
    private final Slot[] slots = new Slot[MAX_TOTAL];
    private int liveCount;
    private long ticks;

    public Afterimages() {
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
    }

    public static boolean isDrawing() {
        return drawing;
    }

    public static int tint() {
        return tint;
    }

    private void clear() {
        trackers.clear();
        for (Slot s : slots) s.live = false;
        liveCount = 0;
    }

    public void tick(Minecraft mc) {
        var level = mc.level;
        var me = mc.player;
        if (level == null || me == null) return;
        if (!cfg.afterimagesEnabled) {
            if (liveCount > 0 || !trackers.isEmpty()) clear();
            return;
        }
        ticks++;
        if (cfg.onlyMe) {
            track(mc, me);
        } else {
            for (AbstractClientPlayer p : level.players()) {
                if (p == me || p.distanceToSqr(me) <= RANGE * RANGE) track(mc, p);
            }
        }
        if (liveCount > 0) updateBehind();
        // forget players that left
        Iterator<Tracker> it = trackers.values().iterator();
        while (it.hasNext()) {
            if (it.next().seen != (int) ticks) it.remove();
        }
    }

    private void track(Minecraft mc, AbstractClientPlayer p) {
        Tracker t = trackers.computeIfAbsent(p.getId(), id -> new Tracker());
        t.seen = (int) ticks;
        t.meter.update(p.getX(), p.getY(), p.getZ(), TICK);
        updateDirection(t, p);
        t.sinceSnap++;
        if (t.meter.smoothed() < cfg.threshold || p.isSpectator() || p.isInvisible()) return;
        if (t.sinceSnap < cfg.interval) return;
        t.sinceSnap = 0;
        snapshot(mc, p);
    }

    private void snapshot(Minecraft mc, AbstractClientPlayer p) {
        AvatarRenderer<AbstractClientPlayer> renderer = mc.getEntityRenderDispatcher().getPlayerRenderer(p);
        Slot slot = pick(p.getId());
        if (slot.state == null) slot.state = renderer.createRenderState();
        AvatarRenderState st = slot.state;
        renderer.extractRenderState(p, st, 1f);
        // a frozen body only: no name, shadow, fire or leash
        st.nameTag = null;
        st.scoreText = null;
        st.shadowPieces.clear();
        st.shadowRadius = 0;
        st.displayFireAnimation = false;
        st.outlineColor = EntityRenderState.NO_OUTLINE;
        slot.pos[0] = st.x;
        slot.pos[1] = st.y;
        slot.pos[2] = st.z;
        slot.box = p.getBoundingBox().inflate(0.5, 0.2, 0.5);
        slot.owner = p.getId();
        slot.tracker = trackers.get(p.getId());
        slot.born = ticks;
        if (!slot.live) liveCount++;
        slot.live = true;
    }

    /** Current position and the way the player goes (the way it looks while standing still). */
    private static void updateDirection(Tracker t, AbstractClientPlayer p) {
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        if (t.has) {
            double dx = x - t.cx;
            double dy = y - t.cy;
            double dz = z - t.cz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len / TICK >= 1.0 && len < SpeedMeter.MAX_STEP) {
                blend(t, dx / len, dy / len, dz / len);
            } else if (len / TICK < 1.0) {
                var look = p.getLookAngle();
                blend(t, look.x, look.y, look.z);
            }
        }
        t.cx = x;
        t.cy = y;
        t.cz = z;
        t.has = true;
    }

    /** Moves the stored direction towards a new one (EMA), keeping it a unit vector. */
    private static void blend(Tracker t, double x, double y, double z) {
        double k = 1 - Math.exp(-TICK / DIRECTION_SMOOTHING);
        double nx = t.dirX + (x - t.dirX) * k;
        double ny = t.dirY + (y - t.dirY) * k;
        double nz = t.dirZ + (z - t.dirZ) * k;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6) {
            t.dirX = nx / len;
            t.dirY = ny / len;
            t.dirZ = nz / len;
        }
    }

    /** Once per tick: whether each copy is behind its player (against the way it goes) and not on the body; with hysteresis. */
    private void updateBehind() {
        for (Slot s : slots) {
            if (!s.live) continue;
            Tracker t = s.tracker;
            if (t == null) {
                s.behind = true;
                continue;
            }
            double dx = s.pos[0] - t.cx;
            double dy = s.pos[1] - t.cy;
            double dz = s.pos[2] - t.cz;
            double distSq = dx * dx + dy * dy + dz * dz;
            double dot = dx * t.dirX + dy * t.dirY + dz * t.dirZ;
            s.behind = s.behind ? (distSq >= KEEP_DISTANCE_SQ && dot < -KEEP_BEHIND)
                    : (distSq >= MIN_DISTANCE_SQ && dot < -MIN_BEHIND);
        }
    }

    private static float smoothstep(double lo, double hi, double v) {
        double x = Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
        return (float) (x * x * (3 - 2 * x));
    }

    /** A free slot; else the owner's oldest when it has too many, else the oldest of all. */
    private Slot pick(int owner) {
        Slot free = null;
        Slot oldestOwn = null;
        Slot oldest = null;
        int own = 0;
        for (Slot s : slots) {
            if (!s.live) {
                if (free == null) free = s;
                continue;
            }
            if (s.owner == owner) {
                own++;
                if (oldestOwn == null || s.born < oldestOwn.born) oldestOwn = s;
            }
            if (oldest == null || s.born < oldest.born) oldest = s;
        }
        if (own >= MAX_PER_PLAYER) return oldestOwn;
        return free != null ? free : oldest;
    }

    /** Draws the live copies; called every frame, returns at once when there are none. */
    public void render(PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, float partialTick) {
        if (liveCount == 0) return;
        var mc = Minecraft.getInstance();
        float life = (float) cfg.life * 20f;
        int rgb = shade(cfg.rgb(), (float) cfg.brightness);
        // your own copies stay out of the first-person view (they would flicker in front of the screen)
        boolean hideOwn = mc.player != null && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson();
        int myId = mc.player == null ? -1 : mc.player.getId();
        var dispatcher = mc.getEntityRenderDispatcher();
        var frustum = camera.cullFrustum;
        double cx = camera.pos.x;
        double cy = camera.pos.y;
        double cz = camera.pos.z;
        for (Slot s : slots) {
            if (!s.live) continue;
            float age = (ticks - s.born) + partialTick;
            if (age >= life) {
                s.live = false;
                liveCount--;
                continue;
            }
            double dx = s.pos[0] - cx;
            double dy = s.pos[1] - cy;
            double dz = s.pos[2] - cz;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            if (frustum != null && !frustum.isVisible(s.box)) continue;
            if (!s.behind) continue;
            if (hideOwn && s.owner == myId) continue;
            double cdy = dy + 0.9;
            double camDist = Math.sqrt(dx * dx + cdy * cdy + dz * dz);
            if (camDist < FADE_NEAR) continue;
            float alpha = MAX_ALPHA * (1f - age / life) * smoothstep(FADE_NEAR, FADE_FAR, camDist);
            int a = Math.max(1, Math.min(255, Math.round(alpha * 255f)));
            tint = (a << 24) | rgb;
            drawing = true;
            try {
                // x, y, z are the offset from the camera, not the camera position
                dispatcher.submit(s.state, camera, dx, dy, dz, pose, out);
            } finally {
                drawing = false;
            }
        }
    }

    /** The colour at a brightness 0..1 (the copies are full-bright, so dimming is done on the colour). */
    private static int shade(int rgb, float k) {
        int r = Math.round(((rgb >> 16) & 0xFF) * k);
        int g = Math.round(((rgb >> 8) & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Test hook: copies alive now. */
    public int alive() {
        return liveCount;
    }
}
