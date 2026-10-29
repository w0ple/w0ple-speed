package dev.w0ple.speed.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.w0ple.speed.W0pleSpeed;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** All settings of the mod: one JSON, config/w0plespeed.json. Fields are public for the settings screen. */
public final class SpeedConfig {

    public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("w0plespeed.json");
    /** The song folder: the first .ogg in it is played. */
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("w0plespeed");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpeedConfig instance = new SpeedConfig();

    // speed music
    public boolean musicEnabled = true;
    public double volume = 100;
    public boolean volumeBySpeed = true;
    public double minSpeed = 8;
    public double maxSpeed = 40;
    /** Second the song starts from; -1 = the automatically found peak. */
    public double peakSeconds = -1;
    /** Peak found by the analysis, with the file it belongs to. */
    public String peakFile = "";
    public long peakSize = -1;
    public double peakValue = -1;

    // afterimages
    public boolean afterimagesEnabled = true;
    public double threshold = 40;
    public String color = "#7CFFB2";
    public double brightness = 1.0;
    public double life = 0.4;
    public int interval = 2;
    public boolean onlyMe = false;

    public static SpeedConfig get() {
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            W0pleSpeed.LOGGER.warn("Cannot create {}", DIR, e);
        }
        if (!Files.isRegularFile(FILE)) {
            save();
            return;
        }
        try {
            SpeedConfig read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SpeedConfig.class);
            if (read != null) {
                read.sanitize();
                instance = read;
            }
        } catch (IOException | RuntimeException e) {
            W0pleSpeed.LOGGER.warn("Cannot read {}, using defaults", FILE, e);
        }
    }

    /** Writes through a temp file so a crash never leaves half a config. */
    public static void save() {
        try {
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(instance), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            W0pleSpeed.LOGGER.warn("Cannot write {}", FILE, e);
        }
    }

    /** The colour as 0xRRGGBB; the default when the text is not a colour. */
    public int rgb() {
        try {
            return Integer.parseInt(color.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0x7CFFB2;
        }
    }

    /** Keeps every value in the range the sliders allow. */
    public void sanitize() {
        volume = clamp(volume, 0, 200);
        minSpeed = clamp(minSpeed, 2, 40);
        maxSpeed = clamp(maxSpeed, 10, 100);
        peakSeconds = clamp(peakSeconds, -1, 1200);
        threshold = clamp(threshold, 10, 100);
        brightness = clamp(brightness, 0.2, 1.0);
        life = clamp(life, 0.1, 1.5);
        interval = (int) clamp(interval, 1, 6);
        if (peakFile == null) peakFile = "";
        if (color == null) color = "#7CFFB2";
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
