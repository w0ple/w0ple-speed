package dev.w0ple.speed;

import dev.w0ple.speed.config.SpeedConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** W0ple Speed: speed-driven music and green afterimages. Stays off when W0ple Visuals (which has both) is loaded. */
public final class W0pleSpeed implements ClientModInitializer {

    public static final String MOD_ID = "w0plespeed";
    public static final Logger LOGGER = LoggerFactory.getLogger("W0ple Speed");

    private static boolean active;
    private static SpeedMusic music;
    private static Afterimages afterimages;

    public static boolean active() {
        return active;
    }

    public static SpeedMusic music() {
        return music;
    }

    public static Afterimages afterimages() {
        return afterimages;
    }

    @Override
    public void onInitializeClient() {
        SpeedConfig.load();
        if (FabricLoader.getInstance().isModLoaded("w0plevisuals")) {
            LOGGER.info("W0ple Visuals найден, W0ple Speed отключён, чтобы не было двойной музыки");
            return;
        }
        active = true;
        music = new SpeedMusic();
        afterimages = new Afterimages();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                music.tick(mc);
                afterimages.tick(mc);
            } catch (RuntimeException e) {
                LOGGER.error("W0ple Speed failed in tick", e);
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (!SpeedConfig.get().afterimagesEnabled || afterimages == null) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            afterimages.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState, partial);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SpeedConfig.save());
    }
}
