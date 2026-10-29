package dev.w0ple.speed.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu entry: opens the Cloth Config screen when Cloth Config is installed (no screen otherwise). */
public final class SpeedModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) return parent -> null;
        return SpeedConfigScreen::create;
    }
}
