package dev.w0ple.speed.config;

import dev.w0ple.speed.W0pleSpeed;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen (Cloth Config); only loaded when Cloth Config is present. */
final class SpeedConfigScreen {

    private SpeedConfigScreen() {
    }

    static Screen create(Screen parent) {
        SpeedConfig c = SpeedConfig.get();
        ConfigBuilder b = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.literal("W0ple Speed"));
        b.setSavingRunnable(() -> {
            c.sanitize();
            SpeedConfig.save();
        });
        ConfigEntryBuilder e = b.entryBuilder();

        ConfigCategory music = b.getOrCreateCategory(Component.literal("Скоростная музыка"));
        var m = W0pleSpeed.music();
        if (m != null) music.addEntry(e.startTextDescription(Component.literal(m.statusText())).build());
        music.addEntry(e.startBooleanToggle(Component.literal("Включена"), c.musicEnabled).setDefaultValue(true).setSaveConsumer(v -> c.musicEnabled = v).build());
        music.addEntry(e.startIntSlider(Component.literal("Громкость, %"), (int) c.volume, 0, 200).setDefaultValue(100).setSaveConsumer(v -> c.volume = v).build());
        music.addEntry(e.startBooleanToggle(Component.literal("Громкость от скорости"), c.volumeBySpeed).setDefaultValue(true).setSaveConsumer(v -> c.volumeBySpeed = v).build());
        music.addEntry(e.startIntSlider(Component.literal("Музыка от скорости, бл/с"), (int) c.minSpeed, 2, 40).setDefaultValue(8).setSaveConsumer(v -> c.minSpeed = v).build());
        music.addEntry(e.startIntSlider(Component.literal("Полная громкость на, бл/с"), (int) c.maxSpeed, 10, 100).setDefaultValue(40).setSaveConsumer(v -> c.maxSpeed = v).build());
        music.addEntry(e.startIntSlider(Component.literal("Старт с секунды (-1 = авто)"), (int) c.peakSeconds, -1, 1200).setDefaultValue(-1).setSaveConsumer(v -> c.peakSeconds = v).build());

        ConfigCategory after = b.getOrCreateCategory(Component.literal("Проекции"));
        after.addEntry(e.startBooleanToggle(Component.literal("Включены"), c.afterimagesEnabled).setDefaultValue(true).setSaveConsumer(v -> c.afterimagesEnabled = v).build());
        after.addEntry(e.startIntSlider(Component.literal("Порог скорости, бл/с"), (int) c.threshold, 10, 100).setDefaultValue(40).setSaveConsumer(v -> c.threshold = v).build());
        after.addEntry(e.startStrField(Component.literal("Цвет (#RRGGBB)"), c.color).setDefaultValue("#7CFFB2")
                .setSaveConsumer(v -> c.color = v.trim()).build());
        after.addEntry(e.startDoubleField(Component.literal("Яркость (0.2-1.0)"), c.brightness).setDefaultValue(1.0).setMin(0.2).setMax(1.0).setSaveConsumer(v -> c.brightness = v).build());
        after.addEntry(e.startDoubleField(Component.literal("Время жизни, с (0.1-1.5)"), c.life).setDefaultValue(0.4).setMin(0.1).setMax(1.5).setSaveConsumer(v -> c.life = v).build());
        after.addEntry(e.startIntSlider(Component.literal("Интервал слепков, тиков"), c.interval, 1, 6).setDefaultValue(2).setSaveConsumer(v -> c.interval = v).build());
        after.addEntry(e.startBooleanToggle(Component.literal("Только я"), c.onlyMe).setDefaultValue(false).setSaveConsumer(v -> c.onlyMe = v).build());
        return b.build();
    }
}
