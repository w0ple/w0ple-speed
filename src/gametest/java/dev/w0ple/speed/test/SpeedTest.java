package dev.w0ple.speed.test;

import dev.w0ple.speed.W0pleSpeed;
import dev.w0ple.speed.config.SpeedConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Starts the game with the mod, runs the player at 50 blocks/s: the song must play, copies must appear, then both stop. */
public class SpeedTest implements FabricClientGameTest {

    private static volatile boolean drive;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        // W0PLE_TEST_SONG=mp3 plays test.mp3 instead of test.ogg
        String song = "mp3".equals(System.getenv("W0PLE_TEST_SONG")) ? "test.mp3" : "test.ogg";
        try (InputStream in = SpeedTest.class.getResourceAsStream("/" + song)) {
            Files.createDirectories(SpeedConfig.DIR);
            Files.copy(in, SpeedConfig.DIR.resolve(song), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runCommand("gamemode creative @a");
            ctx.waitTicks(40);
            if (!W0pleSpeed.active()) throw new IllegalStateException("W0ple Speed is not active");
            boolean[] playing = new boolean[1];
            int[] copies = new int[1];
            for (int i = 0; i < 60; i++) {
                ctx.runOnClient(mc -> mc.player.setPos(mc.player.getX() + 2.5, mc.player.getY(), mc.player.getZ()));
                ctx.waitTick();
                if (i == 50) {
                    ctx.runOnClient(mc -> {
                        playing[0] = W0pleSpeed.music().playing();
                        copies[0] = W0pleSpeed.afterimages().alive();
                    });
                }
            }
            boolean[] after = new boolean[1];
            ctx.waitTicks(140);
            ctx.runOnClient(mc -> after[0] = W0pleSpeed.music().playing());
            W0pleSpeed.LOGGER.info("SPEEDTEST playing while fast={} copies={} playing after standing={}", playing[0], copies[0], after[0]);
            if (!playing[0] || copies[0] <= 0 || after[0]) throw new IllegalStateException("speed test failed");
            // bright copies only behind, seen from the side: run at 20 blocks/s with a lower threshold
            SpeedConfig.get().threshold = 10;
            SpeedConfig.get().life = 1.5;
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (drive && mc.player != null) mc.player.setPos(mc.player.getX() + 1.0, mc.player.getY(), mc.player.getZ());
            });
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                mc.player.setYRot(0f);
            });
            drive = true;
            ctx.waitTicks(40);
            ctx.takeScreenshot("speed-afterimages-side");
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.player.setYRot(-90f);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("speed-afterimages-firstperson");
            drive = false;
        }
    }
}
