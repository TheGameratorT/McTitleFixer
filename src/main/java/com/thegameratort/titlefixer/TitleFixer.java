package com.thegameratort.titlefixer;

import com.thegameratort.titlefixer.config.TitleFixerConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;

public class TitleFixer implements ClientModInitializer {
    private static TitleFixerConfig config;

    @Override
    public void onInitializeClient() {
        System.out.println("Title Fixer started.");
        ConfigHolder<TitleFixerConfig> configHolder = AutoConfig.register(TitleFixerConfig.class, Toml4jConfigSerializer::new);
        TitleFixer.config = configHolder.getConfig();
    }

    public static TitleFixerConfig getConfig() {
        return TitleFixer.config;
    }
}
